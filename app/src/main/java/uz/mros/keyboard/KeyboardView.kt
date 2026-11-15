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
import kotlin.math.pow

enum class EnterAction { NEWLINE, SEARCH, SEND, GO, NEXT, DONE, PREVIOUS }

/**
 * Tugmalarni chizadigan va bosishlarni (multi-touch) qayta ishlaydigan asosiy view.
 * Balon (preview) va popup'lar root view ustida chiziladi: drawOverlay().
 * Suyuq effektlar: jele (spring) kattalashish, tugma ichida suv to'lqini, balonning "pop" animatsiyasi.
 */
class KeyboardView(context: Context, private val host: Host) : View(context) {

    interface Host {
        val glass: GlassTheme
        val liquidEffects: Boolean
        fun onKeyFeedback(key: Key)
        fun onChar(text: String, key: Key)
        fun onFunctionKey(key: Key)
        fun onBackspace(repeat: Boolean)
        fun onCursorMove(dir: Int)
        fun onKeyLongPress(key: Key)
        fun onTouchGlow(x: Float, y: Float)
        /** Bo'shliqni bosib turganda chiqadigan tillar ro'yxati (1 ta bo'lsa — bo'sh) */
        fun languageItems(): List<String>
        fun currentLanguageIndex(): Int
        fun onLanguagePicked(index: Int)
        fun requestOverlayRedraw()
        /** Klaviatura ustida qancha joy bor (root ichida) */
        fun overlaySpaceAbove(): Float
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var layout: KeyboardLayout? = null
        set(v) {
            cancelAll()
            springs.clear()
            ripples.clear()
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
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1.7f) }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var keyRadius = dp(9f)
    private var letterSize = dp(23f)
    private var labelSize = dp(15f)
    private var hintSize = dp(10f)
    private var iconSize = dp(22f)

    // ================= animatsiyalar =================

    /** Prujina (spring): jele kabi tebranib joyiga qaytadi */
    private class Spring(var x: Float = 1f, var v: Float = 0f, var target: Float = 1f) {
        fun step(dt: Float, k: Float = 520f, c: Float = 17f) {
            val a = -k * (x - target) - c * v
            v += a * dt
            x += v * dt
        }

        val settled: Boolean get() = abs(x - target) < 0.0015f && abs(v) < 0.02f
    }

    private class Ripple(val key: Key, val x: Float, val y: Float, val start: Long)

    private val springs = HashMap<Key, Spring>()
    private val ripples = ArrayList<Ripple>()
    private val pressedKeys = HashSet<Key>()
    private var lastFrame = 0L
    private val clipPath = Path()

    private fun pressVisual(k: Key, x: Float, y: Float) {
        pressedKeys.add(k)
        if (host.liquidEffects) {
            val s = springs.getOrPut(k) { Spring() }
            val previewed = showPreviews && k.type == KeyType.CHAR && layout?.kind != LayoutKind.NUMPAD
            // keng tugmalar (bo'shliq) kamroq kattalashadi — qo'shnisiga tegmasin
            s.target = if (previewed) 1.0f else 1f + min(0.12f, dp(9f) / max(1f, k.rect.width()))
            if (previewed) s.v -= 1.2f // kichik "cho'kish"
            ripples.add(Ripple(k, x, y, SystemClock.uptimeMillis()))
            if (ripples.size > 12) ripples.removeAt(0)
            host.onTouchGlow(x, y)
        }
        invalidate()
    }

    private fun releaseVisual(k: Key?) {
        if (k == null) return
        pressedKeys.remove(k)
        springs[k]?.let {
            it.target = 1f
            it.v -= 1.5f
        }
        invalidate()
    }

