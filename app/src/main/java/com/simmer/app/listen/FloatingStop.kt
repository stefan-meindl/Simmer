package com.simmer.app.listen

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.simmer.app.R

/**
 * A small draggable Stop pill drawn over Instagram while Listen captures. Optional: it only shows when the user
 * allowed "Display over other apps"; the notification Stop always works.
 */
class FloatingStop(private val context: Context, private val onStop: () -> Unit) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val density = context.resources.displayMetrics.density
        val pill = TextView(context).apply {
            text = context.getString(R.string.listen_stop)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            minHeight = (48 * density).toInt()
            minWidth = (72 * density).toInt()
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(0xEED32F2F.toInt())
                cornerRadius = 24 * density
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }
        pill.setOnTouchListener(DragOrTap(params, density))
        try {
            windowManager.addView(pill, params)
            view = pill
        } catch (e: RuntimeException) {
            // Overlay refused (permission revoked meanwhile): the notification Stop remains.
        }
    }

    fun hide() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: RuntimeException) {
                // Already gone.
            }
        }
        view = null
    }

    private inner class DragOrTap(private val params: WindowManager.LayoutParams, density: Float) : View.OnTouchListener {
        private val slop = 8 * density
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dragged || kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                        dragged = true
                        // Gravity END: x grows leftwards.
                        params.x = startX - dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragged) onStop()
            }
            return true
        }
    }
}
