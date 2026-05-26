package com.example.instadmguard.service

import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.model.InstagramScreen
import com.example.instadmguard.detector.ReelViewerSignature
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionStateManager {

    private var inDmContext: Boolean = false
    private var recentDmContextUntilMillis: Long = 0L
    private var lastDmContextAtMillis: Long = 0L
    private var pendingDmClickUntilMillis: Long = 0L
    private var dmGraceUntilMillis: Long = 0L
    private var dmAllowanceGrantedAtMillis: Long = 0L
    private var dmAllowedViewerSignature: Set<String> = emptySet()
    private var explicitReelsEntryArmed: Boolean = false
    private var lastBlockedAtMillis: Long = 0L
    private var lastNonReelDetectedAtMillis: Long = 0L
    private var lastDmClickSummary: String = ""
    private var lastNonViewerScreen: InstagramScreen = InstagramScreen.OTHER
    private var usageDay: LocalDate = LocalDate.now()
    private var dailyUsageSeconds: Int = 0

    fun isInDmContext(): Boolean = inDmContext

    fun isRecentlyInDmContext(nowMillis: Long): Boolean =
        inDmContext || recentDmContextUntilMillis > nowMillis

    fun noteDmClick(nowMillis: Long, clickSummary: String) {
        if (!inDmContext && recentDmContextUntilMillis <= nowMillis) {
            return
        }
        pendingDmClickUntilMillis = nowMillis + PENDING_DM_CLICK_WINDOW_MILLIS
        lastDmClickSummary = clickSummary
    }

    fun clearDmReelAllowance() {
        pendingDmClickUntilMillis = 0L
        dmGraceUntilMillis = 0L
        dmAllowanceGrantedAtMillis = 0L
        dmAllowedViewerSignature = emptySet()
    }

    fun hasActiveDmReelAllowance(nowMillis: Long): Boolean =
        pendingDmClickUntilMillis > nowMillis || dmGraceUntilMillis > nowMillis

    fun hasGrantedDmReelAllowance(nowMillis: Long): Boolean = dmGraceUntilMillis > nowMillis

    fun clearDmReelAllowanceForViewerScroll(nowMillis: Long): Boolean {
        if (dmGraceUntilMillis <= nowMillis) {
            return false
        }

        clearDmReelAllowance()
        return true
    }

    fun noteExplicitReelsEntryClick() {
        explicitReelsEntryArmed = true
    }

    fun hasPendingExplicitReelsEntry(): Boolean = explicitReelsEntryArmed

    fun clearExplicitReelsEntry() {
        explicitReelsEntryArmed = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun onScreenDetected(
        screen: InstagramScreen,
        viewerSignature: Set<String>,
        nowMillis: Long,
        settings: AppSettings,
    ): SessionSnapshot {
        rollDay(nowMillis)
        val wasDmContext = inDmContext
        inDmContext = screen == InstagramScreen.DM_LIST || screen == InstagramScreen.DM_THREAD

        if (inDmContext) {
            lastDmContextAtMillis = nowMillis
            recentDmContextUntilMillis = nowMillis + RECENT_DM_CONTEXT_MILLIS
        } else if (wasDmContext) {
            // Just left DM — keep the recent-DM awareness alive
            recentDmContextUntilMillis = nowMillis + RECENT_DM_CONTEXT_MILLIS
        }

        if (!screen.isBlockTarget) {
            lastNonReelDetectedAtMillis = nowMillis
        }

        // When navigating to an explicit reel surface (REELS_TAB, EXPLORE_REELS,
        // HOME_REEL), aggressively clear all DM state. This prevents the
        // DMs → Reels tab flow from keeping a stale DM context alive.
        if (!inDmContext && screen.isBlockTarget && screen != InstagramScreen.REEL_VIEWER) {
            recentDmContextUntilMillis = 0L
            clearDmReelAllowance()
        }

        val grantedDmAllowanceNow =
            screen == InstagramScreen.REEL_VIEWER &&
                pendingDmClickUntilMillis >= nowMillis

        if (grantedDmAllowanceNow) {
            dmGraceUntilMillis = nowMillis + (settings.graceDurationSeconds * 1_000L)
            dmAllowanceGrantedAtMillis = nowMillis
            dmAllowedViewerSignature = viewerSignature
            pendingDmClickUntilMillis = 0L
        }

        if (
            screen == InstagramScreen.REEL_VIEWER &&
            !grantedDmAllowanceNow &&
            dmGraceUntilMillis > nowMillis &&
            viewerSignature.isNotEmpty() &&
            !ReelViewerSignature.matches(dmAllowedViewerSignature, viewerSignature)
        ) {
            clearDmReelAllowance()
        }

        if (!inDmContext) {
            if (screen == InstagramScreen.REEL_VIEWER) {
                // Preserve DM grace if we came from a DM screen recently.
                // Check both the last non-viewer screen AND whether we were in
                // DM context within the last few seconds (covers intermediate
                // OTHER screens during DM→reel transitions).
                // Allow recentDmContext to cover transient OTHER screens
                // during DM→reel transitions, but NOT if the last non-viewer
                // screen was itself a reel surface (REELS_TAB, HOME_REEL, etc.)
                val cameFromDm = lastNonViewerScreen == InstagramScreen.DM_LIST ||
                                 lastNonViewerScreen == InstagramScreen.DM_THREAD ||
                                 (recentDmContextUntilMillis > nowMillis && !lastNonViewerScreen.isBlockTarget)
                if (!cameFromDm) {
                    clearDmReelAllowance()
                }
            } else if (screen != InstagramScreen.OTHER || recentDmContextUntilMillis <= nowMillis) {
                // On non-REEL_VIEWER, non-DM surface: clear grace fully,
                // UNLESS this is a transient OTHER screen and we recently left DMs
                clearDmReelAllowance()
            }
        }

        if (screen != InstagramScreen.REEL_VIEWER) {
            lastNonViewerScreen = screen
        }

        if (pendingDmClickUntilMillis < nowMillis) {
            pendingDmClickUntilMillis = 0L
        }

        return snapshot(nowMillis)
    }

    fun addDailyUsageSeconds(nowMillis: Long, deltaSeconds: Int) {
        if (deltaSeconds <= 0) {
            return
        }
        rollDay(nowMillis)
        dailyUsageSeconds += deltaSeconds
    }

    fun dailyUsageSeconds(nowMillis: Long): Int {
        rollDay(nowMillis)
        return dailyUsageSeconds
    }

    @Suppress("UNUSED_PARAMETER")
    fun dailyLimitRemainingSeconds(settings: AppSettings, nowMillis: Long): Int? {
        return null
    }

    fun buildDecision(
        screen: InstagramScreen,
        settings: AppSettings,
        nowMillis: Long,
    ): GuardDecision {
        rollDay(nowMillis)

        if (!settings.masterEnabled) {
            return GuardDecision(
                shouldBlock = false,
                reason = "Protection disabled",
            )
        }

        if (settings.pauseUntilMillis > nowMillis) {
            return GuardDecision(
                shouldBlock = false,
                reason = "Protection paused",
            )
        }

        if (!screen.isBlockTarget) {
            return GuardDecision(
                shouldBlock = false,
                reason = "Safe surface",
            )
        }

        return if (hasPendingExplicitReelsEntry()) {
            GuardDecision(
                shouldBlock = true,
                reason = "Explicit reels button blocked",
            )
        } else if (screen == InstagramScreen.REEL_VIEWER && hasGrantedDmReelAllowance(nowMillis)) {
            GuardDecision(
                shouldBlock = false,
                reason = "Allowed DM-opened reel within grace window",
            )
        } else {
            GuardDecision(
                shouldBlock = true,
                reason = "Only DM-opened reels are allowed",
            )
        }
    }

    fun canBlockNow(
        screen: InstagramScreen,
        nowMillis: Long,
    ): Boolean {
        if (!screen.isBlockTarget) {
            return false
        }

        if (lastBlockedAtMillis == 0L) {
            return true
        }

        if (lastNonReelDetectedAtMillis >= lastBlockedAtMillis) {
            return true
        }

        return nowMillis - lastBlockedAtMillis >= SAME_SURFACE_BLOCK_COOLDOWN_MILLIS
    }

    fun markBlocked(nowMillis: Long) {
        lastBlockedAtMillis = nowMillis
    }

    fun snapshot(nowMillis: Long): SessionSnapshot {
        rollDay(nowMillis)
        return SessionSnapshot(
            inDmContext = inDmContext,
            pendingDmClickUntilMillis = pendingDmClickUntilMillis,
            dmGraceUntilMillis = dmGraceUntilMillis,
            dailyUsageSeconds = dailyUsageSeconds,
            lastBlockedAtMillis = lastBlockedAtMillis,
            lastDmClickSummary = lastDmClickSummary,
        )
    }

    private fun rollDay(nowMillis: Long) {
        val currentDay =
            Instant.ofEpochMilli(nowMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

        if (currentDay != usageDay) {
            usageDay = currentDay
            dailyUsageSeconds = 0
        }
    }

    private companion object {
        const val PENDING_DM_CLICK_WINDOW_MILLIS = 5_000L
        const val RECENT_DM_CONTEXT_MILLIS = 8_000L
        const val SAME_SURFACE_BLOCK_COOLDOWN_MILLIS = 150L
    }
}

data class SessionSnapshot(
    val inDmContext: Boolean,
    val pendingDmClickUntilMillis: Long,
    val dmGraceUntilMillis: Long,
    val dailyUsageSeconds: Int,
    val lastBlockedAtMillis: Long,
    val lastDmClickSummary: String,
)

data class GuardDecision(
    val shouldBlock: Boolean,
    val reason: String,
)
