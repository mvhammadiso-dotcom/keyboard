package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View

/** Yuqori qator: ikonkalar yoki so'z takliflari (iOS kabi 3 ta katak). */
class ToolbarView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        val themeMode: Int
        fun onToolbarAction(action: Int)
        fun onSuggestionPicked(text: String, isTyped: Boolean)
    }

    companion object {
        const val ACTION_SETTINGS = 1
        const val ACTION_CLIPBOARD = 2
        const val ACTION_THEME = 3
        const val ACTION_HIDE = 4
        const val ACTION_BACK = 5
        const val ACTION_CLEAR_CLIPS = 6
    }

    enum class Mode { NORMAL, CLIPBOARD }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var mode = Mode.NORMAL
        set(v) {
            field = v
            invalidate()
        }

    private var typed: String? = null
    private var suggestions: List<String> = emptyList()

    fun setSuggestions(typedWord: String?, list: List<String>) {
        if (typedWord == typed && list == suggestions) return
        typed = typedWord
        suggestions = list
        invalidate()
    }

    private class Item(val action: Int, val rect: RectF, val text: String? = null, val isTyped: Boolean = false)

    private val items = ArrayList<Item>()
    private var pressed: Item? = null

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1.7f) }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(16.5f)
    }
    private val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(16.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val divPaint = Paint()
    private val tmp = RectF()

    private fun showingSuggestions() = mode == Mode.NORMAL && suggestions.isNotEmpty()

    private fun rebuildItems() {
        items.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val btn = dp(42f)
        when {
            mode == Mode.CLIPBOARD -> {
                items.add(Item(ACTION_BACK, RectF(dp(4f), 0f, dp(4f) + btn * 2.6f, h)))
                items.add(Item(ACTION_CLEAR_CLIPS, RectF(w - dp(4f) - btn, 0f, w - dp(4f), h)))
            }
            showingSuggestions() -> {
                val slotW = w / 3f
                val t = typed ?: ""
                val s0 = suggestions.getOrNull(0)
                val s1 = suggestions.getOrNull(1)
                items.add(Item(0, RectF(0f, 0f, slotW, h), "«$t»", isTyped = true))
                if (s0 != null) items.add(Item(0, RectF(slotW, 0f, slotW * 2, h), s0))
                if (s1 != null) items.add(Item(0, RectF(slotW * 2, 0f, w, h), s1))
            }
            else -> {
                var x = dp(6f)
                for (a in intArrayOf(ACTION_SETTINGS, ACTION_CLIPBOARD, ACTION_THEME)) {
                    items.add(Item(a, RectF(x, 0f, x + btn, h)))
                    x += btn + dp(2f)
                }
                items.add(Item(ACTION_HIDE, RectF(w - dp(6f) - btn, 0f, w - dp(6f), h)))
            }
        }
    }

    override fun onDraw(c: Canvas) {
        rebuildItems()
        val g = host.glass
        iconPaint.color = g.icon
        textPaint.color = g.text
        boldPaint.color = g.text
        divPaint.color = g.divider
        val cy = height / 2f
        val s = dp(21f)
        for (it in items) {
            if (it === pressed || (pressed != null && pressed?.rect == it.rect)) {
                tmp.set(it.rect.left + dp(3f), cy - dp(16f), it.rect.right - dp(3f), cy + dp(16f))
                g.drawGlass(c, tmp, dp(16f), GlassTheme.Fill.PILL, 0f, withShadow = false)
            }
            val cx = it.rect.centerX()
            when (it.action) {
                ACTION_SETTINGS -> Icons.gear(c, cx, cy, s, iconPaint)
                ACTION_CLIPBOARD -> Icons.clipboard(c, cx, cy, s, iconPaint)
                ACTION_THEME -> Icons.theme(c, cx, cy, s, iconPaint, host.themeMode)
                ACTION_HIDE -> Icons.chevronDown(c, cx, cy, s, iconPaint)
                ACTION_CLEAR_CLIPS -> Icons.trash(c, cx, cy, s, iconPaint)
                ACTION_BACK -> {
                    Icons.chevronLeft(c, it.rect.left + dp(16f), cy, s, iconPaint)
                    boldPaint.textAlign = Paint.Align.LEFT
                    c.drawText("Bufer", it.rect.left + dp(30f), cy - (boldPaint.descent() + boldPaint.ascent()) / 2f, boldPaint)
                    boldPaint.textAlign = Paint.Align.CENTER
                }
                0 -> {
                    val paint = if (!it.isTyped && it === items.getOrNull(1)) boldPaint else textPaint
                    val raw = it.text ?: ""
                    val txt = TextUtils.ellipsize(raw, paint, it.rect.width() - dp(12f), TextUtils.TruncateAt.END).toString()
                    paint.color = if (it.isTyped) g.textSecondary else g.text
                    c.drawText(txt, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
                }
            }
        }
        if (showingSuggestions()) {
            val w = width / 3f
            for (i in 1..2) c.drawRect(w * i - dp(0.5f), cy - dp(11f), w * i + dp(0.5f), cy + dp(11f), divPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rebuildItems()
                pressed = items.firstOrNull { it.rect.contains(e.x, e.y) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                pressed = null
                invalidate()
                if (p != null && p.rect.contains(e.x, e.y)) {
                    if (p.action == 0) {
                        val t = if (p.isTyped) (typed ?: "") else (p.text ?: "")
                        host.onSuggestionPicked(t, p.isTyped)
                    } else host.onToolbarAction(p.action)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
            }
        }
        return true
    }
}
