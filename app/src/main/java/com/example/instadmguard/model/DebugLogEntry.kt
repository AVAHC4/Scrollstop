package com.example.instadmguard.model

data class DebugLogEntry(
    val timestampMillis: Long,
    val screen: InstagramScreen,
    val message: String,
)
