package com.example.instadmguard.detector

import com.example.instadmguard.util.InstagramConstants
import java.util.Locale

object DetectionHeuristics {

    const val INSTAGRAM_PACKAGE: String = InstagramConstants.PACKAGE_NAME

    val dmListTextClues = listOf(
        "messages",
        "message requests",
        "notes",
        "search messages",
        "primary",
        "general",
        "requests",
    )

    val dmThreadTextClues = listOf(
        "type a message",
        "message...",
        "write a message",
        "voice message",
        "microphone",
        "camera",
        "call",
        "video chat",
        "seen",
        "active now",
        "reply",
    )

    val dmViewIdClues = listOf(
        "direct",
        "thread",
        "composer",
        "inbox",
        "message_input",
    )

    val genericEngagementTextClues = listOf(
        "like",
        "comment",
        "share",
        "follow",
    )

    val reelActionTextClues = listOf(
        "send message",
        "audio",
        "remix",
        "use template",
        "original audio",
    )

    val reelViewerTextClues = listOf(
        "reel",
        "reels",
        "watch more reels",
        "tap to watch",
    )

    val reelViewIdClues = listOf(
        "clips",
        "reel",
        "viewer",
        "media_viewer",
    )

    val reelsTabClues = listOf(
        "reels tab",
        "clips tab",
        "reels, tab",
        "clips, tab",
        "clips_tab",
        "reels_tab",
    )

    val explicitReelsEntryClickClues = listOf(
        "reels tab",
        "clips tab",
        "reels, tab",
        "clips, tab",
        "clips_tab",
        "reels_tab",
        "tab_bar_button_clips",
        "tab_bar_button_reels",
    )

    val dmSharedReelClickClues = listOf(
        "shared a reel",
        "sent a reel",
        "replied to a reel",
        "tap to watch",
        "reel by",
        "watch reel",
        "play reel",
        "view reel",
        "shared a clip",
        "sent a clip",
        "clips_viewer",
        "reel_viewer",
    )

    val reelViewerClickClues = listOf(
        "clips_viewer",
        "reel_viewer",
        "media_viewer",
        "watch more reels",
    )

    val exploreClues = listOf(
        "explore",
        "discover",
        "search",
        "search_tab",
    )

    val homeClues = listOf(
        "home",
        "following",
        "home_tab",
    )

    fun normalize(values: Collection<String>): List<String> =
        values
            .mapNotNull { value ->
                value.trim()
                    .lowercase(Locale.US)
                    .takeIf { it.isNotEmpty() }
            }
            .distinct()

    fun matchedClues(corpus: Collection<String>, clues: Collection<String>): List<String> =
        clues.filter { clue -> corpus.any { item -> item.contains(clue) } }

    fun isExplicitReelsEntryClick(summary: String): Boolean {
        val corpus = normalize(summary.split('|'))

        if (matchedClues(corpus, dmSharedReelClickClues).isNotEmpty()) {
            return false
        }

        if (matchedClues(corpus, reelViewerClickClues).isNotEmpty()) {
            return false
        }

        if (matchedClues(corpus, explicitReelsEntryClickClues).isNotEmpty()) {
            return true
        }

        return corpus.any { it == "reels" || it == "clips" }
    }
}
