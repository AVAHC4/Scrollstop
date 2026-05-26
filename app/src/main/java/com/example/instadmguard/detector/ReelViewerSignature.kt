package com.example.instadmguard.detector

object ReelViewerSignature {

    private const val MAX_SIGNATURE_TERMS = 8
    private const val MIN_SIGNATURE_LENGTH = 3
    private const val MIN_PARTIAL_MATCH_LENGTH = 6

    private val NUMERIC_PUNCTUATION = setOf('.', ',', '-', ':')

    private val IGNORED_SIGNATURE_CLUES =
        setOf(
            "like",
            "comment",
            "share",
            "follow",
            "send message",
            "audio",
            "original audio",
            "remix",
            "use template",
            "watch more reels",
            "tap to watch",
            "reel",
            "reels",
            "verified",
            "sponsored",
            "home",
            "explore",
            "messages",
            "search",
            "meta ai",
            "more",
            "see translation",
        )

    fun fromSnapshot(snapshot: NodeSnapshot): Set<String> =
        DetectionHeuristics.normalize(snapshot.texts + snapshot.contentDescriptions)
            .asSequence()
            .filter { it.length >= MIN_SIGNATURE_LENGTH }
            .filterNot(::isIgnoredSignatureTerm)
            .sortedByDescending(String::length)
            .take(MAX_SIGNATURE_TERMS)
            .toSet()

    fun matches(
        reference: Set<String>,
        candidate: Set<String>,
    ): Boolean {
        if (reference.isEmpty() || candidate.isEmpty()) {
            return false
        }

        val matchingTermCount =
            reference.count { referenceTerm ->
                candidate.any { candidateTerm ->
                    termsMatch(referenceTerm, candidateTerm)
                }
            }

        val requiredMatchingTerms = if (reference.size >= 2) 2 else 1
        return matchingTermCount >= requiredMatchingTerms
    }

    private fun termsMatch(
        referenceTerm: String,
        candidateTerm: String,
    ): Boolean {
        if (referenceTerm == candidateTerm) {
            return true
        }

        return candidateTerm.length >= MIN_PARTIAL_MATCH_LENGTH &&
            referenceTerm.length >= MIN_PARTIAL_MATCH_LENGTH &&
            (candidateTerm.contains(referenceTerm) || referenceTerm.contains(candidateTerm))
    }

    private fun isIgnoredSignatureTerm(value: String): Boolean {
        if (value.all { it.isDigit() || it.isWhitespace() || it in NUMERIC_PUNCTUATION }) {
            return true
        }

        return IGNORED_SIGNATURE_CLUES.any(value::contains)
    }
}
