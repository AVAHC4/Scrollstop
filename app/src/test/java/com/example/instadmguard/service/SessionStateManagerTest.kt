package com.example.instadmguard.service

import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.model.InstagramScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateManagerTest {

    private val settings = AppSettings()

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
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, emptySet(), nowMillis + 100L, settings)

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
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, emptySet(), nowMillis + 100L, settings)
        // Navigate away to a non-DM surface well after the recent-DM context expires (8s)
        manager.onScreenDetected(InstagramScreen.OTHER, emptySet(), nowMillis + 10_000L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 10_200L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `dm allowance expires if the viewer opens too long after the dm tap`() {
        val manager = SessionStateManager()
        val nowMillis = 55_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        // Open the reel viewer well past the 5000ms pending-click window
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, emptySet(), nowMillis + 5_200L, settings)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 5_250L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `dm grace window allows reel viewing for configured duration`() {
        val manager = SessionStateManager()
        val nowMillis = 60_000L
        val viewerSignature = setOf("friend_creator", "shared reel caption")

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, viewerSignature, nowMillis + 100L, settings)
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, viewerSignature, nowMillis + 1_000L, settings)

        // Still within the 30s grace window
        val allowedDecision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 20_000L,
            )
        assertFalse(allowedDecision.shouldBlock)

        // Past the 30s grace window
        val blockedDecision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 31_000L,
            )
        assertTrue(blockedDecision.shouldBlock)
    }

    @Test
    fun `dm allowance clears when the reel viewer signature changes`() {
        val manager = SessionStateManager()
        val nowMillis = 62_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(
            InstagramScreen.REEL_VIEWER,
            setOf("friend_creator", "shared reel caption"),
            nowMillis + 100L,
            settings,
        )
        manager.onScreenDetected(
            InstagramScreen.REEL_VIEWER,
            setOf("another_creator", "unrelated reel topic"),
            nowMillis + 1_000L,
            settings,
        )

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 1_100L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `viewer scroll clears the dm allowance`() {
        val manager = SessionStateManager()
        val nowMillis = 63_000L

        manager.onScreenDetected(InstagramScreen.DM_THREAD, emptySet(), nowMillis, settings)
        manager.noteDmClick(nowMillis + 50L, "shared reel")
        manager.onScreenDetected(
            InstagramScreen.REEL_VIEWER,
            setOf("friend_creator", "shared reel caption"),
            nowMillis + 100L,
            settings,
        )

        assertTrue(manager.clearDmReelAllowanceForViewerScroll(nowMillis + 500L))

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = settings,
                nowMillis = nowMillis + 600L,
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
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, emptySet(), nowMillis + 100L, settings)
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
        manager.onScreenDetected(InstagramScreen.REEL_VIEWER, emptySet(), nowMillis + 100L, settings)
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
    fun `legacy relaxed mode still blocks general reels`() {
        val manager = SessionStateManager()
        val relaxedSettings = settings.copy(allowDmOpenedReelsOnly = false)

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = relaxedSettings,
                nowMillis = 95_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `explicit reels entry blocks even if legacy relaxed mode is requested`() {
        val manager = SessionStateManager()
        val relaxedSettings = settings.copy(allowDmOpenedReelsOnly = false)

        manager.noteExplicitReelsEntryClick()

        val decision =
            manager.buildDecision(
                screen = InstagramScreen.REEL_VIEWER,
                settings = relaxedSettings,
                nowMillis = 96_000L,
            )

        assertTrue(decision.shouldBlock)
    }

    @Test
    fun `daily limit setting cannot allow general reels`() {
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
