package com.vtx.mousepro.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * Latar Liquid Glass: dasar gelap kehijauan, tiga gumpalan cahaya lembut, dan garis cahaya diagonal.
 * Gumpalan sudah halus (gradien radial), jadi panel kaca di atasnya cukup tembus pandang
 * tanpa perlu blur sungguhan (hemat GPU).
 */
class GlassBackground(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base: Shader? = null
    private var blobA: Shader? = null
    private var blobB: Shader? = null
    private var blobC: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        base = LinearGradient(
            0f, 0f, fw, fh,
            intArrayOf(Vtx.BG0, Vtx.BG1, Vtx.BG0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        blobA = RadialGradient(
            fw * 0.10f, fh * 0.02f, fw * 0.50f,
            intArrayOf(argb(100, 0x2DF0B8), argb(0, 0x2DF0B8)), null, Shader.TileMode.CLAMP
        )
        blobB = RadialGradient(
            fw * 0.92f, fh * 0.98f, fw * 0.45f,
            intArrayOf(argb(85, 0x12A57F), argb(0, 0x12A57F)), null, Shader.TileMode.CLAMP
        )
        blobC = RadialGradient(
            fw * 0.62f, fh * 0.30f, fw * 0.35f,
            intArrayOf(argb(38, 0x1FD6E8), argb(0, 0x1FD6E8)), null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.shader = base
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = blobA
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = blobB
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = blobC
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // garis cahaya diagonal (kiri atas, kanan atas, kanan bawah)
        streak(canvas, -w * 0.02f, h * 0.24f, w * 0.20f, -h * 0.02f, 3f, 170)
        streak(canvas, -w * 0.02f, h * 0.34f, w * 0.26f, -h * 0.02f, 1.5f, 110)
        streak(canvas, w * 0.74f, -h * 0.02f, w * 0.88f, h * 0.20f, 2f, 120)
        streak(canvas, w * 0.80f, -h * 0.02f, w * 0.96f, h * 0.24f, 1.2f, 90)
        streak(canvas, w * 0.90f, h * 1.02f, w * 1.02f, h * 0.80f, 2.5f, 140)
        streak(canvas, w * 0.84f, h * 1.02f, w * 1.02f, h * 0.70f, 1.2f, 90)
    }

    private fun streak(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, widthDp: Float, alpha: Int) {
        val d = resources.displayMetrics.density
        paint.shader = LinearGradient(
            x1, y1, x2, y2,
            intArrayOf(argb(0, 0x2DF0B8), argb(alpha, 0x2DF0B8), argb(0, 0x2DF0B8)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = widthDp * d
        c.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
        paint.shader = null
    }
}
