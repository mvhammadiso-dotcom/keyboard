package uz.mros.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class EnterAction { NEWLINE, SEARCH, SEND, GO, NEXT, DONE, PREVIOUS }

/**
 * Tugmalarni chizadigan va bosishlarni (multi-touch) qayta ishlaydigan asosiy view.
 * Balon (preview) va qo'shimcha harflar popup'i root view ustida chiziladi: drawOverlay().
 */
class KeyboardView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        fun onKeyFeedback(key: Key)
        fun onChar(text: String, key: Key)
        fun onFunctionKey(key: Key)
        fun onBackspace(repeat: Boolean)
        fun onCursorMove(dir: Int)
        fun onKeyLongPress(key: Key)
        fun requestOverlayRedraw()
        /** Klaviatura ustida qancha joy bor (root ichida) */
        fun overlaySpaceAbove(): Float
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var layout: KeyboardLayout? = null
        set(v) {
            cancelAll()
            anims.clear()
            field = v
            computeRects()
            invalidate()
        }

    /** 0 o'chiq, 1 bir martalik, 2 CAPS */
    var shiftState = 0
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var enterAction = EnterAction.NEWLINE
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var spaceLabel = ""
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    var showPreviews = true
    var preferredHeight = 0

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1.7f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var keyRadius = dp(9f)
    private var letterSize = dp(23f)
    private var labelSize = dp(15f)
    private var hintSize = dp(10f)
    private var iconSize = dp(22f)

    // ---- bosish animatsiyasi ----
    private class Anim(var down: Boolean, var t: Long, var from: Float)

    private val anims = HashMap<Key, Anim>()

    private fun pressAmount(k: Key, now: Long): Float {
        val a = anims[k] ?: return 0f
        return if (a.down) {
            min(1f, a.from + (now - a.t) / 60f)
        } else {
            val v = a.from * (1f - (now - a.t) / 180f)
            if (v <= 0f) {
                anims.remove(k)
                0f
            } else v
        }
    }

    private fun pressVisual(k: Key) {
        val now = SystemClock.uptimeMillis()
        anims[k] = Anim(true, now, pressAmount(k, now))
        invalidate()
    }

    private fun releaseVisual(k: Key?) {
        if (k == null) return
        val now = SystemClock.uptimeMillis()
        val cur = pressAmount(k, now)
        anims[k] = Anim(false, now, max(cur, 0.6f))
        invalidate()
    }

    // ---- o'lchamlar ----
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (preferredHeight > 0) preferredHeight else dp(216f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        val L = layout ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val padX = dp(3f)
        val padTop = dp(4f)
        val padBottom = dp(4f)
        val rowH = (h - padTop - padBottom) / L.rows.size
        val normalRows = L.rows.filter { !it.fill }
        val maxUnits = max(1f, (if (normalRows.isEmpty()) L.rows else normalRows).maxOf { it.units })
        val avail = w - 2 * padX
        val unitW = avail / maxUnits
        val gapH = min(dp(6f), unitW * 0.15f)
        val gapV = rowH * (if (L.kind == LayoutKind.NUMPAD) 0.14f else 0.2f)
        L.rows.forEachIndexed { ri, row ->
            val top = padTop + ri * rowH
            val total = row.units
            val uw = if (row.fill) avail / total else unitW
            var x = if (row.fill) padX else padX + (avail - total * uw) / 2f
            for (k in row.keys) {
                val kw = k.width * uw
                k.rect.set(x + gapH / 2f, top + gapV / 2f, x + kw - gapH / 2f, top + rowH - gapV / 2f)
                x += kw
            }
        }
        val keyH = rowH - gapV
        keyRadius = min(dp(10f), keyH * 0.24f)
        letterSize = min(dp(25f), keyH * (if (L.kind == LayoutKind.NUMPAD) 0.48f else 0.52f))
        labelSize = min(dp(15.5f), keyH * 0.34f)
        hintSize = min(dp(10.5f), keyH * 0.23f)
        iconSize = min(dp(24f), keyH * 0.5f)
    }

    // ---- chizish ----
    override fun onDraw(c: Canvas) {
        val L = layout ?: return
        val g = host.glass
        val now = SystemClock.uptimeMillis()
        for (k in L.allKeys) {
            if (k.type == KeyType.SPACER) continue
            val pr = pressAmount(k, now)
            val previewed = showPreviews && k === previewKey
            val scale = if (pr > 0f && !previewed) 1f + 0.055f * pr else 1f
            if (scale != 1f) {
                c.save()
                c.scale(scale, scale, k.rect.centerX(), k.rect.centerY())
            }
            g.drawGlass(c, k.rect, keyRadius, fillFor(k), pr)
            drawLabel(c, k, g)
            if (scale != 1f) c.restore()
        }
        if (anims.values.any { !it.down || now - it.t < 90L }) postInvalidateOnAnimation()
    }

    private fun isAccentEnter() = enterAction != EnterAction.NEWLINE

    private fun fillFor(k: Key): GlassTheme.Fill = when (k.type) {
        KeyType.CHAR, KeyType.SPACE -> GlassTheme.Fill.CHAR
        KeyType.ENTER -> if (isAccentEnter()) GlassTheme.Fill.ACCENT else GlassTheme.Fill.FUNC
        KeyType.SHIFT -> if (shiftState > 0) GlassTheme.Fill.CHAR else GlassTheme.Fill.FUNC
        else -> GlassTheme.Fill.FUNC
    }

    fun labelFor(k: Key): String = if (k.isLetter && shiftState > 0) k.label.uppercase() else k.label

    fun textFor(k: Key): String = if (k.isLetter && shiftState > 0) k.output.uppercase() else k.output

    private fun centerY(p: Paint, cy: Float) = cy - (p.descent() + p.ascent()) / 2f

    private fun drawLabel(c: Canvas, k: Key, g: GlassTheme) {
        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        when (k.type) {
            KeyType.CHAR -> {
                val t = labelFor(k)
                letterPaint.color = g.text
                letterPaint.textSize = when {
                    t.length >= 3 -> letterSize * 0.62f
                    t.length == 2 && !k.isLetter -> letterSize * 0.8f
                    else -> letterSize
                }
                c.drawText(t, cx, centerY(letterPaint, cy - dp(0.5f)), letterPaint)
                val hint = k.hint
                if (hint != null) {
                    hintPaint.color = g.textSecondary
                    hintPaint.textSize = hintSize
                    c.drawText(hint, k.rect.right - hintSize * 0.75f, k.rect.top + hintSize * 1.05f, hintPaint)
                }
            }
            KeyType.SPACE -> {
                labelPaint.color = g.textSecondary
                labelPaint.textSize = labelSize * 0.9f
                if (spaceLabel.isNotEmpty()) c.drawText(spaceLabel, cx, centerY(labelPaint, cy), labelPaint)
            }
            KeyType.SHIFT -> {
                iconPaint.color = g.text
                Icons.shift(c, cx, cy, iconSize * 0.92f, iconPaint, shiftState)
            }
            KeyType.BACKSPACE -> {
                iconPaint.color = g.text
                Icons.backspace(c, cx, cy, iconSize, iconPaint)
            }
            KeyType.ENTER -> {
                iconPaint.color = if (isAccentEnter()) g.textAccent else g.text
                val s = iconSize * 0.9f
                when (enterAction) {
                    EnterAction.SEARCH -> Icons.search(c, cx, cy, s, iconPaint)
                    EnterAction.SEND -> Icons.arrowUp(c, cx, cy, s, iconPaint)
                    EnterAction.GO, EnterAction.NEXT -> Icons.arrowRight(c, cx, cy, s, iconPaint)
                    EnterAction.DONE -> Icons.check(c, cx, cy, s, iconPaint)
                    EnterAction.PREVIOUS -> {
                        c.save(); c.scale(-1f, 1f, cx, cy)
                        Icons.arrowRight(c, cx, cy, s, iconPaint)
                        c.restore()
                    }
                    EnterAction.NEWLINE -> Icons.enter(c, cx, cy, s, iconPaint)
                }
            }
            KeyType.SYMBOLS, KeyType.SYMBOLS2, KeyType.LETTERS -> {
                labelPaint.color = g.text
                labelPaint.textSize = labelSize
                c.drawText(k.label, cx, centerY(labelPaint, cy), labelPaint)
            }
            KeyType.EMOJI -> {
                iconPaint.color = g.text
                Icons.smiley(c, cx, cy, iconSize, iconPaint)
            }
            KeyType.GLOBE -> {
                iconPaint.color = g.text
                Icons.globe(c, cx, cy, iconSize, iconPaint)
            }
            KeyType.SPACER -> {}
        }
    }

    // ---- balon va popup (root ustida) ----
    private var previewKey: Key? = null
    private var previewText = ""

    private class AltPopup(val key: Key, val items: List<String>, val rect: RectF, val cellW: Float, val reversed: Boolean) {
        var selected = 0
    }

    private var popup: AltPopup? = null
    private val bubblePath = Path()
    private val bubbleBounds = RectF()
    private val tmpRect = RectF()

    private fun showPreview(k: Key) {
        if (!showPreviews || layout?.kind == LayoutKind.NUMPAD) return
        if (k.type != KeyType.CHAR) return
        previewKey = k
        previewText = labelFor(k)
        host.requestOverlayRedraw()
        invalidate()
    }

    private fun hidePreview(k: Key? = null) {
        if (previewKey == null) return
        if (k != null && previewKey !== k) return
        previewKey = null
        host.requestOverlayRedraw()
        invalidate()
    }

    fun drawOverlay(c: Canvas) {
        val g = host.glass
        val pk = previewKey
        if (pk != null && popup == null) drawBubble(c, pk, g)
        val pp = popup
        if (pp != null) drawPopup(c, pp, g)
    }

    private fun drawBubble(c: Canvas, k: Key, g: GlassTheme) {
        val r = k.rect
        val ext = min(r.width() * 0.3f, dp(11f))
        val bw = r.width() + 2 * ext
        val bh = r.height() * 1.08f
        val limitTop = -host.overlaySpaceAbove() + dp(2f)
        var bl = r.centerX() - bw / 2f
        bl = bl.coerceIn(dp(1f), max(dp(1f), width - dp(1f) - bw))
        val y0 = r.top - dp(5f)
        val bt = max(limitTop, y0 - bh)
        val br = min(dp(12f), bw * 0.28f)
        val kr = keyRadius
        val y1 = r.top + dp(8f)
        val midY = (y0 + y1) / 2f
        val bRight = bl + bw
        bubblePath.reset()
        bubblePath.moveTo(r.left + kr, r.bottom)
        bubblePath.quadTo(r.left, r.bottom, r.left, r.bottom - kr)
        bubblePath.lineTo(r.left, y1)
        bubblePath.cubicTo(r.left, midY, bl, midY, bl, y0)
        bubblePath.lineTo(bl, bt + br)
        bubblePath.quadTo(bl, bt, bl + br, bt)
        bubblePath.lineTo(bRight - br, bt)
        bubblePath.quadTo(bRight, bt, bRight, bt + br)
        bubblePath.lineTo(bRight, y0)
        bubblePath.cubicTo(bRight, midY, r.right, midY, r.right, y1)
        bubblePath.lineTo(r.right, r.bottom - kr)
        bubblePath.quadTo(r.right, r.bottom, r.right - kr, r.bottom)
        bubblePath.close()
        bubbleBounds.set(bl, bt, bRight, r.bottom)
        g.drawGlassPath(c, bubblePath, bubbleBounds, GlassTheme.Fill.BUBBLE)
        bubblePaint.color = g.text
        bubblePaint.textSize = letterSize * (if (previewText.length > 2) 0.9f else 1.45f)
        c.drawText(previewText, bl + bw / 2f, centerY(bubblePaint, bt + (y0 - bt) / 2f), bubblePaint)
    }

    private fun drawPopup(c: Canvas, p: AltPopup, g: GlassTheme) {
        g.drawGlass(c, p.rect, keyRadius * 1.2f, GlassTheme.Fill.BUBBLE)
        bubblePaint.textSize = letterSize * 0.95f
        for (i in p.items.indices) {
            val slot = if (p.reversed) p.items.size - 1 - i else i
            val left = p.rect.left + slot * p.cellW
            tmpRect.set(left + dp(2f), p.rect.top + dp(3f), left + p.cellW - dp(2f), p.rect.bottom - dp(3f))
            if (i == p.selected) g.fillRound(c, tmpRect, keyRadius, g.accent)
            bubblePaint.color = if (i == p.selected) g.textAccent else g.text
            val t = p.items[i]
            bubblePaint.textSize = if (t.length > 2) letterSize * 0.6f else letterSize * 0.95f
            c.drawText(t, tmpRect.centerX(), centerY(bubblePaint, tmpRect.centerY()), bubblePaint)
        }
    }

    private fun alternatesFor(k: Key): List<String> {
        if (k.alternates.isEmpty()) return emptyList()
        return if (k.isLetter && shiftState > 0) k.alternates.map { it.uppercase() } else k.alternates
    }

    private fun openPopup(k: Key, items: List<String>) {
        val n = items.size
        var cellW = max(k.rect.width(), dp(34f))
        val maxW = width - dp(4f)
        if (cellW * n > maxW) cellW = maxW / n
        val totalW = cellW * n
        val growLeft = k.rect.centerX() > width / 2f
        var left = if (growLeft) k.rect.right - totalW else k.rect.left
        left = left.coerceIn(dp(2f), max(dp(2f), width - dp(2f) - totalW))
        val ph = k.rect.height() * 1.05f
        val limitTop = -host.overlaySpaceAbove() + dp(2f)
        val top = max(limitTop, k.rect.top - ph - dp(6f))
        val p = AltPopup(k, items, RectF(left, top, left + totalW, top + ph), cellW, growLeft)
        p.selected = 0
        popup = p
        previewKey = null
        host.requestOverlayRedraw()
        invalidate()
    }

    private fun updatePopup(x: Float) {
        val p = popup ?: return
        var slot = ((x - p.rect.left) / p.cellW).toInt()
        slot = slot.coerceIn(0, p.items.size - 1)
        val idx = if (p.reversed) p.items.size - 1 - slot else slot
        if (idx != p.selected) {
            p.selected = idx
            host.requestOverlayRedraw()
        }
    }

    private fun closePopup(): String? {
        val p = popup ?: return null
        popup = null
        host.requestOverlayRedraw()
        return p.items.getOrNull(p.selected)
    }

    // ---- touch ----
    private inner class Ptr(val id: Int) {
        var key: Key? = null
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var consumed = false
        var popupActive = false
        var cursorMode = false
        var longPressed = false
        var repeats = 0

        val longPress = Runnable { onLongPress(this) }
        val repeat: Runnable = object : Runnable {
            override fun run() {
                if (ptrs[id] !== this@Ptr || consumed) return
                repeats++
                host.onBackspace(true)
                postDelayed(this, if (repeats > 14) 35L else 70L)
            }
        }
    }

    private val ptrs = HashMap<Int, Ptr>()

    private fun findKey(x: Float, y: Float): Key? {
        val L = layout ?: return null
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in L.allKeys) {
            if (k.type == KeyType.SPACER) continue
            val r = k.rect
            val dx = if (x < r.left) r.left - x else if (x > r.right) x - r.right else 0f
            val dy = if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f
            if (dx == 0f && dy == 0f) return k
            val d = hypot(dx, dy)
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        return if (bestD < dp(30f)) best else null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (layout == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                val x = e.getX(idx)
                val y = e.getY(idx)
                // Tez yozishda avvalgi barmoqning harfini darhol yuboramiz (tartib buzilmasin)
                for (p in ptrs.values) {
                    val k = p.key
                    if (!p.consumed && !p.popupActive && k != null && k.type == KeyType.CHAR) {
                        removeCallbacks(p.longPress)
                        host.onChar(textFor(k), k)
                        p.consumed = true
                        releaseVisual(k)
                        hidePreview(k)
                    }
                }
                val p = Ptr(id)
                p.downX = x
                p.downY = y
                p.lastX = x
                p.key = findKey(x, y)
                ptrs[id] = p
                p.key?.let { onPress(p, it) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = ptrs[e.getPointerId(i)] ?: continue
                    onMove(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                val p = ptrs.remove(e.getPointerId(idx))
                if (p != null) onUp(p)
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun onPress(p: Ptr, k: Key) {
        host.onKeyFeedback(k)
        pressVisual(k)
        when (k.type) {
            KeyType.CHAR -> {
                showPreview(k)
                if (k.alternates.isNotEmpty()) postDelayed(p.longPress, 320L)
            }
            KeyType.BACKSPACE -> {
                host.onBackspace(false)
                postDelayed(p.repeat, 420L)
            }
            KeyType.SPACE, KeyType.GLOBE -> postDelayed(p.longPress, 520L)
            else -> {}
        }
    }

    private fun onMove(p: Ptr, x: Float, y: Float) {
        val k = p.key ?: return
        if (p.consumed) return
        if (p.popupActive) {
            updatePopup(x)
            return
        }
        when (k.type) {
            KeyType.SPACE -> {
                if (!p.cursorMode && abs(x - p.downX) > dp(16f)) {
                    p.cursorMode = true
                    removeCallbacks(p.longPress)
                    p.lastX = x
                }
                if (p.cursorMode) {
                    val step = dp(10f)
                    while (x - p.lastX >= step) {
                        host.onCursorMove(1); p.lastX += step
                    }
                    while (p.lastX - x >= step) {
                        host.onCursorMove(-1); p.lastX -= step
                    }
                }
            }
            KeyType.CHAR -> {
                val r = k.rect
                val margin = dp(4f)
                if (x < r.left - margin || x > r.right + margin || y < r.top - margin * 3 || y > r.bottom + margin * 3) {
                    val nk = findKey(x, y)
                    if (nk != null && nk !== k && nk.type == KeyType.CHAR) {
                        removeCallbacks(p.longPress)
                        releaseVisual(k)
                        p.key = nk
                        pressVisual(nk)
                        showPreview(nk)
                        if (nk.alternates.isNotEmpty()) postDelayed(p.longPress, 320L)
                    }
                }
            }
            else -> {}
        }
    }

    private fun onUp(p: Ptr) {
        removeCallbacks(p.longPress)
        removeCallbacks(p.repeat)
        val k = p.key
        releaseVisual(k)
        if (p.popupActive) {
            p.popupActive = false
            val sel = closePopup()
            if (sel != null && k != null && !p.consumed) host.onChar(sel, k)
            return
        }
        if (k != null) hidePreview(k)
        if (p.consumed || k == null) return
        when (k.type) {
            KeyType.CHAR -> host.onChar(textFor(k), k)
            KeyType.SPACE -> if (!p.cursorMode && !p.longPressed) host.onFunctionKey(k)
            KeyType.GLOBE -> if (!p.longPressed) host.onFunctionKey(k)
            KeyType.BACKSPACE, KeyType.SPACER -> {}
            else -> host.onFunctionKey(k)
        }
    }

    private fun onLongPress(p: Ptr) {
        if (p.consumed || ptrs[p.id] !== p) return
        val k = p.key ?: return
        p.longPressed = true
        when (k.type) {
            KeyType.CHAR -> {
                val alts = alternatesFor(k)
                if (alts.isNotEmpty()) {
                    p.popupActive = true
                    openPopup(k, alts)
                    host.onKeyFeedback(k)
                }
            }
            KeyType.SPACE -> if (!p.cursorMode) {
                p.consumed = true
                host.onKeyLongPress(k)
            }
            KeyType.GLOBE -> {
                p.consumed = true
                host.onKeyLongPress(k)
            }
            else -> {}
        }
    }

    fun cancelAll() {
        for (p in ptrs.values) {
            removeCallbacks(p.longPress)
            removeCallbacks(p.repeat)
            releaseVisual(p.key)
        }
        ptrs.clear()
        if (popup != null) closePopup()
        hidePreview()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }
}