    // ================= o'lchamlar =================

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
        val padTop = dp(5f)
        val padBottom = dp(3f)
        val rowH = (h - padTop - padBottom) / L.rows.size
        val normalRows = L.rows.filter { !it.fill }
        val maxUnits = max(1f, (if (normalRows.isEmpty()) L.rows else normalRows).maxOf { it.units })
        val avail = w - 2 * padX
        val unitW = avail / maxUnits
        val gapH = min(dp(6f), unitW * 0.16f)
        val gapV = rowH * (if (L.kind == LayoutKind.NUMPAD) 0.14f else 0.22f)
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
        keyRadius = min(dp(11f), keyH * 0.27f)
        letterSize = min(dp(25f), keyH * (if (L.kind == LayoutKind.NUMPAD) 0.48f else 0.54f))
        labelSize = min(dp(15.5f), keyH * 0.35f)
        hintSize = min(dp(10.5f), keyH * 0.23f)
        iconSize = min(dp(24f), keyH * 0.52f)
    }

    // ================= chizish =================

    override fun onDraw(c: Canvas) {
        val L = layout ?: return
        val g = host.glass
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.034f)
        lastFrame = now
        var animating = false

        val iter = springs.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.value.step(dt)
            if (e.value.settled) {
                if (e.key !in pressedKeys) iter.remove()
            } else {
                animating = true
            }
        }
        ripples.removeAll { now - it.start > RIPPLE_MS }
        if (ripples.isNotEmpty()) animating = true

        for (k in L.allKeys) {
            if (k.type == KeyType.SPACER) continue
            val pressed = if (k in pressedKeys) 1f else 0f
            val s = springs[k]?.x ?: 1f
            val scaled = abs(s - 1f) > 0.001f
            if (scaled) {
                c.save()
                // jele: eniga ko'proq, bo'yiga kamroq cho'ziladi
                c.scale(s, 1f + (s - 1f) * 0.55f, k.rect.centerX(), k.rect.centerY())
            }
            g.drawGlass(c, k.rect, keyRadius, fillFor(k), pressed)
            for (r in ripples) {
                if (r.key !== k) continue
                val t = (now - r.start) / RIPPLE_MS.toFloat()
                clipPath.reset()
                clipPath.addRoundRect(k.rect, keyRadius, keyRadius, Path.Direction.CW)
                drawWaterDrop(c, g, clipPath, r.x, r.y, k.rect.height(), t)
            }
            // balon ochiq bo'lsa, tugma ostidagi harfni chizmaymiz (iOS kabi)
            if (!(k === previewKey && popup == null)) drawLabel(c, k, g)
            if (scaled) c.restore()
        }
        if (animating) {
            postInvalidateOnAnimation()
        } else {
            lastFrame = 0L
        }
    }

    /** Suv tomchisi: yorqin markaz + tarqaluvchi halqa */
    private fun drawWaterDrop(c: Canvas, g: GlassTheme, clip: Path, x: Float, y: Float, h: Float, t: Float, spread: Float = 1.9f) {
        val ease = 1f - (1f - t).pow(2.6f)
        g.drawRipple(c, clip, x, y, h * (0.25f + spread * ease), (1f - t).pow(1.1f))
        g.drawGlowClipped(c, clip, x, y, h * (0.45f + 0.25f * t), (1f - t).pow(2f) * 0.75f)
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
                    c.drawText(hint, k.rect.right - hintSize * 0.8f, k.rect.top + hintSize * 1.1f, hintPaint)
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

    // ================= balon va popup (root ustida) =================
    private var previewKey: Key? = null
    private var previewText = ""
    private val bubbleSpring = Spring(1f, 0f, 1f)
    private var overlayFrame = 0L

    private class AltPopup(
        val key: Key, val items: List<String>, val rect: RectF, val cellW: Float, val reversed: Boolean,
        val isLanguage: Boolean,
    ) {
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
        if (host.liquidEffects) {
            bubbleSpring.x = 0.55f
            bubbleSpring.v = 0f
            bubbleSpring.target = 1f
            overlayFrame = 0L
        } else {
            bubbleSpring.x = 1f
            bubbleSpring.v = 0f
        }
        host.requestOverlayRedraw()
    }

    private fun hidePreview(k: Key? = null) {
        if (previewKey == null) return
        if (k != null && previewKey !== k) return
        previewKey = null
        host.requestOverlayRedraw()
    }

    fun drawOverlay(c: Canvas) {
        val g = host.glass
        val pk = previewKey
        if (pk != null && popup == null) {
            val now = SystemClock.uptimeMillis()
            val dt = if (overlayFrame == 0L) 0.016f else ((now - overlayFrame) / 1000f).coerceIn(0.001f, 0.034f)
            overlayFrame = now
            bubbleSpring.step(dt, 700f, 22f)
            val s = bubbleSpring.x
            c.save()
            c.scale(s, s, pk.rect.centerX(), pk.rect.bottom)
            drawBubble(c, pk, g)
            c.restore()
            if (!bubbleSpring.settled) host.requestOverlayRedraw() else overlayFrame = 0L
        }
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
        val br = min(dp(13f), bw * 0.3f)
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
        if (host.liquidEffects) {
            val now = SystemClock.uptimeMillis()
            for (rp in ripples) {
                if (rp.key !== k) continue
                val t = ((now - rp.start) / RIPPLE_MS.toFloat()).coerceIn(0f, 1f)
                drawWaterDrop(c, g, bubblePath, bl + bw / 2f, bt + (y0 - bt) * 0.62f, bh, t, 0.75f)
                if (t < 1f) host.requestOverlayRedraw()
            }
        }
        bubblePaint.color = g.text
        bubblePaint.textSize = letterSize * (if (previewText.length > 2) 0.9f else 1.45f)
        c.drawText(previewText, bl + bw / 2f, centerY(bubblePaint, bt + (y0 - bt) / 2f), bubblePaint)
    }

    private fun drawPopup(c: Canvas, p: AltPopup, g: GlassTheme) {
        g.drawGlass(c, p.rect, keyRadius * 1.25f, GlassTheme.Fill.BUBBLE)
        for (i in p.items.indices) {
            val slot = if (p.reversed) p.items.size - 1 - i else i
            val left = p.rect.left + slot * p.cellW
            tmpRect.set(left + dp(3f), p.rect.top + dp(4f), left + p.cellW - dp(3f), p.rect.bottom - dp(4f))
            if (i == p.selected) g.fillRound(c, tmpRect, keyRadius, g.accent)
            bubblePaint.color = if (i == p.selected) g.textAccent else g.text
            val t = p.items[i]
            bubblePaint.textSize = when {
                p.isLanguage -> labelSize
                t.length > 2 -> letterSize * 0.6f
                else -> letterSize * 0.95f
            }
            c.drawText(t, tmpRect.centerX(), centerY(bubblePaint, tmpRect.centerY()), bubblePaint)
        }
    }

    private fun alternatesFor(k: Key): List<String> {
        if (k.alternates.isEmpty()) return emptyList()
        return if (k.isLetter && shiftState > 0) k.alternates.map { it.uppercase() } else k.alternates
    }

    private fun openPopup(k: Key, items: List<String>, isLanguage: Boolean, selected: Int) {
        val n = items.size
        var cellW = max(k.rect.width(), dp(34f))
        if (isLanguage) {
            bubblePaint.textSize = labelSize
            cellW = items.maxOf { bubblePaint.measureText(it) } + dp(26f)
        }
        val maxW = width - dp(4f)
        if (cellW * n > maxW) cellW = maxW / n
        val totalW = cellW * n
        val growLeft = !isLanguage && k.rect.centerX() > width / 2f
        var left = when {
            isLanguage -> k.rect.centerX() - totalW / 2f
            growLeft -> k.rect.right - totalW
            else -> k.rect.left
        }
        left = left.coerceIn(dp(2f), max(dp(2f), width - dp(2f) - totalW))
        val ph = if (isLanguage) dp(46f) else k.rect.height() * 1.05f
        val limitTop = -host.overlaySpaceAbove() + dp(2f)
        val top = max(limitTop, k.rect.top - ph - dp(6f))
        val p = AltPopup(k, items, RectF(left, top, left + totalW, top + ph), cellW, growLeft, isLanguage)
        p.selected = selected.coerceIn(0, n - 1)
        popup = p
        previewKey = null
        host.requestOverlayRedraw()
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

    private fun closePopup(): AltPopup? {
        val p = popup ?: return null
        popup = null
        host.requestOverlayRedraw()
        return p
    }

    // ================= touch =================
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
                // Tez yozishda avvalgi barmoqning tugmasini darhol yuboramiz (tartib buzilmasin)
                for (p in ptrs.values) {
                    val k = p.key ?: continue
                    if (p.consumed || p.popupActive) continue
                    if (k.type == KeyType.CHAR) {
                        removeCallbacks(p.longPress)
                        host.onChar(textFor(k), k)
                        p.consumed = true
                        releaseVisual(k)
                        hidePreview(k)
                    } else if (k.type == KeyType.SPACE && !p.cursorMode) {
                        removeCallbacks(p.longPress)
                        host.onFunctionKey(k)
                        p.consumed = true
                        releaseVisual(k)
                    }
                }
                val p = Ptr(id)
                p.downX = x
                p.downY = y
                p.lastX = x
                p.key = findKey(x, y)
                ptrs[id] = p
                p.key?.let { onPress(p, it, x, y) }
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

    private fun onPress(p: Ptr, k: Key, x: Float, y: Float) {
        host.onKeyFeedback(k)
        pressVisual(k, x, y)
        when (k.type) {
            KeyType.CHAR -> {
                showPreview(k)
                if (k.alternates.isNotEmpty()) postDelayed(p.longPress, LONG_PRESS_MS)
            }
            KeyType.BACKSPACE -> {
                host.onBackspace(false)
                postDelayed(p.repeat, 420L)
            }
            KeyType.SPACE, KeyType.GLOBE -> postDelayed(p.longPress, 450L)
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
                if (!p.cursorMode && abs(x - p.downX) > dp(18f)) {
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
                // barmoq qo'shni tugmaga aniq o'tgandagina almashtiramiz (tasodifiy qimirlash xato harf bermasin)
                val r = k.rect
                val m = dp(10f)
                if (x < r.left - m || x > r.right + m || y < r.top - m * 2 || y > r.bottom + m * 2) {
                    val nk = findKey(x, y)
                    if (nk != null && nk !== k && nk.type == KeyType.CHAR) {
                        removeCallbacks(p.longPress)
                        releaseVisual(k)
                        p.key = nk
                        pressVisual(nk, x, y)
                        showPreview(nk)
                        if (nk.alternates.isNotEmpty()) postDelayed(p.longPress, LONG_PRESS_MS)
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
            val pp = closePopup()
            if (pp != null && k != null && !p.consumed) {
                if (pp.isLanguage) host.onLanguagePicked(pp.selected)
                else pp.items.getOrNull(pp.selected)?.let { host.onChar(it, k) }
            }
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
                    openPopup(k, alts, false, 0)
                    host.onKeyFeedback(k)
                }
            }
            KeyType.SPACE -> if (!p.cursorMode) {
                val langs = host.languageItems()
                if (langs.size > 1) {
                    p.popupActive = true
                    openPopup(k, langs + "⌨", true, host.currentLanguageIndex())
                    host.onKeyFeedback(k)
                } else {
                    p.consumed = true
                    host.onKeyLongPress(k)
                }
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
        pressedKeys.clear()
        if (popup != null) closePopup()
        hidePreview()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val RIPPLE_MS = 620L
        private const val LONG_PRESS_MS = 420L
    }
}
