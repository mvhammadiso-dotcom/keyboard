package uz.mros.keyboard

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * "Liquid Glass" uslubidagi ranglar va chizish yordamchilari.
 * Hamma shakllar shu yerda chiziladi, shuning uchun dizaynni bitta fayldan o'zgartirish mumkin.
 */
class GlassTheme(val dark: Boolean, val density: Float) {

    fun dp(v: Float) = v * density

    // --- Orqa fon ---
    val bgTop: Int = if (dark) 0xFF2B2C30.toInt() else 0xFFE6E8EC.toInt()
    val bgBottom: Int = if (dark) 0xFF1B1C1F.toInt() else 0xFFD2D5DB.toInt()
    /** Xiralashtirish (blur) yoqilganda shaffof tus */
    val blurTint: Int = if (dark) 0x991C1D20.toInt() else 0xA6E9EBEF.toInt()
    val glowA: Int = if (dark) 0x1AFFFFFF else 0x66FFFFFF
    val glowB: Int = if (dark) 0x264F8CFF else 0x1F4F8CFF

    // --- Tugmalar ---
    private val charTop = if (dark) 0x5EFFFFFF else 0xF7FFFFFF.toInt()
    private val charBottom = if (dark) 0x45FFFFFF else 0xE3FFFFFF.toInt()
    private val funcTop = if (dark) 0x33FFFFFF else 0x99F4F5F8.toInt()
    private val funcBottom = if (dark) 0x22FFFFFF else 0x73E2E5EA
    private val accentTop = 0xFF47A3FF.toInt()
    private val accentBottom = 0xFF0A6CFF.toInt()
    private val rimTop = if (dark) 0x73FFFFFF else 0xFFFFFFFF.toInt()
    private val rimBottom = if (dark) 0x0FFFFFFF else 0x14000000
    private val highlight = if (dark) 0x2EFFFFFF else 0x73FFFFFF
    val shadow: Int = if (dark) 0x73000000 else 0x30000000
    val pressedChar: Int = if (dark) 0x33FFFFFF else 0x1F000000
    val pressedFunc: Int = if (dark) 0x40FFFFFF else 0x99FFFFFF.toInt()

    val text: Int = if (dark) 0xFFFFFFFF.toInt() else 0xFF0E0F11.toInt()
    val textSecondary: Int = if (dark) 0x99FFFFFF.toInt() else 0x8C0E0F11.toInt()
    val textAccent: Int = 0xFFFFFFFF.toInt()
    val accent: Int = 0xFF0A84FF.toInt()
    val divider: Int = if (dark) 0x33FFFFFF else 0x2E000000
    val icon: Int = if (dark) 0xE6FFFFFF.toInt() else 0xD91C1D21.toInt()

    // --- Popup / balon ---
    private val bubbleTop = if (dark) 0xFF6A6B70.toInt() else 0xFFFFFFFF.toInt()
    private val bubbleBottom = if (dark) 0xFF57585D.toInt() else 0xFFF4F5F7.toInt()

    enum class Fill { CHAR, FUNC, ACCENT, BUBBLE, PILL }

    private fun vGrad(top: Int, bottom: Int) =
        LinearGradient(0f, 0f, 0f, 1f, top, bottom, Shader.TileMode.CLAMP)

    private val shaders = mapOf(
        Fill.CHAR to vGrad(charTop, charBottom),
        Fill.FUNC to vGrad(funcTop, funcBottom),
        Fill.ACCENT to vGrad(accentTop, accentBottom),
        Fill.BUBBLE to vGrad(bubbleTop, bubbleBottom),
        Fill.PILL to vGrad(funcTop, funcBottom),
    )
    private val rimShader = vGrad(rimTop, rimBottom)
    private val accentRimShader = vGrad(0xCCFFFFFF.toInt(), 0x330A3CFF)
    private val highlightShader = LinearGradient(
        0f, 0f, 0f, 1f, intArrayOf(highlight, 0x00FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, density * 0.9f)
    }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val tmp = RectF()

