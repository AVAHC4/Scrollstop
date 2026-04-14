package com.example.instadmguard.service

import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.model.InstagramScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateManagerTest {

    private val settings = AppSettings()
    private val sharedReelSignature = setOf("friend_creator", "shared reel caption")
    private val otherReelSignature = setOf("other_creator", "different reel caption")

    @Test
    fun `reels blocking re-arms immediately after returning to a safe surface`() {
        val manager = SessionStateManager()
        val blockedAt = 10_000L

        manager.onScreenDetected(InstagramScreen.REELS_TAB, emptySet(), blockedAt, settings)
        manager.markBlocked(blockedAt)

        assertFalse(manager.canBlockNow(InstagramScreen.REELS_TAB, blockedAt + 100L))

        manager.onScreenDetected(InstagramScreen.OTHER, emptySet(), blockedAt + 120L, settings)

        assertTrue(manager.canBlockNow(InstagramScreen.REELS_TAB, blockedAt + 150L))
    }

    @Test
    fun `home feed reel surface now triggers a block decision`() {
        val manager = SessionStateManager()
        val decision =
            manager.buildDecision(
                screen = InstagramScreen.HOME_REEL,
                settings = settings,
                nowMillis = 15_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `reel viewer blocks by default outside the dm allowance`() {
        val manager = SessionStateManager()
        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = 16_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `reels tab blocks by default outside the dm allowance`() {
        val manager = SessionStateManager()
        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = 17_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `same reel surface still has a short debounce after a block`() {
        val manager = SessionStateManager()
        val blockedAt = 20_000L

        manager.onScreenDetected(InstagramScreen.REELS_TAB, emptySet(), blockedAt, settings)
        manager.markBlocked(blockedAt)

        assertFalse(manager.canBlockNow(InstagramScreen.REELS_TAB, blockedAt + 100L))
        assertTrue(manager.canBlockNow(InstagramScreen.REELS_TAB, blockedAt + 200L))
    }

    @Test
    fun `dm-opened reel viewer remains allowed during the dm grace window`() {
        val manager = SessionStateManager()
        val nowMillis = 30_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 150L,
            )

        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `dm reel blocks again after leaving the viewer`() {
        val manager = SessionStateManager()
        val nowMillis = 40_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)
        manager.onScreenDetected(InstagramScreen.OTHER, emptySet(), nowMillis + 500L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 700L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `scrolling away from the dm-opened reel allowance re-blocks the viewer`() {
        val manager = SessionStateManager()
        val nowMillis = 50_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)

        assertTrue(manager.clearDmReelAllowanceForViewerScroll(nowMillis + 150L))

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 200L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `dm allowance expires if the viewer opens too long after the dm tap`() {
        val manager = SessionStateManager()
        val nowMillis = 55_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 1_400L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 1_450L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `different reel viewer blocks after the dm-opened signature changes`() {
        val manager = SessionStateManager()
        val nowMillis = 60_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, otherReelSignature, nowMillis + 1_000L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 1_100L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `pending dm click alone does not allow the reels tab`() {
        val manager = SessionStateManager()
        val nowMillis = 65_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = nowMillis + 100L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `explicit reels button click blocks even while dm allowance is active`() {
        val manager = SessionStateManager()
        val nowMillis = 70_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)
        manager.noteExplicitReelsEntryClick()

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = nowMillis + 250L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `explicit reels button click remains armed through delayed transition after dm reel`() {
        val manager = SessionStateManager()
        val nowMillis = 80_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, sharedReelSignature, nowMillis + 100L, settings)
        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis + 1_000L, settings)
        manager.noteExplicitReelsEntryClick()

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = nowMillis + 6_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `clearing explicit reels entry falls back to dm-only blocking`() {
        val manager = SessionStateManager()
        val nowMillis = 90_000L

        manager.noteExplicitReelsEntryClick()

        val delayedDecision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = nowMillis + 60_000L,
            )

        assertTrue(delayedDecision.shouldBlock)

        manager.clearExplicitReelsEntry()

        val clearedDecision =
            manager.buildDecision(
                screen = InstagramScreen.REELS_TAB,
                settings = settings,
                nowMillis = nowMillis + 60_100L,
            )

        assertTrue(clearedDecision.shouldBlock)
    }

    @Test
    fun `general reels are allowed when dm-only mode is off and no daily limit is active`() {
        val manager = SessionStateManager()
        val relaxedSettings = settings.copy(allowDmOpenedReelsOnly = false)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = relaxedSettings,
                nowMillis = 95_000L,
            )

        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `explicit reels entry does not override relaxed mode`() {
        val manager = SessionStateManager()
        val relaxedSettings = settings.copy(allowDmOpenedReelsOnly = false)

        manager.noteExplicitReelsEntryClick()

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = relaxedSettings,
                nowMillis = 96_000L,
            )

        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `general reels block when the daily limit is exhausted`() {
        val manager = SessionStateManager()
        val limitedSettings =
            settings.copy(
                allowDmOpenedReelsOnly = false,
                dailyLimitEnabled = true,
                dailyLimitMinutes = 1,
            )
        val nowMillis = 100_000L

        manager.addDailyUsageSeconds(nowMillis, 60)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = limitedSettings,
                nowMillis = nowMillis + 1_000L,
            )

        assertTrue(decision.shouldBlock)
    }
}
