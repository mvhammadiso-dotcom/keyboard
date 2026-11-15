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
 * Tugmalar shaffof shisha: orqadagi rangli yorug'lik ular orqali ko'rinadi,
 * yuqori va pastki qirralari yaltiraydi (shisha qalinligi), ustida diagonal yaltirash bor.
 */
class GlassTheme(val dark: Boolean, val density: Float) {

    fun dp(v: Float) = v * density

    // --- Orqa fon ---
    val bgTop: Int = if (dark) 0xFF26272C.toInt() else 0xFFE4E7EE.toInt()
    val bgBottom: Int = if (dark) 0xFF17181B.toInt() else 0xFFD0D4DC.toInt()
    /** Xiralashtirish (blur) yoqilganda shaffof tus */
    val blurTint: Int = if (dark) 0x8C1C1D20.toInt() else 0x99E9EBEF.toInt()
    private val blobCyan = if (dark) 0x405AC8FA else 0x4D5AC8FA
    private val blobViolet = if (dark) 0x478E6CFF else 0x3D8E6CFF
    private val blobWhite = if (dark) 0x14FFFFFF else 0x73FFFFFF
    private val glowColors = if (dark) intArrayOf(0xD95AC8FA.toInt(), 0xD99B7BFF.toInt(), 0xCC64D2FF.toInt())
    else intArrayOf(0xCC3FB6F5.toInt(), 0xB38E6CFF.toInt(), 0xB34FC3F7.toInt())

    // --- Tugmalar (shaffof shisha) ---
    private val charTop = if (dark) 0x57FFFFFF else 0xE0FFFFFF.toInt()
    private val charBottom = if (dark) 0x33FFFFFF else 0xB8FFFFFF.toInt()
    private val funcTop = if (dark) 0x30FFFFFF else 0x73FFFFFF
    private val funcBottom = if (dark) 0x1AFFFFFF else 0x4DFFFFFF
    private val accentTop = 0xFF4FB0FF.toInt()
    private val accentBottom = 0xFF0A6CFF.toInt()
    private val rimTop = if (dark) 0x8CFFFFFF.toInt() else 0xFFFFFFFF.toInt()
    private val rimMid = if (dark) 0x0DFFFFFF else 0x1AFFFFFF
    private val rimBottom = if (dark) 0x40FFFFFF else 0x99FFFFFF.toInt()
    private val highlight = if (dark) 0x33FFFFFF else 0x8CFFFFFF.toInt()
    val shadow: Int = if (dark) 0x80000000.toInt() else 0x2B1B2440
    val pressedChar: Int = if (dark) 0x38FFFFFF else 0x14000000
    val pressedFunc: Int = if (dark) 0x40FFFFFF else 0x99FFFFFF.toInt()

    val text: Int = if (dark) 0xFFFFFFFF.toInt() else 0xFF0E0F11.toInt()
    val textSecondary: Int = if (dark) 0x99FFFFFF.toInt() else 0x8C0E0F11.toInt()
    val textAccent: Int = 0xFFFFFFFF.toInt()
    val accent: Int = 0xFF0A84FF.toInt()
    val divider: Int = if (dark) 0x33FFFFFF else 0x2E000000
    val icon: Int = if (dark) 0xE6FFFFFF.toInt() else 0xD91C1D21.toInt()
    val rippleColor: Int = if (dark) 0xC79BE6FF.toInt() else 0xB34FB8F5.toInt()

