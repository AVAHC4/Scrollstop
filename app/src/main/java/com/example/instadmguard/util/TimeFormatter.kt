package com.example.instadmguard.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm, dd MMM")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun formatDateTime(timestampMillis: Long): String =
        Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)

    fun formatTime(timestampMillis: Long): String =
        Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

    fun formatDurationSeconds(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}
