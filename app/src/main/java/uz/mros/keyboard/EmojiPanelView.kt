package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * iOS uslubidagi emoji panel: tepada qidiruv, o'rtada gorizontal varaqlanadigan to'r,
 * pastda bo'limlar qatori. Teri rangini tanlash uchun emoji ustida bosib turing.
 */
class EmojiPanelView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        val prefs: Prefs
        fun onEmoji(emoji: String)
        fun onEmojiBackspace(repeat: Boolean)
        fun onEmojiAbc()
        fun onEmojiSearch()
        fun onEmojiFeedback()
        fun abcLabel(): String
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var preferredHeight = 0

    private class Section(val title: String, val icon: String, val items: List<EmojiItem?>, val texts: List<String>) {
        var startX = 0f
        var width = 0f
        var columns = 0
    }

    private val sections = ArrayList<Section>()
    private val scroll = DragScroller(context, this, true)

    private val searchRect = RectF()
    private val gridRect = RectF()
    private val barRect = RectF()
    private var rows = 4
    private var cellW = 0f
    private var cellH = 0f
    private val labelH get() = dp(20f)

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val barIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1.6f) }
    private var barIconSize = dp(20f)
    private var emojiSize = dp(30f)
    private val use3d get() = host.prefs.emojiStyle == 0
    private val onImages: () -> Unit = { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EmojiImages.addListener(onImages)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.04f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(15.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1.7f) }
    private val tmp = RectF()

    // pastki qator tugmalari: -1 = ABC, -2 = o'chirish, 0..n = bo'lim indekslari
    private class BarButton(val id: Int, val rect: RectF)

    private val barButtons = ArrayList<BarButton>()

    fun rebuild() {
        sections.clear()
        val p = host.prefs
        val recents = p.recentEmoji
        if (recents.isNotEmpty()) {
            sections.add(Section(EmojiData.RECENT_TITLE, EmojiData.RECENT_ICON, recents.map { EmojiData.itemFor(it) }, recents))
        }
        if (EmojiData.ready) {
            EmojiData.categories.forEachIndexed { i, list ->
                if (list.isNotEmpty()) {
                    val texts = list.map { if (it.hasTones) it.variant(p.toneFor(it.base)) else it.base }
                    sections.add(Section(EmojiData.CATEGORY_TITLES[i], EmojiData.CATEGORY_ICONS[i], list, texts))
                }
            }
        }
        layoutSections()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, if (preferredHeight > 0) preferredHeight else dp(260f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutSections()
    }

    private fun layoutSections() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val searchH = dp(46f)
        val barH = dp(44f)
        searchRect.set(dp(8f), dp(7f), w - dp(8f), searchH - dp(5f))
        barRect.set(0f, h - barH, w, h)
        gridRect.set(0f, searchH, w, h - barH)
        val gridH = gridRect.height() - labelH
        rows = floor(gridH / dp(38f)).toInt().coerceIn(3, 5)
        cellH = gridH / rows
        cellW = min(cellH * 1.12f, w / 7.6f).coerceAtLeast(cellH * 0.9f)
        emojiPaint.textSize = min(cellH, cellW) * 0.74f
        emojiSize = min(cellH, cellW) * 0.84f

        var x = dp(6f)
        for (s in sections) {
            s.columns = max(1, ceil(s.items.size / rows.toFloat()).toInt())
            s.startX = x
            s.width = s.columns * cellW
            x += s.width + dp(14f)
        }
        scroll.max = x - w

        // pastki qator
        barButtons.clear()
        val side = dp(50f)
        barButtons.add(BarButton(-1, RectF(dp(2f), barRect.top, dp(2f) + side, barRect.bottom)))
        barButtons.add(BarButton(-2, RectF(w - dp(2f) - side, barRect.top, w - dp(2f), barRect.bottom)))
        val n = 1 + EmojiData.CATEGORY_ICONS.size
        val avail = w - 2 * (side + dp(2f))
        val bw = avail / n
        for (i in 0 until n) {
            val l = side + dp(2f) + i * bw
            barButtons.add(BarButton(i, RectF(l, barRect.top, l + bw, barRect.bottom)))
        }
        barIconSize = min(bw * 0.6f, dp(21f))
    }

    /** Joriy ko'rinayotgan bo'lim (pastki qatordagi tanlangan ikonka uchun) */
    private fun currentBarIndex(): Int {
        if (sections.isEmpty()) return 1
        val probe = scroll.pos + width * 0.25f
        var idx = 0
        for ((i, s) in sections.withIndex()) if (s.startX <= probe) idx = i
        val s = sections[idx]
        if (s.title == EmojiData.RECENT_TITLE) return 0
        val cat = EmojiData.CATEGORY_TITLES.indexOf(s.title)
        return if (cat >= 0) cat + 1 else 1
    }

    private fun scrollToBar(i: Int) {
        val target = if (i == 0) {
            sections.firstOrNull { it.title == EmojiData.RECENT_TITLE } ?: sections.firstOrNull()
        } else {
            val title = EmojiData.CATEGORY_TITLES.getOrNull(i - 1)
            sections.firstOrNull { it.title == title }
        }
        if (target != null) scroll.scrollTo(target.startX - dp(6f), true)
    }

    override fun onDraw(c: Canvas) {
        val g = host.glass
        val animating = scroll.update()
        val sx = scroll.pos
        val w = width.toFloat()

        // qidiruv maydoni
        g.drawGlass(c, searchRect, searchRect.height() / 2f, GlassTheme.Fill.PILL, 0f, withShadow = false)
        strokePaint.color = g.textSecondary
        Icons.search(c, searchRect.left + dp(18f), searchRect.centerY(), dp(17f), strokePaint)
        titlePaint.color = g.textSecondary
        titlePaint.textSize = dp(15f)
        titlePaint.letterSpacing = 0f
        c.drawText("Emoji qidirish", searchRect.left + dp(34f), searchRect.centerY() + dp(5f), titlePaint)

        if (!EmojiData.ready && sections.isEmpty()) {
            textPaint.color = g.textSecondary
            c.drawText("Yuklanmoqda…", w / 2f, gridRect.centerY(), textPaint)
        }

        // to'r
        c.save()
        c.clipRect(gridRect)
        titlePaint.textSize = dp(11.5f)
        titlePaint.letterSpacing = 0.04f
        val gridTop = gridRect.top + labelH
        for (s in sections) {
            val left = s.startX - sx
            if (left > w || left + s.width < 0f) continue
            // yopishqoq sarlavha
            val tw = titlePaint.measureText(s.title)
            val lx = max(dp(10f), left + dp(4f)).coerceAtMost(max(left + dp(4f), left + s.width - tw - dp(4f)))
            titlePaint.color = g.textSecondary
            c.drawText(s.title, lx, gridRect.top + dp(14f), titlePaint)
            val c0 = max(0, floor((sx - s.startX) / cellW).toInt())
            val c1 = min(s.columns - 1, ceil((sx + w - s.startX) / cellW).toInt())
            for (col in c0..c1) {
                for (r in 0 until rows) {
                    val idx = col * rows + r
                    if (idx >= s.texts.size) break
                    val cx = left + col * cellW + cellW / 2f
                    val cy = gridTop + r * cellH + cellH / 2f
                    if (pressedSection === s && pressedIndex == idx && !scroll.dragging) {
                        tmp.set(cx - cellW / 2f + dp(1f), cy - cellH / 2f + dp(1f), cx + cellW / 2f - dp(1f), cy + cellH / 2f - dp(1f))
                        g.drawGlass(c, tmp, dp(10f), GlassTheme.Fill.PILL, 0f, withShadow = false)
                    }
                    EmojiImages.draw(c, s.texts[idx], cx, cy, emojiSize, emojiPaint, use3d)
                }
            }
        }
        c.restore()

        // pastki qator
        val sel = currentBarIndex()
        for (b in barButtons) {
            val cx = b.rect.centerX()
            val cy = b.rect.centerY()
            when (b.id) {
                -1 -> {
                    textPaint.color = g.text
                    textPaint.textSize = dp(15f)
                    c.drawText(host.abcLabel(), cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
                }
                -2 -> {
                    strokePaint.color = g.text
                    Icons.backspace(c, cx, cy, dp(22f), strokePaint)
                }
                else -> {
                    if (b.id == sel) {
                        val r = min(b.rect.width(), b.rect.height()) * 0.42f
                        tmp.set(cx - r, cy - r, cx + r, cy + r)
                        g.drawGlass(c, tmp, r, GlassTheme.Fill.CHAR, 0f, withShadow = false)
                    }
                    barIconPaint.color = if (b.id == sel) g.text else g.textSecondary
                    val sz = barIconSize
                    when (b.id) {
                        0 -> Icons.clock(c, cx, cy, sz, barIconPaint)
                        1 -> Icons.smiley(c, cx, cy, sz, barIconPaint)
                        2 -> Icons.bear(c, cx, cy, sz, barIconPaint)
                        3 -> Icons.food(c, cx, cy, sz, barIconPaint)
                        4 -> Icons.ball(c, cx, cy, sz, barIconPaint)
                        5 -> Icons.car(c, cx, cy, sz, barIconPaint)
                        6 -> Icons.bulb(c, cx, cy, sz, barIconPaint)
                        7 -> Icons.symbols(c, cx, cy, sz, barIconPaint)
                        else -> Icons.flag(c, cx, cy, sz, barIconPaint)
                    }
                }
            }
        }

        drawTonePopup(c, g)
        if (animating) postInvalidateOnAnimation()
    }

    // ---- teri rangi popup ----
    private var toneItem: EmojiItem? = null
    private val toneRect = RectF()
    private var toneSel = 0
    private var toneCellW = 0f

    private fun openTone(item: EmojiItem, cx: Float, cy: Float) {
        toneItem = item
        toneCellW = max(cellW, dp(40f))
        val tw = toneCellW * 6
        val th = cellH * 1.2f
        var l = cx - tw / 2f
        l = l.coerceIn(dp(4f), max(dp(4f), width - dp(4f) - tw))
        val t = max(dp(2f), cy - cellH / 2f - th - dp(4f))
        toneRect.set(l, t, l + tw, t + th)
        toneSel = host.prefs.toneFor(item.base).coerceIn(0, 5)
        host.onEmojiFeedback()
        invalidate()
    }

    private fun drawTonePopup(c: Canvas, g: GlassTheme) {
        val item = toneItem ?: return
        g.drawGlass(c, toneRect, dp(14f), GlassTheme.Fill.BUBBLE)
        for (i in 0..5) {
            val l = toneRect.left + i * toneCellW
            tmp.set(l + dp(3f), toneRect.top + dp(4f), l + toneCellW - dp(3f), toneRect.bottom - dp(4f))
            if (i == toneSel) g.fillRound(c, tmp, dp(10f), GlassTheme.scaleAlpha(g.accent, 0.35f))
            EmojiImages.draw(c, item.variant(i), tmp.centerX(), tmp.centerY(), emojiSize, emojiPaint, use3d)
        }
    }

    // ---- touch ----
    private enum class Zone { NONE, SEARCH, GRID, BAR }

    private var zone = Zone.NONE
    private var pressedSection: Section? = null
    private var pressedIndex = -1
    private var pressedCx = 0f
    private var pressedCy = 0f
    private var barDown: BarButton? = null
    private var repeatCount = 0

    private val longPress = Runnable {
        val s = pressedSection
        if (s != null && pressedIndex >= 0 && !scroll.dragging) {
            val item = s.items.getOrNull(pressedIndex)
            if (item != null && item.hasTones) openTone(item, pressedCx, pressedCy)
        }
    }

    private val deleteRepeat: Runnable = object : Runnable {
        override fun run() {
            if (barDown?.id != -2) return
            repeatCount++
            host.onEmojiBackspace(true)
            postDelayed(this, if (repeatCount > 12) 35L else 70L)
        }
    }

    private fun hitCell(x: Float, y: Float): Boolean {
        pressedSection = null
        pressedIndex = -1
        val gridTop = gridRect.top + labelH
        if (y < gridTop || y > gridRect.bottom) return false
        val ax = x + scroll.pos
        for (s in sections) {
            if (ax >= s.startX && ax < s.startX + s.width) {
                val col = ((ax - s.startX) / cellW).toInt()
                val row = ((y - gridTop) / cellH).toInt().coerceIn(0, rows - 1)
                val idx = col * rows + row
                if (idx < s.texts.size) {
                    pressedSection = s
                    pressedIndex = idx
                    pressedCx = s.startX - scroll.pos + col * cellW + cellW / 2f
                    pressedCy = gridTop + row * cellH + cellH / 2f
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zone = when {
                    y < gridRect.top -> Zone.SEARCH
                    y >= barRect.top -> Zone.BAR
                    else -> Zone.GRID
                }
                when (zone) {
                    Zone.GRID -> {
                        scroll.onTouch(e)
                        if (!scroll.stoppedFling && hitCell(x, y)) postDelayed(longPress, 420L)
                        invalidate()
                    }
                    Zone.BAR -> {
                        barDown = barButtons.firstOrNull { it.rect.contains(x, y) }
                        host.onEmojiFeedback()
                        if (barDown?.id == -2) {
                            repeatCount = 0
                            host.onEmojiBackspace(false)
                            postDelayed(deleteRepeat, 420L)
                        }
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (toneItem != null) {
                    val i = ((x - toneRect.left) / toneCellW).toInt().coerceIn(0, 5)
                    if (i != toneSel) {
                        toneSel = i
                        invalidate()
                    }
                } else if (zone == Zone.GRID) {
                    scroll.onTouch(e)
                    if (scroll.dragging) {
                        removeCallbacks(longPress)
                        pressedSection = null
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                removeCallbacks(deleteRepeat)
                val ti = toneItem
                if (ti != null) {
                    toneItem = null
                    host.prefs.setTone(ti.base, toneSel)
                    val v = ti.variant(toneSel)
                    // shu bo'limdagi ko'rinishni yangilaymiz
                    val s = pressedSection
                    if (s != null && pressedIndex >= 0 && s.items.getOrNull(pressedIndex) === ti && s.title != EmojiData.RECENT_TITLE) {
                        (s.texts as? MutableList<String>)?.set(pressedIndex, v)
                    }
                    host.onEmoji(v)
                } else when (zone) {
                    Zone.GRID -> {
                        val wasDrag = scroll.dragging
                        scroll.onTouch(e)
                        val s = pressedSection
                        if (!wasDrag && !scroll.stoppedFling && s != null && pressedIndex >= 0) {
                            host.onEmojiFeedback()
                            host.onEmoji(s.texts[pressedIndex])
                        }
                    }
                    Zone.BAR -> {
                        val b = barDown
                        if (b != null && b.rect.contains(x, y)) {
                            when (b.id) {
                                -1 -> host.onEmojiAbc()
                                -2 -> {}
                                else -> scrollToBar(b.id)
                            }
                        }
                    }
                    Zone.SEARCH -> if (searchRect.contains(x, y) || y < gridRect.top) host.onEmojiSearch()
                    Zone.NONE -> {}
                }
                barDown = null
                pressedSection = null
                pressedIndex = -1
                zone = Zone.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                removeCallbacks(deleteRepeat)
                scroll.onTouch(e)
                toneItem = null
                barDown = null
                pressedSection = null
                zone = Zone.NONE
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        EmojiImages.removeListener(onImages)
        removeCallbacks(longPress)
        removeCallbacks(deleteRepeat)
        super.onDetachedFromWindow()
    }
}