    // --- Popup / balon ---
    private val bubbleTop = if (dark) 0xF2707176.toInt() else 0xFAFFFFFF.toInt()
    private val bubbleBottom = if (dark) 0xF25B5C61.toInt() else 0xF5F3F5F8.toInt()

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
    // qirra: tepada yorqin, o'rtada deyarli yo'q, pastda yana yaltiraydi (shisha qalinligi)
    private val rimShader = LinearGradient(
        0f, 0f, 0f, 1f, intArrayOf(rimTop, rimMid, rimMid, rimBottom), floatArrayOf(0f, 0.35f, 0.7f, 1f),
        Shader.TileMode.CLAMP
    )
    private val accentRimShader = LinearGradient(
        0f, 0f, 0f, 1f, intArrayOf(0xE6FFFFFF.toInt(), 0x22FFFFFF, 0x66B5DCFF), floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    private val highlightShader = LinearGradient(
        0f, 0f, 0f, 1f, intArrayOf(highlight, 0x00FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    // diagonal yaltirash (chap yuqori burchakdan)
    private val glareShader = LinearGradient(
        0f, 0f, 1f, 1f, intArrayOf(if (dark) 0x26FFFFFF else 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.38f, 1f),
        Shader.TileMode.CLAMP
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, density * 1.1f)
    }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val tmp = RectF()

    private fun bindV(shader: Shader, r: RectF) {
        matrix.reset()
        matrix.setScale(1f, r.height().coerceAtLeast(1f))
        matrix.postTranslate(0f, r.top)
        shader.setLocalMatrix(matrix)
    }

    private fun bindBox(shader: Shader, r: RectF) {
        matrix.reset()
        matrix.setScale(r.width().coerceAtLeast(1f), r.height().coerceAtLeast(1f))
        matrix.postTranslate(r.left, r.top)
        shader.setLocalMatrix(matrix)
    }

    /** Bitta shisha tugma: soya + shaffof to'ldirish + yaltirash + ikki tomonlama yorqin qirra. */
    fun drawGlass(c: Canvas, r: RectF, radius: Float, fill: Fill, pressed: Float = 0f, withShadow: Boolean = true) {
        if (withShadow) {
            solidPaint.color = shadow
            tmp.set(r.left, r.top + dp(1.2f), r.right, r.bottom + dp(1.2f))
            c.drawRoundRect(tmp, radius, radius, solidPaint)
        }
        val sh = shaders.getValue(fill)
        bindV(sh, r)
        fillPaint.shader = sh
        c.drawRoundRect(r, radius, radius, fillPaint)

        bindV(highlightShader, r)
        fillPaint.shader = highlightShader
        c.drawRoundRect(r, radius, radius, fillPaint)

        if (fill != Fill.ACCENT) {
            bindBox(glareShader, r)
            fillPaint.shader = glareShader
            c.drawRoundRect(r, radius, radius, fillPaint)
        }
        fillPaint.shader = null

        if (pressed > 0f) {
            val base = if (fill == Fill.CHAR || fill == Fill.BUBBLE) pressedChar else pressedFunc
            solidPaint.color = scaleAlpha(base, pressed)
            c.drawRoundRect(r, radius, radius, solidPaint)
        }

        val rs = if (fill == Fill.ACCENT) accentRimShader else rimShader
        bindV(rs, r)
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
        c.translate(0f, dp(2f))
        c.drawPath(path, solidPaint)
        c.restore()
        val sh = shaders.getValue(fill)
        bindV(sh, bounds)
        fillPaint.shader = sh
        c.drawPath(path, fillPaint)
        bindV(highlightShader, bounds)
        fillPaint.shader = highlightShader
        c.drawPath(path, fillPaint)
        bindBox(glareShader, bounds)
        fillPaint.shader = glareShader
        c.drawPath(path, fillPaint)
        fillPaint.shader = null
        bindV(rimShader, bounds)
        rimPaint.shader = rimShader
        c.drawPath(path, rimPaint)
        rimPaint.shader = null
    }

    fun fillRound(c: Canvas, r: RectF, radius: Float, color: Int) {
        solidPaint.shader = null
        solidPaint.color = color
        c.drawRoundRect(r, radius, radius, solidPaint)
    }

    // --- Klaviatura orqa foni: rangli "suyuq" dog'lar ---
    private var bgW = 0
    private var bgH = 0
    private var bgShader: Shader? = null
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** phase: 0..1 sekin harakat (dog'lar suzib yuradi) */
    fun drawBackground(c: Canvas, w: Int, h: Int, translucent: Boolean, phase: Float) {
        if (w <= 0 || h <= 0) return
        val fw = w.toFloat()
        val fh = h.toFloat()
        if (!translucent) {
            if (w != bgW || h != bgH || bgShader == null) {
                bgW = w; bgH = h
                bgShader = LinearGradient(0f, 0f, 0f, fh, bgTop, bgBottom, Shader.TileMode.CLAMP)
            }
            bgPaint.shader = bgShader
            c.drawRect(0f, 0f, fw, fh, bgPaint)
            bgPaint.shader = null
        }
        val a = phase * 2f * Math.PI.toFloat()
        blob(c, fw * (0.12f + 0.06f * kotlin.math.sin(a)), fh * (0.2f + 0.1f * kotlin.math.cos(a)), fw * 0.55f, blobCyan)
        blob(c, fw * (0.88f - 0.07f * kotlin.math.cos(a)), fh * (0.85f + 0.08f * kotlin.math.sin(a)), fw * 0.5f, blobViolet)
        blob(c, fw * (0.5f + 0.12f * kotlin.math.sin(a * 0.5f)), fh * 0.05f, fw * 0.45f, blobWhite)
        // yuqori yaltiroq chiziq (shisha qirrasi)
        bgPaint.color = if (dark) 0x33FFFFFF else 0xCCFFFFFF.toInt()
        c.drawRect(0f, 0f, fw, maxOf(1f, density * 0.9f), bgPaint)
    }

    private fun blob(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        glowPaint.shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, glowPaint)
        glowPaint.shader = null
    }

    /** Bosilgan joyda tarqaladigan yorug'lik (tugmalar orqasida). */
    fun drawGlow(c: Canvas, x: Float, y: Float, radius: Float, colorIndex: Int, alpha: Float) {
        if (alpha <= 0f || radius <= 1f) return
        val col = scaleAlpha(glowColors[colorIndex % glowColors.size], alpha)
        glowPaint.shader = RadialGradient(x, y, radius, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
    }

    /** Shakl ichida yumshoq oq yorug'lik (suv tomchisi markazi). */
    fun drawGlowClipped(c: Canvas, clip: Path, x: Float, y: Float, radius: Float, alpha: Float) {
        if (alpha <= 0f || radius <= 1f) return
        val col = scaleAlpha(rippleColor, alpha)
        c.save()
        c.clipPath(clip)
        glowPaint.shader = RadialGradient(x, y, radius, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
        c.restore()
    }

    /** Tugma ichida tarqaladigan suv to'lqini (halqa). */
    fun drawRipple(c: Canvas, clip: Path, x: Float, y: Float, radius: Float, alpha: Float) {
        if (alpha <= 0f || radius <= 1f) return
        val col = scaleAlpha(rippleColor, alpha)
        c.save()
        c.clipPath(clip)
        glowPaint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(scaleAlpha(col, 0.1f), scaleAlpha(col, 0.18f), col, col and 0x00FFFFFF),
            floatArrayOf(0f, 0.68f, 0.9f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
        c.restore()
    }

    companion object {
        fun scaleAlpha(color: Int, f: Float): Int {
            val a = ((color ushr 24) * f.coerceIn(0f, 1f)).toInt()
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
