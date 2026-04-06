package com.example.instadmguard.model

data class ServiceStatus(
    val serviceConnected: Boolean = false,
    val inDmContext: Boolean = false,
    val lastDetectedScreen: InstagramScreen = InstagramScreen.OTHER,
    val lastDecision: String = "Waiting for Instagram",
    val pausedUntilMillis: Long = 0L,
    val dmGraceUntilMillis: Long = 0L,
    val dailyLimitUsedSeconds: Int = 0,
    val lastDetectorSummary: String = "",
    val lastEventSummary: String = "",
)
