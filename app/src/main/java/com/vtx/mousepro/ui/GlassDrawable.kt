package com.vtx.mousepro.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Permukaan kaca: isi gradien tembus pandang, tepi gradien (terang di kiri atas), kilau tipis di sisi atas,
 * dan (opsional) cahaya hijau di dalam tepi. Dipakai sebagai background View apa pun.
 *
 * @param radius    sudut membulat dalam piksel
 * @param density   kepadatan layar (untuk lebar garis 1dp)
 * @param strength  seberapa terang kaca (1 = normal)
 * @param glow      tambahkan cahaya hijau di tepi dalam
 * @param filled    mode tombol utama: diisi gradien hijau
 */
class GlassDrawable(
    private val radius: Float,
    private val density: Float,
    private val strength: Float = 1f,
    private val accent: Int = Vtx.MINT,
    private val glow: Boolean = false,
    private val filled: Boolean = false
) : Drawable() {
    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var alphaValue = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val sw = density
        rect.set(bounds.left + sw, bounds.top + sw, bounds.right - sw, bounds.bottom - sw)
        val l = rect.left
        val t = rect.top
        val r = rect.right
        val b = rect.bottom
        val rgb = accent and 0xFFFFFF

        if (filled) {
            fillPaint.shader = LinearGradient(
                0f, t, 0f, b,
                intArrayOf(argb(235, 0x3CF5C8), argb(220, 0x12A57F)), null, Shader.TileMode.CLAMP
            )
        } else {
            fillPaint.shader = LinearGradient(
                0f, t, 0f, b,
                intArrayOf(argb((36 * strength).toInt(), 0xFFFFFF), argb((12 * strength).toInt(), 0xFFFFFF)),
                null, Shader.TileMode.CLAMP
            )
        }

        strokePaint.strokeWidth = sw
        strokePaint.shader = LinearGradient(
            l, t, r, b,
            intArrayOf(argb(130, 0xFFFFFF), argb(46, rgb), argb(20, 0xFFFFFF)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )

        shinePaint.strokeWidth = sw * 1.3f
        val sl = l + radius
        val sr = r - radius
        if (sr > sl) {
            shinePaint.shader = LinearGradient(
                sl, t, sr, t,
                intArrayOf(argb(0, 0xFFFFFF), argb(160, 0xFFFFFF), argb(0, 0xFFFFFF)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }

        glowPaint.strokeWidth = sw * 4f
        glowPaint.shader = LinearGradient(
            l, t, r, b,
            intArrayOf(argb(120, rgb), argb(26, rgb)), null, Shader.TileMode.CLAMP
        )
        glowPaint.maskFilter = BlurMaskFilter(sw * 5f, BlurMaskFilter.Blur.NORMAL)
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val rr = minOf(radius, rect.height() / 2f)
        fillPaint.alpha = alphaValue
        strokePaint.alpha = alphaValue
        shinePaint.alpha = alphaValue
        glowPaint.alpha = alphaValue
        canvas.drawRoundRect(rect, rr, rr, fillPaint)
        if (glow) canvas.drawRoundRect(rect, rr, rr, glowPaint)
        canvas.drawRoundRect(rect, rr, rr, strokePaint)
        if (rect.width() > rr * 2.5f) {
            val y = rect.top + density * 0.5f
            canvas.drawLine(rect.left + rr, y, rect.right - rr, y, shinePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        shinePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
