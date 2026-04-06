package com.example.instadmguard.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OverlayController(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun show(
        message: String,
        dismissible: Boolean,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (overlayView != null) {
            return
        }

        val container =
            FrameLayout(service).apply {
                setBackgroundColor(Color.argb(222, 15, 22, 33))
            }

        val card =
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(Color.parseColor("#132A3E"))
            }

        val title =
            TextView(service).apply {
                text = message
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            }

        val subtitle =
            TextView(service).apply {
                text =
                    if (dismissible) {
                        "Dismiss this overlay or back out of the reel."
                    } else {
                        "Instagram reels are blocked in this mode."
                    }
                setTextColor(Color.parseColor("#D7E8EE"))
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, dp(12), 0, 0)
            }

        card.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        if (dismissible) {
            val button =
                Button(service).apply {
                    text = "Dismiss"
                    setOnClickListener {
                        hide()
                        onDismiss?.invoke()
                    }
                }
            val buttonParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(18)
                }
            card.addView(button, buttonParams)
        }

        container.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        val flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                if (dismissible) {
                    0
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }

        val layoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            }

        runCatching {
            windowManager.addView(container, layoutParams)
            overlayView = container
        }
    }

    fun hide() {
        val view = overlayView ?: return
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    fun dispose() {
        hide()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).toInt()
}
