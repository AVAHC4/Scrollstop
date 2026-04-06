package com.example.instadmguard.detector

import com.example.instadmguard.model.InstagramScreen

class InstagramScreenDetector {

    fun detect(snapshot: NodeSnapshot): DetectionResult {
        if (snapshot.packageName != null && snapshot.packageName != DetectionHeuristics.INSTAGRAM_PACKAGE) {
            return DetectionResult(
                screen = InstagramScreen.OTHER,
                scores = emptyMap(),
                winningClues = emptyList(),
                summary = "Non-Instagram package: ${snapshot.packageName}",
            )
        }

        val texts = DetectionHeuristics.normalize(snapshot.texts)
        val descriptions = DetectionHeuristics.normalize(snapshot.contentDescriptions)
        val ids = DetectionHeuristics.normalize(snapshot.viewIds)
        val combined = texts + descriptions
        val everything = combined + ids

        val dmThreadMatches =
            (DetectionHeuristics.matchedClues(combined, DetectionHeuristics.dmThreadTextClues) +
                DetectionHeuristics.matchedClues(ids, DetectionHeuristics.dmViewIdClues))
                .distinct()
        val dmListMatches =
            DetectionHeuristics.matchedClues(combined, DetectionHeuristics.dmListTextClues).distinct()
        val engagementMatches =
            DetectionHeuristics.matchedClues(combined, DetectionHeuristics.genericEngagementTextClues).distinct()
        val reelActionMatches =
            DetectionHeuristics.matchedClues(combined, DetectionHeuristics.reelActionTextClues).distinct()
        val reelViewerMatches =
            (DetectionHeuristics.matchedClues(everything, DetectionHeuristics.reelViewerTextClues) +
                DetectionHeuristics.matchedClues(ids, DetectionHeuristics.reelViewIdClues))
                .distinct()
        val reelsTabMatches =
            DetectionHeuristics.matchedClues(everything, DetectionHeuristics.reelsTabClues).distinct()
        val exploreMatches =
            DetectionHeuristics.matchedClues(everything, DetectionHeuristics.exploreClues).distinct()
        val homeMatches =
            DetectionHeuristics.matchedClues(everything, DetectionHeuristics.homeClues).distinct()

        val dmThreadScore = (dmThreadMatches.size * 3)
        val dmListScore = (dmListMatches.size * 2) + if ("messages" in combined) 2 else 0

        val engagementScore =
            when {
                engagementMatches.size >= 3 -> 3
                engagementMatches.size == 2 -> 2
                engagementMatches.size == 1 -> 1
                else -> 0
            }
        val hasReelIdentity = reelViewerMatches.isNotEmpty()
        val reelBaseScore =
            if (hasReelIdentity) {
                engagementScore + (reelActionMatches.size * 2) + minOf(reelViewerMatches.size, 3)
            } else {
                0
            }

        val reelViewerScore = reelBaseScore
        val reelsTabScore = reelBaseScore + (reelsTabMatches.size * 2)
        val exploreScore = reelBaseScore + (exploreMatches.size * 2)
        val homeScore = reelBaseScore + (homeMatches.size * 2)

        val screen =
            when {
                dmThreadScore >= 6 && dmThreadScore >= reelBaseScore + 2 -> InstagramScreen.DM_THREAD
                dmListScore >= 4 && dmListScore >= reelBaseScore + 1 -> InstagramScreen.DM_LIST
                reelBaseScore >= 4 -> {
                    when {
                        reelsTabMatches.isNotEmpty() && reelsTabScore >= exploreScore && reelsTabScore >= homeScore ->
                            InstagramScreen.REELS_TAB

                        exploreMatches.isNotEmpty() && exploreScore >= homeScore ->
                            InstagramScreen.EXPLORE_REELS

                        homeMatches.isNotEmpty() ->
                            InstagramScreen.HOME_REEL

                        else -> InstagramScreen.REEL_VIEWER
                    }
                }

                else -> InstagramScreen.OTHER
            }

        val scores =
            mapOf(
                InstagramScreen.DM_THREAD to dmThreadScore,
                InstagramScreen.DM_LIST to dmListScore,
                InstagramScreen.REEL_VIEWER to reelViewerScore,
                InstagramScreen.REELS_TAB to reelsTabScore,
                InstagramScreen.EXPLORE_REELS to exploreScore,
                InstagramScreen.HOME_REEL to homeScore,
            )

        val winningClues =
            when (screen) {
                InstagramScreen.DM_THREAD -> dmThreadMatches
                InstagramScreen.DM_LIST -> dmListMatches
                InstagramScreen.REELS_TAB -> (reelActionMatches + reelViewerMatches + reelsTabMatches).distinct()
                InstagramScreen.EXPLORE_REELS -> (reelActionMatches + reelViewerMatches + exploreMatches).distinct()
                InstagramScreen.HOME_REEL -> (reelActionMatches + reelViewerMatches + homeMatches).distinct()
                InstagramScreen.REEL_VIEWER -> (reelActionMatches + reelViewerMatches).distinct()
                InstagramScreen.OTHER -> emptyList()
            }

        val summary =
            buildString {
                append("nodes=")
                append(snapshot.nodeCount)
                append(" winner=")
                append(screen.name)
                append(" scores(dmThread=")
                append(dmThreadScore)
                append(", dmList=")
                append(dmListScore)
                append(", reelBase=")
                append(reelBaseScore)
                append(", reelsTab=")
                append(reelsTabScore)
                append(", explore=")
                append(exploreScore)
                append(", home=")
                append(homeScore)
                append(") clues=")
                append(winningClues.take(6).joinToString())
            }

        return DetectionResult(
            screen = screen,
            scores = scores,
            winningClues = winningClues,
            summary = summary,
        )
    }
}
