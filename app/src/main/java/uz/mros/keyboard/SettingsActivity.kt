package uz.mros.keyboard

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Sozlamalar va o'rnatish yo'riqnomasi (kodda yasalgan UI, XML'siz). */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var dark = false
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private lateinit var step1: TextView
    private lateinit var step2: TextView

    private val accent = 0xFF0A84FF.toInt()
    private val textColor get() = if (dark) Color.WHITE else 0xFF0E0F11.toInt()
    private val subColor get() = if (dark) 0x99FFFFFF.toInt() else 0x8C3C3C43.toInt()
    private val cardColor get() = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    private val bgColor get() = if (dark) Color.BLACK else 0xFFF2F2F7.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshSteps()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgColor)
        scroll.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16f), dp(12f), dp(16f), dp(32f))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val pad = systemPadding(insets)
            v.setPadding(0, pad[0], 0, pad[1])
            insets
        }

        // Sarlavha
        col.addView(TextView(this).apply {
            text = "MROS Klaviatura"
            textSize = 32f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(dp(4f), dp(20f), 0, dp(2f))
        })
        col.addView(TextView(this).apply {
            text = "Liquid Glass uslubidagi klaviatura"
            textSize = 15f
            setTextColor(subColor)
            setPadding(dp(4f), 0, 0, dp(14f))
        })

        // 1. O'rnatish
        val setup = card(col, "OʻRNATISH")
        step1 = actionRow(setup, "1. Klaviaturani yoqish") {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (_: Exception) {
            }
        }
        divider(setup)
        step2 = actionRow(setup, "2. MROS Klaviaturani tanlash") {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }

        // 2. Tillar
        val langs = card(col, "TILLAR")
        val current = prefs.languages.toMutableList()
        val labels = mapOf(Lang.UZ to "Oʻzbekcha (lotin)", Lang.RU to "Русский / Ўзбекча (кирилл)", Lang.EN to "English")
        for (l in Lang.values()) {
            val cb = CheckBox(this)
            cb.text = labels[l]
            cb.textSize = 16f
            cb.setTextColor(textColor)
            cb.buttonTintList = ColorStateList.valueOf(accent)
            cb.isChecked = l in current
            cb.setPadding(dp(6f), dp(10f), 0, dp(10f))
            cb.setOnCheckedChangeListener { btn, checked ->
                val list = prefs.languages.toMutableList()
                if (checked) {
                    if (l !in list) list.add(l)
                } else {
                    if (list.size <= 1) {
                        btn?.isChecked = true
                        toast("Kamida bitta til qolishi kerak")
                        return@setOnCheckedChangeListener
                    }
                    list.remove(l)
                }
                val ordered = Lang.values().filter { it in list }
                prefs.languages = ordered
            }
            langs.addView(cb)
        }
        hint(langs, "Tilni almashtirish: klaviaturadagi 🌐 tugmasi. Bosib tursangiz — boshqa klaviaturalar roʻyxati.")

        // 3. Ko'rinish
        val look = card(col, "KOʻRINISH")
        segmented(look, "Mavzu", listOf("Avto", "Yorugʻ", "Qorongʻi"), prefs.theme) { prefs.theme = it }
        divider(look)
        switchRow(look, "Raqamlar qatori", "Harflar ustida alohida 1–0 qatori", prefs.numberRow) { prefs.numberRow = it }
        divider(look)
        switchRow(look, "Tugma baloni", "Bosilgan harf kattalashib koʻrinadi", prefs.keyPopup) { prefs.keyPopup = it }
        divider(look)
        switchRow(look, "Orqa fonni xiralashtirish (tajriba)", "Android 12+ va telefon qoʻllasa ishlaydi", prefs.blur) { prefs.blur = it }
        divider(look)
        heightRow(look)

        // 4. Yozish
        val typing = card(col, "YOZISH")
        switchRow(typing, "Avtomatik bosh harf", "Gap boshida katta harf", prefs.autoCap) { prefs.autoCap = it }
        divider(typing)
        switchRow(typing, "Ikki boʻshliq = nuqta", "Boʻshliqni ikki marta bossangiz “. ” qoʻyiladi", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        divider(typing)
        switchRow(typing, "Soʻz takliflari", "Yuqori qatorda soʻzlar chiqadi", prefs.suggestions) { prefs.suggestions = it }
        divider(typing)
        switchRow(typing, "Soʻzlarni oʻrganish", "Siz yozgan soʻzlar takliflarga qoʻshiladi", prefs.learnWords) { prefs.learnWords = it }
        divider(typing)
        segmented(typing, "Tutuq belgisi", listOf("oʻ gʻ (rasmiy)", "o' g' (oddiy)"), prefs.apostropheStyle) { prefs.apostropheStyle = it }

        // 5. Sezgi
        val feel = card(col, "SEZGI")
        switchRow(feel, "Tebranish", null, prefs.vibrate) { prefs.vibrate = it }
        divider(feel)
        segmented(feel, "Tebranish kuchi", listOf("Yengil", "Oʻrta", "Kuchli"), prefs.vibrateStrength) { prefs.vibrateStrength = it }
        divider(feel)
        switchRow(feel, "Tugma ovozi", null, prefs.sound) { prefs.sound = it }

        // 6. Sinov
        val test = card(col, "SINAB KOʻRING")
        test.addView(EditText(this).apply {
            hint = "Shu yerga yozib koʻring…"
            setTextColor(textColor)
            setHintTextColor(subColor)
            textSize = 17f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            background = null
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
        })

        // 7. Ma'lumotlar
        val data = card(col, "MAʼLUMOTLAR")
        actionRow(data, "Oʻrganilgan soʻzlarni tozalash") {
            prefs.sp.edit().putLong("learn_reset", System.currentTimeMillis()).apply()
            SuggestionEngine(this).clearLearned()
            toast("Tozalandi")
        }
        divider(data)
        actionRow(data, "Bufer tarixini tozalash") {
            prefs.clips = emptyList()
            toast("Tozalandi")
        }
        divider(data)
        actionRow(data, "Soʻnggi emojilarni tozalash") {
            prefs.recentEmoji = emptyList()
            toast("Tozalandi")
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        col.addView(TextView(this).apply {
            text = "Versiya $version"
            textSize = 13f
            setTextColor(subColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(18f), 0, 0)
        })

        setContentView(scroll)
        window.statusBarColor = Color.TRANSPARENT
    }

    @Suppress("DEPRECATION")
    private fun systemPadding(insets: WindowInsets): IntArray {
        return if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            intArrayOf(i.top, i.bottom)
        } else {
            intArrayOf(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
        }
    }

    // ---------------- yordamchi UI bo'laklari ----------------

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(subColor)
            letterSpacing = 0.04f
            setPadding(dp(14f), dp(18f), 0, dp(6f))
        })
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
        card.background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(cardColor)
            setStroke(dp(1f).coerceAtLeast(1), if (dark) 0x1FFFFFFF else 0x14000000)
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun divider(parent: LinearLayout) {
        val v = View(this)
        v.setBackgroundColor(if (dark) 0x26FFFFFF else 0x1F3C3C43)
        parent.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    private fun hint(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(subColor)
            setPadding(dp(4f), dp(4f), dp(4f), dp(12f))
        })
    }

    private fun actionRow(parent: LinearLayout, label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 17f
        tv.setTextColor(accent)
        tv.setPadding(dp(4f), dp(14f), dp(4f), dp(14f))
        tv.setOnClickListener { onClick() }
        parent.addView(tv)
        return tv
    }

    private fun switchRow(parent: LinearLayout, label: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(4f), dp(10f), 0, dp(10f))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(TextView(this).apply {
            text = label
            textSize = 16.5f
            setTextColor(textColor)
        })
        if (sub != null) texts.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(subColor)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = value
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, if (dark) 0xFFBBBBBB.toInt() else Color.WHITE)
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(0xFF34C759.toInt(), if (dark) 0xFF39393D.toInt() else 0xFFE5E5EA.toInt())
        )
        sw.setOnCheckedChangeListener { _, c -> onChange(c) }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        parent.addView(row)
    }

    private fun segmented(parent: LinearLayout, label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 16.5f
            setTextColor(textColor)
            setPadding(dp(4f), dp(12f), 0, dp(8f))
        })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        row.background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(if (dark) 0xFF2C2C2E.toInt() else 0xFFEEEEF0.toInt())
        }
        val buttons = ArrayList<TextView>()
        fun paint(sel: Int) {
            buttons.forEachIndexed { i, b ->
                if (i == sel) {
                    b.background = GradientDrawable().apply {
                        cornerRadius = dp(9f).toFloat()
                        setColor(if (dark) 0xFF636366.toInt() else Color.WHITE)
                    }
                    b.setTextColor(textColor)
                    b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                } else {
                    b.background = null
                    b.setTextColor(subColor)
                    b.typeface = Typeface.DEFAULT
                }
            }
        }
        options.forEachIndexed { i, o ->
            val b = TextView(this)
            b.text = o
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
            b.setOnClickListener {
                onSelect(i)
                paint(i)
            }
            buttons.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(selected)
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12f)
        })
    }

    private fun heightRow(parent: LinearLayout) {
        val label = TextView(this)
        label.textSize = 16.5f
        label.setTextColor(textColor)
        label.setPadding(dp(4f), dp(12f), 0, dp(4f))
        fun setLabel(v: Int) {
            label.text = "Klaviatura balandligi: $v%"
        }
        setLabel(prefs.heightPercent)
        parent.addView(label)
        val sb = SeekBar(this)
        sb.max = 60 // 80..140
        sb.progress = (prefs.heightPercent - 80).coerceIn(0, 60)
        sb.progressTintList = ColorStateList.valueOf(accent)
        sb.thumbTintList = ColorStateList.valueOf(accent)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = 80 + progress
                setLabel(v)
                if (fromUser) prefs.heightPercent = v
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        parent.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12f)
        })
    }

    private fun refreshSteps() {
        if (!::step1.isInitialized) return
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        step1.text = if (enabled) "✓ 1. Klaviatura yoqilgan" else "1. Klaviaturani yoqish"
        step1.setTextColor(if (enabled) 0xFF34C759.toInt() else accent)
        step2.text = if (selected) "✓ 2. MROS Klaviatura tanlangan" else "2. MROS Klaviaturani tanlash"
        step2.setTextColor(if (selected) 0xFF34C759.toInt() else accent)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