    private fun bind(shader: Shader, r: RectF) {
        matrix.reset()
        matrix.setScale(1f, r.height().coerceAtLeast(1f))
        matrix.postTranslate(0f, r.top)
        shader.setLocalMatrix(matrix)
    }

    /** Bitta shisha tugma: soya + gradient to'ldirish + yaltirash + yorqin chet. */
    fun drawGlass(c: Canvas, r: RectF, radius: Float, fill: Fill, pressed: Float = 0f, withShadow: Boolean = true) {
        if (withShadow) {
            solidPaint.color = shadow
            tmp.set(r.left, r.top + dp(1f), r.right, r.bottom + dp(1f))
            c.drawRoundRect(tmp, radius, radius, solidPaint)
        }
        val sh = shaders.getValue(fill)
        bind(sh, r)
        fillPaint.shader = sh
        c.drawRoundRect(r, radius, radius, fillPaint)

        bind(highlightShader, r)
        fillPaint.shader = highlightShader
        c.drawRoundRect(r, radius, radius, fillPaint)
        fillPaint.shader = null

        if (pressed > 0f) {
            val base = if (fill == Fill.CHAR || fill == Fill.BUBBLE) pressedChar else pressedFunc
            solidPaint.color = scaleAlpha(base, pressed)
            c.drawRoundRect(r, radius, radius, solidPaint)
        }

        val rs = if (fill == Fill.ACCENT) accentRimShader else rimShader
        bind(rs, r)
        rimPaint.shader = rs
        val inset = rimPaint.strokeWidth / 2f
        tmp.set(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
        c.drawRoundRect(tmp, radius, radius, rimPaint)
        rimPaint.shader = null
    }

    /** Ixtiyoriy shakl (balon) uchun shisha. */
    fun drawGlassPath(c: Canvas, path: Path, bounds: RectF, fill: Fill) {
        solidPaint.color = shadow
        c.save()
        c.translate(0f, dp(1.5f))
        c.drawPath(path, solidPaint)
        c.restore()
        val sh = shaders.getValue(fill)
        bind(sh, bounds)
        fillPaint.shader = sh
        c.drawPath(path, fillPaint)
        bind(highlightShader, bounds)
        fillPaint.shader = highlightShader
        c.drawPath(path, fillPaint)
        fillPaint.shader = null
        bind(rimShader, bounds)
        rimPaint.shader = rimShader
        c.drawPath(path, rimPaint)
        rimPaint.shader = null
    }

    fun fillRound(c: Canvas, r: RectF, radius: Float, color: Int) {
        solidPaint.color = color
        c.drawRoundRect(r, radius, radius, solidPaint)
    }

    // --- Klaviatura orqa foni ---
    private var bgW = 0
    private var bgH = 0
    private var bgShader: Shader? = null
    private var glowShaderA: Shader? = null
    private var glowShaderB: Shader? = null
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawBackground(c: Canvas, w: Int, h: Int, translucent: Boolean) {
        if (w <= 0 || h <= 0) return
        if (translucent) {
            // fon rangini oyna o'zi (blur bilan) chizadi
        } else {
            if (w != bgW || h != bgH || bgShader == null) {
                bgW = w; bgH = h
                bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP)
                glowShaderA = RadialGradient(w * 0.15f, 0f, w * 0.6f, glowA, 0x00FFFFFF, Shader.TileMode.CLAMP)
                glowShaderB = RadialGradient(w * 0.9f, h.toFloat(), w * 0.55f, glowB, 0x004F8CFF, Shader.TileMode.CLAMP)
            }
            bgPaint.shader = bgShader
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            bgPaint.shader = glowShaderA
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            bgPaint.shader = glowShaderB
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            bgPaint.shader = null
        }
        // yuqori yaltiroq chiziq (shisha qirrasi)
        bgPaint.color = if (dark) 0x26FFFFFF else 0xB3FFFFFF.toInt()
        c.drawRect(0f, 0f, w.toFloat(), maxOf(1f, density * 0.8f), bgPaint)
    }

    companion object {
        fun scaleAlpha(color: Int, f: Float): Int {
            val a = ((color ushr 24) * f.coerceIn(0f, 1f)).toInt()
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
