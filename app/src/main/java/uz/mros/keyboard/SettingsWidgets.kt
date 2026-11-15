package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** iOS uslubidagi o'chirgich (switch). */
class IosSwitch(context: Context, private val dark: Boolean) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var isOn = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private var progress = 0f
    private var lastFrame = 0L
    private val track = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobShadow = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setChecked(v: Boolean, animate: Boolean) {
        isOn = v
        if (!animate) progress = if (v) 1f else 0f
        lastFrame = 0L
        invalidate()
    }

    fun toggle() {
        setChecked(!isOn, true)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onChange?.invoke(isOn)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(51f).toInt(), dp(31f).toInt())
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val ia = 1f - t
        val aa = ((a ushr 24) * ia + (b ushr 24) * t).toInt()
        val r = (((a shr 16) and 255) * ia + ((b shr 16) and 255) * t).toInt()
        val g = (((a shr 8) and 255) * ia + ((b shr 8) and 255) * t).toInt()
        val bl = ((a and 255) * ia + (b and 255) * t).toInt()
        return (aa shl 24) or (r shl 16) or (g shl 8) or bl
    }

    override fun onDraw(c: Canvas) {
        val target = if (isOn) 1f else 0f
        val now = SystemClock.uptimeMillis()
        if (abs(progress - target) > 0.001f) {
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.05f)
            lastFrame = now
            val step = dt / 0.22f
            progress = if (progress < target) minOf(target, progress + step) else maxOf(target, progress - step)
            postInvalidateOnAnimation()
        } else {
            progress = target
            lastFrame = 0L
        }
        val w = width.toFloat()
        val h = height.toFloat()
        track.set(0f, 0f, w, h)
        val off = if (dark) 0xFF39393D.toInt() else 0xFFE9E9EA.toInt()
        paint.color = blend(off, 0xFF34C759.toInt(), progress)
        c.drawRoundRect(track, h / 2f, h / 2f, paint)
        val r = h / 2f - dp(2f)
        val ease = progress * progress * (3f - 2f * progress)
        val cx = dp(2f) + r + (w - 2 * dp(2f) - 2 * r) * ease
        knobShadow.color = 0x26000000
        c.drawCircle(cx, h / 2f + dp(1.5f), r, knobShadow)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, h / 2f, r, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) toggle()
        return true
    }
}

/** Sozlamalar qatoridagi rangli kvadrat ikonka (iOS Settings kabi). */
class IconTile(
    context: Context,
    private val color: Int,
    private val draw: (Canvas, Float, Float, Float, Paint) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 1.7f * density
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (30 * density).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        rect.set(0f, 0f, w, w)
        bg.color = color
        c.drawRoundRect(rect, w * 0.24f, w * 0.24f, bg)
        shine.shader = LinearGradient(0f, 0f, 0f, w, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, w * 0.24f, w * 0.24f, shine)
        draw(c, w / 2f, w / 2f, w * 0.62f, icon)
    }
}
