package com.example.instadmguard.detector

import com.example.instadmguard.model.InstagramScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramScreenDetectorTest {

    private val detector = InstagramScreenDetector()

    @Test
    fun `detects dm thread from composer clues`() {
        val snapshot =
            NodeSnapshot(
                packageName = DetectionHeuristics.INSTAGRAM_PACKAGE,
                texts = setOf("Type a message", "Seen 1h ago"),
                contentDescriptions = setOf("Camera"),
                viewIds = setOf("com.instagram.android:id/direct_thread_fragment"),
                classNames = emptySet(),
                nodeCount = 42,
            )

        val result = detector.detect(snapshot)

        assertEquals(InstagramScreen.DM_THREAD, result.screen)
    }

    @Test
    fun `detects reel viewer from reel action cluster`() {
        val snapshot =
            NodeSnapshot(
                packageName = DetectionHeuristics.INSTAGRAM_PACKAGE,
                texts = setOf("Like", "Comment", "Share", "Original audio"),
                contentDescriptions = setOf("Send message"),
                viewIds = setOf("com.instagram.android:id/clips_viewer_root"),
                classNames = emptySet(),
                nodeCount = 67,
            )

        val result = detector.detect(snapshot)

        assertEquals(InstagramScreen.REEL_VIEWER, result.screen)
    }

    @Test
    fun `detects explore reels when explore clue coexists with reel ui`() {
        val snapshot =
            NodeSnapshot(
                packageName = DetectionHeuristics.INSTAGRAM_PACKAGE,
                texts = setOf("Explore", "Like", "Comment", "Share", "Original audio"),
                contentDescriptions = setOf("Search", "Follow"),
                viewIds = setOf(
                    "com.instagram.android:id/search_tab",
                    "com.instagram.android:id/clips_viewer_root",
                ),
                classNames = emptySet(),
                nodeCount = 73,
            )

        val result = detector.detect(snapshot)

        assertEquals(InstagramScreen.EXPLORE_REELS, result.screen)
    }

    @Test
    fun `does not misclassify a normal home feed post as reels`() {
        val snapshot =
            NodeSnapshot(
                packageName = DetectionHeuristics.INSTAGRAM_PACKAGE,
                texts = setOf("Like", "Comment", "Share"),
                contentDescriptions = setOf("Home"),
                viewIds = setOf("com.instagram.android:id/home_tab"),
                classNames = emptySet(),
                nodeCount = 58,
            )

        val result = detector.detect(snapshot)

        assertEquals(InstagramScreen.OTHER, result.screen)
    }
}
