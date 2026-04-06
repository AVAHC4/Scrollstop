package com.example.instadmguard.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AppIntents {

    fun openAccessibilitySettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    fun openInstagram(context: Context): Boolean {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(InstagramConstants.PACKAGE_NAME)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}
