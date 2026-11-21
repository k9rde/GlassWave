package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import com.example.R
import com.example.ui.HardwarePttBridge
import kotlin.math.abs

/**
 * Draggable Liquid Glass floating PTT bubble rendered over other apps when SYSTEM_ALERT_WINDOW is granted.
 * Tap-and-hold (or tap-toggle) triggers live transmission cleanly.
 */
class FloatingPttService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: ImageView? = null
    private var isTransmittingToggle = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        val density = resources.displayMetrics.density
        val sizePx = (64 * density).toInt()
        val padPx = (14 * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).toInt()
            y = (220 * density).toInt()
        }

        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            setPadding(padPx, padPx, padPx, padPx)
            contentDescription = getString(R.string.app_name)
            background = createGlassCircle(active = false, density = density)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        iconView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager?.updateViewLayout(iconView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        isTransmittingToggle = !isTransmittingToggle
                        iconView.background = createGlassCircle(isTransmittingToggle, density)
                        if (isTransmittingToggle) {
                            HardwarePttBridge.onFloatingStart?.invoke()
                        } else {
                            HardwarePttBridge.onFloatingStop?.invoke()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = iconView
        runCatching { windowManager?.addView(iconView, params) }
    }

    private fun createGlassCircle(active: Boolean, density: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (active) {
                intArrayOf(Color.argb(230, 16, 185, 129), Color.argb(215, 5, 150, 105))
            } else {
                intArrayOf(Color.argb(215, 56, 189, 248), Color.argb(200, 15, 23, 42))
            }
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), Color.argb(190, 255, 255, 255))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTransmittingToggle) {
            HardwarePttBridge.onFloatingStop?.invoke()
        }
        bubbleView?.let { runCatching { windowManager?.removeView(it) } }
        bubbleView = null
    }

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
            runCatching { context.startService(Intent(context, FloatingPttService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingPttService::class.java)) }
        }
    }
}
