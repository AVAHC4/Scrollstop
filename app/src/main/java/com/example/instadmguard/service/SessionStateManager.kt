package com.example.instadmguard.service

import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.model.InstagramScreen
import com.example.instadmguard.detector.ReelViewerSignature
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionStateManager {

    private var inDmContext: Boolean = false
    private var pendingDmClickUntilMillis: Long = 0L
    private var dmGraceUntilMillis: Long = 0L
    private var dmAllowanceGrantedAtMillis: Long = 0L
    private var dmAllowedViewerSignature: Set<String> = emptySet()
    private var explicitReelsEntryUntilMillis: Long = 0L
    private var lastBlockedAtMillis: Long = 0L
    private var lastNonReelDetectedAtMillis: Long = 0L
    private var lastDmClickSummary: String = ""
    private var usageDay: LocalDate = LocalDate.now()
    private var dailyUsageSeconds: Int = 0

    fun isInDmContext(): Boolean = inDmContext

    fun noteDmClick(nowMillis: Long, clickSummary: String) {
        if (!inDmContext) {
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

    fun clearDmReelAllowanceForViewerScroll(nowMillis: Long): Boolean {
        if (
            dmGraceUntilMillis <= nowMillis ||
            nowMillis - dmAllowanceGrantedAtMillis < DM_VIEWER_SCROLL_CLEAR_DELAY_MILLIS
        ) {
            return false
        }

        clearDmReelAllowance()
        return true
    }

    fun noteExplicitReelsEntryClick(nowMillis: Long) {
        explicitReelsEntryUntilMillis = nowMillis + EXPLICIT_REELS_ENTRY_WINDOW_MILLIS
    }

    fun hasPendingExplicitReelsEntry(nowMillis: Long): Boolean =
        explicitReelsEntryUntilMillis > nowMillis

    fun clearExplicitReelsEntry() {
        explicitReelsEntryUntilMillis = 0L
    }

    fun onScreenDetected(
        screen: InstagramScreen,
        viewerSignature: Set<String>,
        nowMillis: Long,
        settings: AppSettings,
    ): SessionSnapshot {
        rollDay(nowMillis)
        inDmContext = screen == InstagramScreen.DM_LIST || screen == InstagramScreen.DM_THREAD

        if (!screen.isBlockTarget) {
            lastNonReelDetectedAtMillis = nowMillis
        }

        if (
            settings.allowDmOpenedReelsOnly &&
            screen == InstagramScreen.REEL_VIEWER &&
            pendingDmClickUntilMillis >= nowMillis
        ) {
            dmGraceUntilMillis = nowMillis + (settings.graceDurationSeconds * 1_000L)
            dmAllowanceGrantedAtMillis = nowMillis
            dmAllowedViewerSignature = viewerSignature
            pendingDmClickUntilMillis = 0L
        }

        if (
            settings.allowDmOpenedReelsOnly &&
            screen == InstagramScreen.REEL_VIEWER &&
            dmGraceUntilMillis > nowMillis &&
            dmAllowedViewerSignature.isNotEmpty() &&
            viewerSignature.isNotEmpty() &&
            !ReelViewerSignature.matches(dmAllowedViewerSignature, viewerSignature)
        ) {
            clearDmReelAllowance()
        }

        if (!inDmContext && screen != InstagramScreen.REEL_VIEWER) {
            dmGraceUntilMillis = 0L
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

    fun dailyLimitRemainingSeconds(settings: AppSettings, nowMillis: Long): Int? {
        if (!settings.dailyLimitEnabled || settings.allowDmOpenedReelsOnly) {
            return null
        }
        val total = settings.dailyLimitMinutes * 60
        return (total - dailyUsageSeconds(nowMillis)).coerceAtLeast(0)
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

        if (hasPendingExplicitReelsEntry(nowMillis)) {
            return GuardDecision(
                shouldBlock = true,
                reason = "Explicit reels button blocked",
            )
        }

        if (settings.allowDmOpenedReelsOnly) {
            return if (screen == InstagramScreen.REEL_VIEWER && dmGraceUntilMillis > nowMillis) {
                GuardDecision(
                    shouldBlock = false,
                    reason = "DM reel session active",
                )
            } else {
                GuardDecision(
                    shouldBlock = true,
                    reason = "General reels blocked",
                )
            }
        }

        if (settings.dailyLimitEnabled) {
            val remaining = dailyLimitRemainingSeconds(settings, nowMillis) ?: 0
            return if (remaining > 0) {
                GuardDecision(
                    shouldBlock = false,
                    reason = "Within daily limit (${remaining}s left)",
                )
            } else {
                GuardDecision(
                    shouldBlock = true,
                    reason = "Daily limit reached",
                )
            }
        }

        return GuardDecision(
            shouldBlock = true,
            reason = "Reel surface blocked",
        )
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
        const val PENDING_DM_CLICK_WINDOW_MILLIS = 2_500L
        const val DM_VIEWER_SCROLL_CLEAR_DELAY_MILLIS = 750L
        const val EXPLICIT_REELS_ENTRY_WINDOW_MILLIS = 2_000L
        const val SAME_SURFACE_BLOCK_COOLDOWN_MILLIS = 350L
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
