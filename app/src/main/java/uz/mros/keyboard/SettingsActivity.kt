package uz.mros.keyboard

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** iOS Settings uslubidagi sozlamalar oynasi (kodda yasalgan, XML'siz). */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var dark = false
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val accent = 0xFF0A84FF.toInt()
    private val green = 0xFF34C759.toInt()
    private val red = 0xFFFF3B30.toInt()
    private val textColor get() = if (dark) Color.WHITE else 0xFF000000.toInt()
    private val subColor get() = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
    private val cardColor get() = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    private val bgColor get() = if (dark) Color.BLACK else 0xFFF2F2F7.toInt()
    private val sepColor get() = if (dark) 0x5C545458 else 0x4A3C3C43

    // iOS ikonka ranglari
    private val cBlue = 0xFF0A84FF.toInt()
    private val cIndigo = 0xFF5E5CE6.toInt()
    private val cTeal = 0xFF30B0C7.toInt()
    private val cGreen = 0xFF34C759.toInt()
    private val cOrange = 0xFFFF9500.toInt()
    private val cPink = 0xFFFF2D55.toInt()
    private val cRed = 0xFFFF3B30.toInt()
    private val cPurple = 0xFFAF52DE.toInt()
    private val cGray = 0xFF8E8E93.toInt()
    private val cYellow = 0xFFFFCC00.toInt()

    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var step1Btn: TextView
    private lateinit var step2Btn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    // ======================= UI =======================

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgColor)
        scroll.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16f), dp(8f), dp(16f), dp(40f))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val pad = systemPadding(insets)
            v.setPadding(0, pad[0], 0, pad[1])
            insets
        }

        // Katta sarlavha
        col.addView(TextView(this).apply {
            text = "Klaviatura"
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(dp(4f), dp(24f), 0, dp(14f))
        })

        buildStatusCard(col)

        // TILLAR
        val langs = section(col, "TILLAR", "Til almashtirish: boʻsh joy tugmasini bosib turing yoki yuqoridagi 🌐 belgisini bosing.")
        val labels = linkedMapOf(Lang.UZ to "Oʻzbekcha (lotin)", Lang.RU to "Русский / Ўзбекча", Lang.EN to "English")
        val langColors = mapOf(Lang.UZ to cBlue, Lang.RU to cRed, Lang.EN to cIndigo)
        var first = true
        for ((l, label) in labels) {
            if (!first) separator(langs)
            first = false
            val check = TextView(this).apply {
                text = "✓"
                textSize = 19f
                setTextColor(accent)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            check.visibility = if (l in prefs.languages) View.VISIBLE else View.INVISIBLE
            val row = row(langs, tile(langColors.getValue(l)) { c, x, y, s, p ->
                p.textSize = s * 0.62f
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                p.style = Paint.Style.FILL
                c.drawText(l.code.uppercase(), x, y - (p.descent() + p.ascent()) / 2f, p)
            }, label, null, check)
            row.setOnClickListener {
                val list = prefs.languages.toMutableList()
                if (l in list) {
                    if (list.size <= 1) {
                        toast("Kamida bitta til qolishi kerak")
                        return@setOnClickListener
                    }
                    list.remove(l)
                } else list.add(l)
                prefs.languages = Lang.values().filter { it in list }
                check.visibility = if (l in prefs.languages) View.VISIBLE else View.INVISIBLE
            }
        }

        // KO'RINISH
        val look = section(col, "KOʻRINISH", null)
        segmentRow(look, tile(cIndigo) { c, x, y, s, p -> Icons.theme(c, x, y, s, p, 0) }, "Mavzu",
            listOf("Avto", "Yorugʻ", "Qorongʻi"), prefs.theme) { prefs.theme = it }
        separator(look)
        switchRow(look, tile(cTeal) { c, x, y, s, p -> Icons.drop(c, x, y, s, p) }, "Suyuq effektlar",
            "Jele animatsiya, suv toʻlqini, yorugʻlik", prefs.liquidEffects) { prefs.liquidEffects = it }
        separator(look)
        switchRow(look, tile(cOrange) { c, x, y, s, p ->
            p.textSize = s * 0.8f; p.style = Paint.Style.FILL
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.drawText("A", x, y - (p.descent() + p.ascent()) / 2f, p)
        }, "Tugma baloni", "Bosilgan harf kattalashib koʻrinadi", prefs.keyPopup) { prefs.keyPopup = it }
        separator(look)
        switchRow(look, tile(cGray) { c, x, y, s, p ->
            p.textSize = s * 0.52f; p.style = Paint.Style.FILL
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.drawText("123", x, y - (p.descent() + p.ascent()) / 2f, p)
        }, "Raqamlar qatori", null, prefs.numberRow) { prefs.numberRow = it }
        separator(look)
        segmentRow(look, tile(cYellow) { c, x, y, s, p -> Icons.smiley(c, x, y, s, p) }, "Emoji uslubi",
            listOf("3D", "Tizim"), prefs.emojiStyle) { prefs.emojiStyle = it }
        separator(look)
        switchRow(look, tile(cBlue) { c, x, y, s, p -> Icons.keyboard(c, x, y, s, p) }, "Orqa fonni xiralashtirish",
            "Tajriba: Android 12+ va telefon qoʻllasa", prefs.blur) { prefs.blur = it }

        // O'LCHAM
        val size = section(col, "OʻLCHAM VA JOYLASHUV", "Klaviatura telefonning pastki tugmalariga tegib qolsa, “Pastdan masofa”ni oshiring.")
        sliderRow(size, tile(cGreen) { c, x, y, s, p -> Icons.arrowsVertical(c, x, y, s, p) }, "Balandlik",
            80, 140, prefs.heightPercent, "%") { prefs.heightPercent = it }
        separator(size)
        sliderRow(size, tile(cGreen) { c, x, y, s, p -> Icons.chevronDown(c, x, y + s * 0.05f, s, p) }, "Pastdan masofa",
            0, 48, prefs.bottomOffset, " dp") { prefs.bottomOffset = it }

        // YOZISH
        val typing = section(col, "YOZISH", null)
        switchRow(typing, tile(cPurple) { c, x, y, s, p ->
            p.textSize = s * 0.62f; p.style = Paint.Style.FILL
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.drawText("Aa", x, y - (p.descent() + p.ascent()) / 2f, p)
        }, "Avtomatik bosh harf", null, prefs.autoCap) { prefs.autoCap = it }
        separator(typing)
        switchRow(typing, tile(cPurple) { c, x, y, s, p ->
            p.textSize = s; p.style = Paint.Style.FILL
            c.drawText(".", x, y - (p.descent() + p.ascent()) / 2f - s * 0.12f, p)
        }, "Ikki boʻshliq = nuqta", null, prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        separator(typing)
        switchRow(typing, tile(cPurple) { c, x, y, s, p ->
            p.textSize = s * 0.5f; p.style = Paint.Style.FILL
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            c.drawText("abc", x, y - (p.descent() + p.ascent()) / 2f, p)
        }, "Soʻz takliflari", null, prefs.suggestions) { prefs.suggestions = it }
        separator(typing)
        switchRow(typing, tile(cYellow) { c, x, y, s, p -> Icons.star(c, x, y, s, p) }, "Soʻzlarni oʻrganish",
            "Siz yozgan soʻzlar takliflarga qoʻshiladi", prefs.learnWords) { prefs.learnWords = it }
        separator(typing)
        segmentRow(typing, tile(cPurple) { c, x, y, s, p ->
            p.textSize = s * 0.9f; p.style = Paint.Style.FILL
            c.drawText("ʻ", x, y - (p.descent() + p.ascent()) / 2f + s * 0.1f, p)
        }, "Tutuq belgisi", listOf("oʻ gʻ", "o' g'"), prefs.apostropheStyle) { prefs.apostropheStyle = it }

        // SEZGI
        val feel = section(col, "TOVUSH VA TEBRANISH", null)
        switchRow(feel, tile(cRed) { c, x, y, s, p -> Icons.vibrate(c, x, y, s, p) }, "Tebranish", null, prefs.vibrate) { prefs.vibrate = it }
        separator(feel)
        segmentRow(feel, tile(cRed) { c, x, y, s, p -> Icons.vibrate(c, x, y, s * 0.8f, p) }, "Tebranish kuchi",
            listOf("Yengil", "Oʻrta", "Kuchli"), prefs.vibrateStrength) { prefs.vibrateStrength = it }
        separator(feel)
        switchRow(feel, tile(cPink) { c, x, y, s, p -> Icons.speaker(c, x, y, s, p) }, "Tugma ovozi", null, prefs.sound) { prefs.sound = it }

        // SINOV
        val test = section(col, "SINAB KOʻRING", null)
        test.addView(EditText(this).apply {
            hint = "Shu yerga yozib koʻring…"
            setTextColor(textColor)
            setHintTextColor(subColor)
            textSize = 17f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP
            background = null
            setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
        })

        // MA'LUMOTLAR
        val data = section(col, "MAʼLUMOTLAR", null)
        actionRow(data, tile(cRed) { c, x, y, s, p -> Icons.trash(c, x, y, s, p) }, "Oʻrganilgan soʻzlarni tozalash", red) {
            prefs.sp.edit().putLong("learn_reset", System.currentTimeMillis()).apply()
            SuggestionEngine(this).clearLearned()
            toast("Tozalandi")
        }
        separator(data)
        actionRow(data, tile(cRed) { c, x, y, s, p -> Icons.clipboard(c, x, y, s, p) }, "Bufer tarixini tozalash", red) {
            prefs.clips = emptyList()
            toast("Tozalandi")
        }
        separator(data)
        actionRow(data, tile(cRed) { c, x, y, s, p -> Icons.clock(c, x, y, s, p) }, "Soʻnggi emojilarni tozalash", red) {
            prefs.recentEmoji = emptyList()
            toast("Tozalandi")
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        col.addView(TextView(this).apply {
            text = "MROS Klaviatura $version\n3D emoji: Microsoft Fluent Emoji (MIT)"
            textSize = 13f
            setTextColor(subColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(22f), 0, 0)
        })

        setContentView(scroll)
        window.statusBarColor = Color.TRANSPARENT
    }

    private fun buildStatusCard(col: LinearLayout) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (dark) intArrayOf(0xFF1E3A5F.toInt(), 0xFF2A1E5C.toInt()) else intArrayOf(0xFF5AC8FA.toInt(), 0xFF5E5CE6.toInt())
        ).apply { cornerRadius = dp(20f).toFloat() }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(IconTile(this, 0x33FFFFFF) { c, x, y, s, p -> Icons.keyboard(c, x, y, s, p) })
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.setPadding(dp(12f), 0, 0, 0)
        statusTitle = TextView(this).apply {
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        statusSub = TextView(this).apply {
            textSize = 14f
            setTextColor(0xD9FFFFFF.toInt())
        }
        texts.addView(statusTitle)
        texts.addView(statusSub)
        top.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.setPadding(0, dp(16f), 0, 0)
        step1Btn = pillButton("1. Yoqish") {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (_: Exception) {
            }
        }
        step2Btn = pillButton("2. Tanlash") {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        buttons.addView(step1Btn, LinearLayout.LayoutParams(0, dp(44f), 1f).apply { rightMargin = dp(6f) })
        buttons.addView(step2Btn, LinearLayout.LayoutParams(0, dp(44f), 1f).apply { leftMargin = dp(6f) })
        card.addView(buttons)
        col.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun pillButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15.5f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFF0A3D91.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(0xF2FFFFFF.toInt())
        }
        setOnClickListener { onClick() }
    }

    private fun refreshStatus() {
        if (!::statusTitle.isInitialized) return
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        when {
            enabled && selected -> {
                statusTitle.text = "Hammasi tayyor ✓"
                statusSub.text = "MROS Klaviatura ishlayapti"
            }
            enabled -> {
                statusTitle.text = "Deyarli tayyor"
                statusSub.text = "Endi klaviaturani tanlang"
            }
            else -> {
                statusTitle.text = "Oʻrnatish"
                statusSub.text = "Ikki qadam: yoqing va tanlang"
            }
        }
        step1Btn.text = if (enabled) "✓ Yoqilgan" else "1. Yoqish"
        step1Btn.alpha = if (enabled) 0.7f else 1f
        step2Btn.text = if (selected) "✓ Tanlangan" else "2. Tanlash"
        step2Btn.alpha = if (selected) 0.7f else 1f
    }

    // ======================= qatorlar =======================

    @Suppress("DEPRECATION")
    private fun systemPadding(insets: WindowInsets): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            intArrayOf(i.top, i.bottom)
        } else {
            intArrayOf(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
        }
    }

    private fun tile(color: Int, draw: (Canvas, Float, Float, Float, Paint) -> Unit) = IconTile(this, color, draw)

    private fun section(parent: LinearLayout, title: String, footer: String?): LinearLayout {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(subColor)
            setPadding(dp(16f), dp(26f), 0, dp(7f))
        })
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(cardColor)
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (footer != null) {
            parent.addView(TextView(this).apply {
                text = footer
                textSize = 13f
                setTextColor(subColor)
                setPadding(dp(16f), dp(7f), dp(16f), 0)
            })
        }
        return card
    }

    private fun separator(parent: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(sepColor)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(0.5f)))
        lp.leftMargin = dp(58f)
        parent.addView(v, lp)
    }

    /** Umumiy qator: ikonka + sarlavha (+ izoh) + o'ng tomondagi element */
    private fun row(parent: LinearLayout, icon: View, title: String, sub: String?, accessory: View?): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14f), dp(if (sub == null) 11f else 9f), dp(14f), dp(if (sub == null) 11f else 9f))
        row.addView(icon)
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.setPadding(dp(14f), 0, dp(8f), 0)
        texts.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(textColor)
        })
        if (sub != null) texts.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(subColor)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (accessory != null) row.addView(accessory)
        parent.addView(row)
        return row
    }

    private fun switchRow(parent: LinearLayout, icon: View, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = IosSwitch(this, dark)
        sw.setChecked(value, false)
        sw.onChange = onChange
        val r = row(parent, icon, title, sub, sw)
        r.setOnClickListener { sw.toggle() }
    }

    private fun actionRow(parent: LinearLayout, icon: View, title: String, color: Int, onClick: () -> Unit) {
        val r = row(parent, icon, title, null, null)
        val tv = (r.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
        tv.setTextColor(color)
        r.setOnClickListener { onClick() }
    }

    private fun segmentRow(parent: LinearLayout, icon: View, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        row(wrap, icon, title, null, null)
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
        seg.background = GradientDrawable().apply {
            cornerRadius = dp(9f).toFloat()
            setColor(if (dark) 0xFF2C2C2E.toInt() else 0xFFEEEEEF.toInt())
        }
        val buttons = ArrayList<TextView>()
        fun paint(sel: Int) {
            buttons.forEachIndexed { i, b ->
                if (i == sel) {
                    b.background = GradientDrawable().apply {
                        cornerRadius = dp(7f).toFloat()
                        setColor(if (dark) 0xFF636366.toInt() else Color.WHITE)
                        setStroke(maxOf(1, dp(0.5f)), if (dark) 0x33FFFFFF else 0x14000000)
                    }
                    b.setTextColor(textColor)
                    b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                } else {
                    b.background = null
                    b.setTextColor(textColor)
                    b.typeface = Typeface.DEFAULT
                }
            }
        }
        options.forEachIndexed { i, o ->
            val b = TextView(this)
            b.text = o
            b.textSize = 13.5f
            b.gravity = Gravity.CENTER
            b.setPadding(dp(4f), dp(7f), dp(4f), dp(7f))
            b.setOnClickListener {
                onSelect(i)
                paint(i)
                b.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            buttons.add(b)
            seg.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(selected)
        wrap.addView(seg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(58f)
            rightMargin = dp(14f)
            bottomMargin = dp(12f)
        })
        parent.addView(wrap)
    }

    private fun sliderRow(parent: LinearLayout, icon: View, title: String, min: Int, max: Int, value: Int, unit: String, onChange: (Int) -> Unit) {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        val valueText = TextView(this).apply {
            textSize = 17f
            setTextColor(subColor)
        }
        fun setValue(v: Int) {
            valueText.text = "$v$unit"
        }
        setValue(value)
        row(wrap, icon, title, null, valueText)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (value - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(accent)
        sb.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = min + progress
                setValue(v)
                if (fromUser) onChange(v)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        wrap.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(46f)
            rightMargin = dp(6f)
            bottomMargin = dp(12f)
        })
        parent.addView(wrap)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
