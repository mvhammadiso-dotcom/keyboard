package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Bufer (clipboard) tarixi. Bosish — joylash, bosib turish — o'chirish. */
class ClipboardPanelView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        fun clipItems(): List<String>
        fun onClipPaste(text: String)
        fun onClipDelete(text: String)
        fun onClipFeedback()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var preferredHeight = 0
    private val scroll = DragScroller(context, this, false)
    private var items: List<String> = emptyList()
    private val rowH get() = dp(50f)
    private val gap get() = dp(8f)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(15f) }
    private val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(14.5f)
        textAlign = Paint.Align.CENTER
    }
    private val tmp = RectF()

    fun refresh() {
        items = host.clipItems()
        scroll.reset()
        updateMax()
        invalidate()
    }

    private fun updateMax() {
        scroll.max = items.size * (rowH + gap) + gap - height
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), if (preferredHeight > 0) preferredHeight else dp(216f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMax()
    }

    override fun onDraw(c: Canvas) {
        val g = host.glass
        val animating = scroll.update()
        if (items.isEmpty()) {
            emptyPaint.color = g.textSecondary
            c.drawText("Bufer boʻsh", width / 2f, height / 2f - dp(8f), emptyPaint)
            c.drawText("Nusxa olingan matnlar shu yerda chiqadi", width / 2f, height / 2f + dp(14f), emptyPaint)
            return
        }
        val y0 = gap - scroll.pos
        for (i in items.indices) {
            val top = y0 + i * (rowH + gap)
            if (top > height || top + rowH < 0) continue
            tmp.set(dp(8f), top, width - dp(8f), top + rowH)
            g.drawGlass(c, tmp, dp(12f), GlassTheme.Fill.CHAR, if (i == pressedIndex && !scroll.dragging) 1f else 0f)
            textPaint.color = g.text
            val one = items[i].replace('\n', ' ').trim()
            val t = TextUtils.ellipsize(one, textPaint, tmp.width() - dp(24f), TextUtils.TruncateAt.END).toString()
            c.drawText(t, tmp.left + dp(12f), tmp.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
        if (animating) postInvalidateOnAnimation()
    }

    private var pressedIndex = -1
    private var longFired = false
    private val longPress = Runnable {
        val i = pressedIndex
        if (i in items.indices && !scroll.dragging) {
            longFired = true
            host.onClipFeedback()
            host.onClipDelete(items[i])
            items = host.clipItems()
            updateMax()
            pressedIndex = -1
            invalidate()
        }
    }

    private fun indexAt(y: Float): Int {
        val rel = y + scroll.pos - gap
        if (rel < 0) return -1
        val i = (rel / (rowH + gap)).toInt()
        val within = rel - i * (rowH + gap)
        return if (within <= rowH && i in items.indices) i else -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroll.onTouch(e)
                longFired = false
                pressedIndex = if (scroll.stoppedFling) -1 else indexAt(e.y)
                if (pressedIndex >= 0) postDelayed(longPress, 550L)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                scroll.onTouch(e)
                if (scroll.dragging) {
                    removeCallbacks(longPress)
                    pressedIndex = -1
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val wasDrag = scroll.dragging
                scroll.onTouch(e)
                val i = pressedIndex
                if (!wasDrag && !longFired && i in items.indices) {
                    host.onClipFeedback()
                    host.onClipPaste(items[i])
                }
                pressedIndex = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                scroll.onTouch(e)
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    @Suppress("unused")
    private fun clampPos() = max(0f, scroll.pos)
}
