package com.example.instadmguard.detector

data class NodeSnapshot(
    val packageName: String?,
    val texts: Set<String>,
    val contentDescriptions: Set<String>,
    val viewIds: Set<String>,
    val classNames: Set<String>,
    val nodeCount: Int,
)

data class DetectionResult(
    val screen: com.example.instadmguard.model.InstagramScreen,
    val scores: Map<com.example.instadmguard.model.InstagramScreen, Int>,
    val winningClues: List<String>,
    val summary: String,
)
