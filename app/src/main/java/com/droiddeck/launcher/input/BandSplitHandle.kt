package com.droiddeck.launcher.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.droiddeck.launcher.ui.InputSurfaceView
import kotlin.math.abs

/**
 * The grip on the border between the game and a foldable's band while the band's bar is open: dragging it up makes the game smaller
 * (letterboxed at its sides) and the band and its controls bigger, down the other way; a double tap goes back to the automatic split.
 * It reports how far the finger moved from where it went down; the session moves the border and keeps the split for the window's shape.
 */
class BandSplitHandle(context: Context) : View(context) {
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((dy: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false
    private var lastTap = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val arrow = Path()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        val inset = stroke.strokeWidth
        fill.color = (InputSurfaceView.SURFACE and 0x00ffffff) or ((if (dragging) 240 else 210) shl 24)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, r, r, fill)
        stroke.color = if (dragging) InputSurfaceView.TEXT else InputSurfaceView.LINE
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, r, r, stroke)
        line.color = InputSurfaceView.TEXT
        line.strokeWidth = height * .09f
        val cx = width / 2f
        val cy = height / 2f
        val half = width * .16f
        canvas.drawLine(cx - half, cy, cx + half, cy, line)
        // an up and a down chevron at the ends: the border moves both ways
        val a = height * .16f
        for ((x, up) in listOf(width * .14f to true, width * .86f to false, width * .22f to false, width * .78f to true)) {
            val tip = if (up) cy - a * .9f else cy + a * .9f
            val base = if (up) cy - a * .1f else cy + a * .1f
            arrow.reset(); arrow.moveTo(x - a * .8f, base); arrow.lineTo(x, tip); arrow.lineTo(x + a * .8f, base)
            canvas.drawPath(arrow, line)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = event.rawY; dragging = false; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                if (!dragging && abs(dy) > slop) { dragging = true; onDragStart?.invoke(); invalidate() }
                if (dragging) onDrag?.invoke(dy)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) { dragging = false; onDragEnd?.invoke(); invalidate() }
                else {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTap < ViewConfiguration.getDoubleTapTimeout()) {
                        lastTap = 0L
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onReset?.invoke()
                    } else lastTap = now
                }
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) { dragging = false; onDragEnd?.invoke(); invalidate() }
        }
        return true
    }
}
