package org.nexus.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

class SelectionOverlayView(
    context: Context,
    private val onSelectionDone: (Rect) -> Unit
) : View(context) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var selecting = false

    private val borderPaint = Paint().apply {
        color = Color.rgb(232, 67, 58)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(60, 232, 67, 58)
        style = Paint.Style.FILL
    }
    private val dimPaint = Paint().apply {
        color = Color.argb(90, 0, 0, 0)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                selecting = true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                selecting = false
                val rect = Rect(
                    minOf(startX, currentX).toInt(),
                    minOf(startY, currentY).toInt(),
                    maxOf(startX, currentX).toInt(),
                    maxOf(startY, currentY).toInt()
                )
                if (rect.width() > 20 && rect.height() > 20) {
                    onSelectionDone(rect)
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        if (selecting) {
            val left = minOf(startX, currentX)
            val top = minOf(startY, currentY)
            val right = maxOf(startX, currentX)
            val bottom = maxOf(startY, currentY)
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, borderPaint)
        }
    }
}
