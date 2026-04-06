package com.example.instadmguard.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.example.instadmguard.service.InstagramBlockAccessibilityService

object AccessibilityUtils {

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        return enabledServices.any { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.packageName == context.packageName &&
                serviceInfo.resolveInfo.serviceInfo.name == InstagramBlockAccessibilityService::class.java.name
        }
    }
}
