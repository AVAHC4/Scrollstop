package com.example.instadmguard.detector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionHeuristicsTest {

    @Test
    fun `recognizes explicit reels tab click summaries`() {
        assertTrue(
            DetectionHeuristics.isExplicitReelsEntryClick(
                "Reels, tab | com.instagram.android:id/clips_tab",
            ),
        )
    }

    @Test
    fun `does not treat reel viewer content as a reels tab click`() {
        assertFalse(
            DetectionHeuristics.isExplicitReelsEntryClick(
                "Like | Comment | Share | com.instagram.android:id/clips_viewer_root",
            ),
        )
    }

    @Test
    fun `does not treat dm shared reel content as a reels tab click`() {
        assertFalse(
            DetectionHeuristics.isExplicitReelsEntryClick(
                "Shared a reel | Tap to watch | Reel",
            ),
        )
    }
}
