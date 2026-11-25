package com.vtx.mousepro.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Saklar bergaya kaca: lintasan tembus pandang, menyala hijau saat aktif, bulatan putih meluncur. */
class GlassToggle(context: Context) : View(context) {
    var onToggle: ((Boolean) -> Unit)? = null

    private var state = false
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var checked: Boolean
        get() = state
        set(value) {
            if (state != value) {
                state = value
                animateTo(if (value) 1f else 0f)
            }
        }

    init {
        isClickable = true
        setOnClickListener {
            if (isEnabled) {
                checked = !checked
                onToggle?.invoke(checked)
            }
        }
    }

    /** Atur status tanpa animasi (untuk tampilan awal). */
    fun setInstant(value: Boolean) {
        anim?.cancel()
        state = value
        progress = if (value) 1f else 0f
        invalidate()
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = 180
        a.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(
            resolveSize((54 * d).toInt(), widthMeasureSpec),
            resolveSize((32 * d).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(d, d, w - d, h - d)

        paint.style = Paint.Style.FILL
        paint.color = mix(argb(48, 0xFFFFFF), argb(225, 0x1FD6A4), progress)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = mix(argb(95, 0xFFFFFF), argb(190, 0x9CFFE6), progress)
        canvas.drawRoundRect(rect, r, r, paint)

        val pad = 3f * d
        val tr = r - pad
        val cx = (pad + tr) + (w - 2f * (pad + tr)) * progress
        paint.style = Paint.Style.FILL
        paint.color = argb(70, 0x000000)
        canvas.drawCircle(cx, h / 2f + d, tr, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, h / 2f, tr, paint)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
