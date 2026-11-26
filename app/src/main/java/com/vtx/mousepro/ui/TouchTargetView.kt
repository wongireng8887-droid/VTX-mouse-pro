package com.vtx.mousepro.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Lingkaran sasaran untuk Tes Sentuhan: menyala hijau dan menghitung sentuhan yang masuk. */
class TouchTargetView(context: Context) : View(context) {
    var downCount = 0
        private set
    private var hit = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun reset() {
        downCount = 0
        hit = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downCount++
            hit = true
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 3f * d
        if (hit) {
            paint.style = Paint.Style.FILL
            paint.color = argb(70, 0x2DF0B8)
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = if (hit) Vtx.MINT else argb(140, 0xFFFFFF)
        canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = 1.2f * d
        paint.color = argb(if (hit) 200 else 80, 0x2DF0B8)
        canvas.drawCircle(cx, cy, r * 0.66f, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (hit) Vtx.MINT else argb(120, 0x2DF0B8)
        canvas.drawCircle(cx, cy, 5f * d, paint)
    }
}
