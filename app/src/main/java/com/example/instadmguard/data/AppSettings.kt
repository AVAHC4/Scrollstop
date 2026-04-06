package com.example.instadmguard.data

import com.example.instadmguard.model.BlockMode

data class AppSettings(
    val masterEnabled: Boolean = true,
    val allowDmOpenedReelsOnly: Boolean = true,
    val blockMode: BlockMode = BlockMode.OVERLAY_AND_BACK,
    val graceDurationSeconds: Int = 30,
    val pauseUntilMillis: Long = 0L,
    val debugMode: Boolean = false,
    val overlayDismissible: Boolean = false,
    val dailyLimitEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 10,
)
