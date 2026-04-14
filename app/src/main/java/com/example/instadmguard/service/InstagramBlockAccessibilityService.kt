package com.example.instadmguard.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.instadmguard.InstaDMGuardApp
import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.data.DebugLogRepository
import com.example.instadmguard.data.SettingsRepository
import com.example.instadmguard.detector.AccessibilityNodeSnapshotBuilder
import com.example.instadmguard.detector.DetectionHeuristics
import com.example.instadmguard.detector.InstagramScreenDetector
import com.example.instadmguard.detector.ReelViewerSignature
import com.example.instadmguard.model.BlockMode
import com.example.instadmguard.model.InstagramScreen
import com.example.instadmguard.util.InstagramConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class InstagramBlockAccessibilityService : AccessibilityService() {

    private val detector = InstagramScreenDetector()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var debugLogRepository: DebugLogRepository
    private lateinit var serviceStateStore: ServiceStateStore
    private lateinit var sessionStateManager: SessionStateManager
    private lateinit var overlayController: OverlayController

    private var evaluationJob: Job? = null
    private var keepAliveJob: Job? = null
    private var evaluationRequested: Boolean = false
    private var latestEvaluationEventType: Int = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    private var dailyUsageTickerJob: Job? = null
    private var lastEventSummary: String = ""
    private var lastOverlayDismissedAtMillis: Long = 0L
    private var homeNavigationPending: Boolean = false
    private var lastHomeNavigationAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()

        val appContainer = (application as InstaDMGuardApp).container
        settingsRepository = appContainer.settingsRepository
        debugLogRepository = appContainer.debugLogRepository
        serviceStateStore = appContainer.serviceStateStore
        sessionStateManager = appContainer.sessionStateManager
        overlayController = OverlayController(this)

        serviceStateStore.update { current ->
            current.copy(serviceConnected = true, lastDecision = "Service connected")
        }

        logDebug(
            screen = InstagramScreen.OTHER,
            message = "Accessibility service connected",
        )

        startKeepAlivePulse()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.packageName?.toString() != com.example.instadmguard.util.InstagramConstants.PACKAGE_NAME) {
            return
        }

        handleEventSignal(event)
        scheduleEvaluation(event.eventType)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        evaluationJob?.cancel()
        keepAliveJob?.cancel()
        dailyUsageTickerJob?.cancel()
        overlayController.dispose()
        serviceScope.cancel()
        serviceStateStore.reset()
    }

    private fun handleEventSignal(event: AccessibilityEvent) {
        val nowMillis = System.currentTimeMillis()
        lastEventSummary = summarizeEvent(event)
        val lastDetectedScreen = serviceStateStore.status.value.lastDetectedScreen

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (DetectionHeuristics.isExplicitReelsEntryClick(lastEventSummary)) {
                sessionStateManager.noteExplicitReelsEntryClick()
                sessionStateManager.clearDmReelAllowance()
                logDebug(
                    screen = lastDetectedScreen,
                    message = "Armed strict reels button block from click: $lastEventSummary",
                )
                return
            }

            if (sessionStateManager.isInDmContext()) {
                sessionStateManager.noteDmClick(
                    nowMillis = nowMillis,
                    clickSummary = lastEventSummary,
                )

                logDebug(
                    screen = serviceStateStore.status.value.lastDetectedScreen,
                    message = "Armed DM click window from click: $lastEventSummary",
                )
                return
            }
            return
        }

        if (
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            lastDetectedScreen == InstagramScreen.REEL_VIEWER &&
            sessionStateManager.clearDmReelAllowanceForViewerScroll(nowMillis)
        ) {
            logDebug(
                screen = lastDetectedScreen,
                message = "Cleared DM reel allowance from reel viewer scroll",
            )
        }
    }

    private fun scheduleEvaluation(eventType: Int) {
        latestEvaluationEventType = eventType
        evaluationRequested = true

        if (evaluationJob?.isActive == true) {
            return
        }

        evaluationJob =
            serviceScope.launch {
                while (isActive && evaluationRequested) {
                    evaluationRequested = false
                    delay(evaluationDelayMillis(latestEvaluationEventType))
                    runCatching { evaluateCurrentWindow() }
                        .onFailure { error ->
                            logDebug(
                                screen = InstagramScreen.OTHER,
                                message = "Evaluation failed: ${error.javaClass.simpleName}",
                            )
                        }
                }
            }
    }

    private fun evaluationDelayMillis(eventType: Int): Long =
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> 60L
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> 100L
            else -> 140L
        }

    private fun startKeepAlivePulse() {
        if (keepAliveJob?.isActive == true) {
            return
        }

        keepAliveJob =
            serviceScope.launch {
                while (isActive) {
                    delay(KEEP_ALIVE_PULSE_MILLIS)
                    if (isInstagramWindowActive()) {
                        scheduleEvaluation(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    }
                }
            }
    }

    private fun isInstagramWindowActive(): Boolean {
        val root = rootInActiveWindow ?: return false

        return try {
            root.packageName?.toString() == InstagramConstants.PACKAGE_NAME
        } finally {
            root.recycleSafely()
        }
    }

    private fun evaluateCurrentWindow() {
        val root = rootInActiveWindow ?: run {
            overlayController.hide()
            stopDailyUsageTicker()
            serviceStateStore.update { current ->
                current.copy(lastDecision = "Instagram window unavailable")
            }
            return
        }

        val snapshot =
            try {
                AccessibilityNodeSnapshotBuilder.build(root)
            } finally {
                root.recycleSafely()
            }

        val settings = settingsRepository.settings.value
        val nowMillis = System.currentTimeMillis()
        val detection = detector.detect(snapshot)
        val previousDetectedScreen = serviceStateStore.status.value.lastDetectedScreen
        val viewerSignature =
            if (detection.screen == InstagramScreen.REEL_VIEWER) {
                ReelViewerSignature.fromSnapshot(snapshot)
            } else {
                emptySet()
            }

        if (homeNavigationPending && !detection.screen.isBlockTarget) {
            homeNavigationPending = false
            sessionStateManager.clearExplicitReelsEntry()
        } else if (homeNavigationPending && nowMillis - lastHomeNavigationAtMillis >= HOME_NAVIGATION_SETTLE_MILLIS) {
            homeNavigationPending = false
        }

        val sessionSnapshot =
            sessionStateManager.onScreenDetected(
                screen = detection.screen,
                viewerSignature = viewerSignature,
                nowMillis = nowMillis,
                settings = settings,
            )
        val decision =
            sessionStateManager.buildDecision(
                screen = detection.screen,
                settings = settings,
                nowMillis = nowMillis,
            )

        syncDailyUsageTicker(
            settings = settings,
            screen = detection.screen,
        )

        applyDecision(
            decision = decision,
            settings = settings,
            screen = detection.screen,
            previousScreen = previousDetectedScreen,
            nowMillis = nowMillis,
        )

        serviceStateStore.update { current ->
            current.copy(
                serviceConnected = true,
                inDmContext = sessionSnapshot.inDmContext,
                lastDetectedScreen = detection.screen,
                lastDecision = decision.reason,
                pausedUntilMillis = settings.pauseUntilMillis,
                dmGraceUntilMillis = sessionSnapshot.dmGraceUntilMillis,
                dailyLimitUsedSeconds = sessionSnapshot.dailyUsageSeconds,
                lastDetectorSummary = detection.summary,
                lastEventSummary = lastEventSummary,
            )
        }

        logDebug(
            screen = detection.screen,
            message = "${detection.screen.name} | ${decision.reason} | ${detection.summary}",
        )
    }

    private fun syncDailyUsageTicker(
        settings: AppSettings,
        screen: InstagramScreen,
    ) {
        val shouldTrack =
            settings.dailyLimitEnabled &&
                !settings.allowDmOpenedReelsOnly &&
                screen.isBlockTarget

        if (!shouldTrack) {
            stopDailyUsageTicker()
            return
        }

        if (dailyUsageTickerJob != null) {
            return
        }

        dailyUsageTickerJob =
            serviceScope.launch {
                while (isActive) {
                    delay(1_000L)
                    val nowMillis = System.currentTimeMillis()
                    sessionStateManager.addDailyUsageSeconds(nowMillis, 1)
                    val usedSeconds = sessionStateManager.dailyUsageSeconds(nowMillis)
                    serviceStateStore.update { current ->
                        current.copy(dailyLimitUsedSeconds = usedSeconds)
                    }

                    val liveSettings = settingsRepository.settings.value
                    val currentScreen = serviceStateStore.status.value.lastDetectedScreen
                    val liveDecision =
                        sessionStateManager.buildDecision(
                            screen = currentScreen,
                            settings = liveSettings,
                            nowMillis = nowMillis,
                        )

                    if (liveDecision.shouldBlock && currentScreen.isBlockTarget) {
                        applyDecision(
                            decision = liveDecision,
                            settings = liveSettings,
                            screen = currentScreen,
                            previousScreen = currentScreen,
                            nowMillis = nowMillis,
                        )
                    }
                }
            }
    }

    private fun stopDailyUsageTicker() {
        dailyUsageTickerJob?.cancel()
        dailyUsageTickerJob = null
    }

    private fun applyDecision(
        decision: GuardDecision,
        settings: AppSettings,
        screen: InstagramScreen,
        previousScreen: InstagramScreen,
        nowMillis: Long,
    ) {
        if (!decision.shouldBlock) {
            overlayController.hide()
            return
        }

        when (settings.blockMode) {
            BlockMode.OVERLAY_ONLY -> {
                val dismissible = settings.overlayDismissible
                if (dismissible && nowMillis - lastOverlayDismissedAtMillis < 1_000L) {
                    return
                }

                overlayController.show(
                    message = "Reels blocked",
                    dismissible = dismissible,
                    onDismiss = {
                        lastOverlayDismissedAtMillis = System.currentTimeMillis()
                    },
                )
            }

            BlockMode.BACK_ONLY -> {
                overlayController.hide()
                if (!sessionStateManager.canBlockNow(screen, nowMillis)) {
                    return
                }
                if (shouldSuppressRepeatedHomeNavigation(nowMillis)) {
                    return
                }
                val returnedToSafeSurface = returnToSafeSurface(screen, previousScreen)
                if (returnedToSafeSurface) {
                    recordHomeNavigation(nowMillis)
                    sessionStateManager.markBlocked(nowMillis)
                    logDebug(screen, "Returned to safe Instagram surface for $screen")
                } else {
                    logDebug(screen, "Could not return to a safe Instagram surface for $screen")
                }
            }

            BlockMode.OVERLAY_AND_BACK -> {
                if (!sessionStateManager.canBlockNow(screen, nowMillis)) {
                    return
                }
                if (shouldSuppressRepeatedHomeNavigation(nowMillis)) {
                    return
                }
                overlayController.show(
                    message = "Reels blocked",
                    dismissible = false,
                )
                val returnedToSafeSurface = returnToSafeSurface(screen, previousScreen)
                if (returnedToSafeSurface) {
                    recordHomeNavigation(nowMillis)
                    sessionStateManager.markBlocked(nowMillis)
                    logDebug(screen, "Displayed overlay and returned to safe Instagram surface for $screen")
                } else {
                    logDebug(screen, "Displayed overlay but could not return to a safe Instagram surface for $screen")
                }
                serviceScope.launch {
                    delay(700L)
                    overlayController.hide()
                }
            }
        }
    }

    private fun logDebug(
        screen: InstagramScreen,
        message: String,
    ) {
        if (!::settingsRepository.isInitialized || !settingsRepository.settings.value.debugMode) {
            return
        }
        debugLogRepository.add(screen, message)
    }

    private fun summarizeEvent(event: AccessibilityEvent): String {
        val parts = linkedSetOf<String>()

        event.text
            .mapNotNull { it?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .forEach(parts::add)

        event.contentDescription
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(parts::add)

        val source = event.source
        if (source != null) {
            source.text
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(parts::add)

            source.contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(parts::add)

            source.viewIdResourceName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(parts::add)

            source.recycleSafely()
        }

        return parts.joinToString(" | ")
    }

    private fun shouldSuppressRepeatedHomeNavigation(nowMillis: Long): Boolean =
        homeNavigationPending && nowMillis - lastHomeNavigationAtMillis < HOME_NAVIGATION_SETTLE_MILLIS

    private fun recordHomeNavigation(nowMillis: Long) {
        homeNavigationPending = true
        lastHomeNavigationAtMillis = nowMillis
    }

    private fun shouldUseBackToReturnToFeed(
        screen: InstagramScreen,
        previousScreen: InstagramScreen,
    ): Boolean =
        screen == InstagramScreen.HOME_REEL ||
            (screen == InstagramScreen.REEL_VIEWER && previousScreen == InstagramScreen.HOME_REEL)

    private fun returnToSafeSurface(
        screen: InstagramScreen,
        previousScreen: InstagramScreen,
    ): Boolean {
        val preferBack = shouldUseBackToReturnToFeed(screen, previousScreen)
        val primaryAction: () -> Boolean =
            if (preferBack) {
                ::navigateBackInsideInstagram
            } else {
                ::navigateToInstagramHome
            }
        val fallbackAction: () -> Boolean =
            if (preferBack) {
                ::navigateToInstagramHome
            } else {
                ::navigateBackInsideInstagram
            }

        return primaryAction() || fallbackAction()
    }

    private fun navigateBackInsideInstagram(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    private fun navigateToInstagramHome(): Boolean {
        val root = rootInActiveWindow ?: return false

        return try {
            val candidate = findBestInstagramHomeCandidate(root) ?: return false
            clickNodeOrAncestor(candidate)
        } finally {
            root.recycleSafely()
        }
    }

    private fun findBestInstagramHomeCandidate(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }

            if (node === root) {
                continue
            }

            if (scoreInstagramHomeCandidate(node) > 0) {
                candidates += node
            } else {
                node.recycleSafely()
            }
        }

        val bestCandidate = candidates.maxByOrNull(::scoreInstagramHomeCandidate)
        candidates
            .asSequence()
            .filter { it !== bestCandidate }
            .forEach { it.recycleSafely() }

        return bestCandidate
    }

    private fun scoreInstagramHomeCandidate(node: AccessibilityNodeInfo): Int {
        if (!node.isEnabled) {
            return 0
        }

        val viewId = normalizeNodeValue(node.viewIdResourceName)
        val text = normalizeNodeValue(node.text)
        val contentDescription = normalizeNodeValue(node.contentDescription)
        val className = normalizeNodeValue(node.className)

        var score = 0

        if (viewId in HOME_TAB_VIEW_IDS) {
            score += 12
        }

        if (HOME_TAB_VIEW_ID_CLUES.any(viewId::contains)) {
            score += 8
        }

        if (looksLikeHomeTabLabel(contentDescription)) {
            score += 7
        }

        if (looksLikeHomeTabLabel(text)) {
            score += 6
        }

        if (node.isClickable) {
            score += 3
        }

        if (node.isVisibleToUser) {
            score += 1
        }

        if (className.contains("button") || className.contains("imageview") || className.contains("framelayout")) {
            score += 1
        }

        return score
    }

    private fun looksLikeHomeTabLabel(value: String): Boolean {
        if (value.isEmpty()) {
            return false
        }

        return value == "home" ||
            value == "feed" ||
            HOME_TAB_LABEL_CLUES.any(value::contains)
    }

    private fun normalizeNodeValue(value: CharSequence?): String =
        value
            ?.toString()
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        val nodesToRecycle = mutableListOf<AccessibilityNodeInfo>()
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            nodesToRecycle += current
            if (current.isEnabled && current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                nodesToRecycle.forEach { it.recycleSafely() }
                return true
            }
            current = current.parent
        }

        nodesToRecycle.forEach { it.recycleSafely() }
        return false
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleSafely() {
        runCatching { recycle() }
    }

    private companion object {
        const val KEEP_ALIVE_PULSE_MILLIS = 1_000L
        const val HOME_NAVIGATION_SETTLE_MILLIS = 1_500L

        val HOME_TAB_VIEW_IDS =
            setOf(
                "${InstagramConstants.PACKAGE_NAME}:id/home_tab",
                "${InstagramConstants.PACKAGE_NAME}:id/feed_tab",
                "${InstagramConstants.PACKAGE_NAME}:id/tab_bar_button_home",
                "${InstagramConstants.PACKAGE_NAME}:id/tab_bar_button_feed",
            )

        val HOME_TAB_VIEW_ID_CLUES =
            listOf(
                "home_tab",
                "feed_tab",
                "button_home",
                "button_feed",
                "main_feed",
            )

        val HOME_TAB_LABEL_CLUES =
            listOf(
                "home tab",
                "home, tab",
                "tab, home",
                "selected, home",
                "selected home",
                "feed tab",
                "feed, tab",
                "go to home",
            )
    }
}
