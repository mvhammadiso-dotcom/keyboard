package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Emoji qidiruv rejimi: qidiruv satri + natijalar qatori. Pastda oddiy harflar klaviaturasi turadi. */
class EmojiSearchView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        val prefs: Prefs
        fun onEmoji(emoji: String)
        fun onEmojiSearchClose()
        fun onEmojiFeedback()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var query: String = ""
        private set

    private var results: List<String> = emptyList()
    private val scroll = DragScroller(context, this, true)

    private val pillRect = RectF()
    private val cancelRect = RectF()
    private val clearRect = RectF()
    private val resultsRect = RectF()
    private val cell get() = dp(46f)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(16f) }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(15.5f)
        textAlign = Paint.Align.CENTER
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(29f)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1.7f) }
    private val tmp = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(98f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cancelW = dp(64f)
        pillRect.set(dp(8f), dp(7f), w - cancelW - dp(4f), dp(43f))
        cancelRect.set(w - cancelW - dp(4f), 0f, w.toFloat(), dp(48f))
        clearRect.set(pillRect.right - dp(36f), pillRect.top, pillRect.right, pillRect.bottom)
        resultsRect.set(0f, dp(48f), w.toFloat(), h.toFloat())
        updateMax()
    }

    fun reset() {
        query = ""
        refresh()
    }

    fun setQuery(q: String) {
        query = q
        refresh()
    }

    fun refresh() {
        results = if (query.isBlank()) {
            host.prefs.recentEmoji
        } else {
            val p = host.prefs
            EmojiData.search(query).map { if (it.hasTones) it.variant(p.toneFor(it.base)) else it.base }
        }
        scroll.reset()
        updateMax()
        invalidate()
    }

    private fun updateMax() {
        scroll.max = results.size * cell + dp(12f) - width
    }

    override fun onDraw(c: Canvas) {
        val g = host.glass
        val animating = scroll.update()
        g.drawGlass(c, pillRect, pillRect.height() / 2f, GlassTheme.Fill.PILL, 0f, withShadow = false)
        strokePaint.color = g.textSecondary
        Icons.search(c, pillRect.left + dp(18f), pillRect.centerY(), dp(17f), strokePaint)
        val tx = pillRect.left + dp(34f)
        val baseY = pillRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        val maxW = clearRect.left - tx - dp(4f)
        if (query.isEmpty()) {
            textPaint.color = g.textSecondary
            c.drawText("Emoji qidirish", tx, baseY, textPaint)
        } else {
            textPaint.color = g.text
            val shown = TextUtils.ellipsize(query, textPaint, maxW, TextUtils.TruncateAt.START).toString()
            c.drawText(shown, tx, baseY, textPaint)
            Icons.close(c, clearRect.centerX(), clearRect.centerY(), dp(14f), strokePaint)
        }
        // miltillovchi kursor
        if ((SystemClock.uptimeMillis() / 530) % 2 == 0L) {
            val cw = if (query.isEmpty()) 0f else min(maxW, textPaint.measureText(query))
            tmp.set(tx + cw + dp(1f), pillRect.top + dp(9f), tx + cw + dp(3f), pillRect.bottom - dp(9f))
            g.fillRound(c, tmp, dp(1f), g.accent)
        }
        cancelPaint.color = g.accent
        c.drawText("Bekor", cancelRect.centerX(), cancelRect.centerY() - (cancelPaint.descent() + cancelPaint.ascent()) / 2f, cancelPaint)

        c.save()
        c.clipRect(resultsRect)
        if (results.isEmpty()) {
            cancelPaint.color = g.textSecondary
            val msg = if (query.isBlank()) "Qidirish uchun yozing" else "Hech narsa topilmadi"
            c.drawText(msg, width / 2f, resultsRect.centerY() + dp(5f), cancelPaint)
        } else {
            val sx = scroll.pos
            val i0 = max(0, floor(sx / cell).toInt())
            val i1 = min(results.size - 1, ceil((sx + width) / cell).toInt())
            val cy = resultsRect.centerY()
            for (i in i0..i1) {
                val cx = dp(6f) + i * cell + cell / 2f - sx
                if (i == pressedIndex && !scroll.dragging) {
                    tmp.set(cx - cell / 2f + dp(2f), cy - cell / 2f + dp(2f), cx + cell / 2f - dp(2f), cy + cell / 2f - dp(2f))
                    g.drawGlass(c, tmp, dp(10f), GlassTheme.Fill.PILL, 0f, withShadow = false)
                }
                EmojiImages.draw(c, results[i], cx, cy, dp(34f), emojiPaint, host.prefs.emojiStyle == 0)
            }
        }
        c.restore()
        if (animating) postInvalidateOnAnimation() else postInvalidateDelayed(530L)
    }

    private val onImages: () -> Unit = { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EmojiImages.addListener(onImages)
    }

    override fun onDetachedFromWindow() {
        EmojiImages.removeListener(onImages)
        super.onDetachedFromWindow()
    }

    private var pressedIndex = -1
    private var downZone = 0 // 1 natijalar, 2 bekor, 3 tozalash

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downZone = when {
                    resultsRect.contains(e.x, e.y) -> 1
                    cancelRect.contains(e.x, e.y) -> 2
                    query.isNotEmpty() && clearRect.contains(e.x, e.y) -> 3
                    else -> 0
                }
                if (downZone == 1) {
                    scroll.onTouch(e)
                    val i = ((e.x + scroll.pos - dp(6f)) / cell).toInt()
                    pressedIndex = if (!scroll.stoppedFling && i in results.indices) i else -1
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> if (downZone == 1) {
                scroll.onTouch(e)
                if (scroll.dragging) pressedIndex = -1
            }
            MotionEvent.ACTION_UP -> {
                when (downZone) {
                    1 -> {
                        val wasDrag = scroll.dragging
                        scroll.onTouch(e)
                        val i = pressedIndex
                        if (!wasDrag && i in results.indices) {
                            host.onEmojiFeedback()
                            host.onEmoji(results[i])
                        }
                    }
                    2 -> if (cancelRect.contains(e.x, e.y)) host.onEmojiSearchClose()
                    3 -> if (clearRect.contains(e.x, e.y)) setQuery("")
                }
                pressedIndex = -1
                downZone = 0
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                scroll.onTouch(e)
                pressedIndex = -1
                downZone = 0
                invalidate()
            }
        }
        return true
    }
}
