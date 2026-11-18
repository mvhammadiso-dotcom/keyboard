package uz.mros.keyboard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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

    private var updateSub: TextView? = null
    private var updateTile: SpinTile? = null
    private var fontSub: TextView? = null
    private var emojiSegPaint: ((Int) -> Unit)? = null
    private var checking = false
    private var installing = false
    private var pendingInstall: Updater.Release? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        buildUi()
        UpdateJobService.ensureScheduled(this)
        if (!prefs.sp.getBoolean("notif_asked", false)) {
            prefs.sp.edit().putBoolean("notif_asked", true).apply()
            askNotificationPermission()
        }
        if (!handleOpen(intent)) maybeShowGuide()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        val p = pendingInstall
        if (p != null && packageManager.canRequestPackageInstalls()) startInstall(p)
    }

    /** Bildirishnomadan ochilganda: yangilanish yoki qo'llanma. */
    private fun handleOpen(i: Intent?): Boolean {
        val what = i?.getStringExtra(Notifier.EXTRA_OPEN) ?: return false
        i.removeExtra(Notifier.EXTRA_OPEN)
        when (what) {
            Notifier.OPEN_UPDATE -> checkUpdates(true)
            Notifier.OPEN_GUIDE -> {
                prefs.lastSeenVersion = Updater.currentVersion(this)
                showGuide(true)
            }
            else -> return false
        }
        return true
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

        // YANGILANISHLAR
        val upd = section(col, "YANGILANISHLAR", null)
        val spin = SpinTile(this, cGreen) { c, x, y, s, p -> Icons.sync(c, x, y, s, p) }
        updateTile = spin
        val updRow = row(upd, spin, "Yangilanishlarni tekshirish", updateStatusText(), chevron())
        updateSub = subtitleOf(updRow)
        updRow.setOnClickListener { checkUpdates(true) }
        separator(upd)
        switchRow(upd, tile(cBlue) { c, x, y, s, p -> Icons.sync(c, x, y, s, p) }, "Avtomatik yangilash",
            "Wi-Fi'da yangi versiyani oʻzi yuklab oʻrnatadi", prefs.autoUpdate) {
            prefs.autoUpdate = it
            UpdateJobService.ensureScheduled(this)
        }
        separator(upd)
        switchRow(upd, tile(cRed) { c, x, y, s, p -> Icons.bell(c, x, y, s, p) }, "Yangilanish bildirishnomalari",
            "Yangi versiya chiqsa xabar beradi", prefs.updateNotify) {
            prefs.updateNotify = it
            if (it) askNotificationPermission()
            UpdateJobService.ensureScheduled(this)
        }
        separator(upd)
        row(upd, tile(cOrange) { c, x, y, s, p -> Icons.info(c, x, y, s, p) }, "Qoʻllanma va yangiliklar",
            "Ilovani qanday ishlatish", chevron()).setOnClickListener { showGuide(false) }

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
        emojiSegPaint = segmentRow(look, tile(cYellow) { c, x, y, s, p -> Icons.smiley(c, x, y, s, p) }, "Emoji uslubi",
            listOf("3D", "Tizim", "Shaxsiy"), prefs.emojiStyle, canSelect = { i ->
                if (i == 2 && !CustomFont.exists(this)) {
                    toast("Avval shrift faylini yuklang")
                    pickFont()
                    false
                } else true
            }) { prefs.emojiStyle = it }
        separator(look)
        switchRow(look, tile(cBlue) { c, x, y, s, p -> Icons.keyboard(c, x, y, s, p) }, "Orqa fonni xiralashtirish",
            "Tajriba: Android 12+ va telefon qoʻllasa", prefs.blur) { prefs.blur = it }

        // SHAXSIY SHRIFT
        val fontSec = section(col, "SHAXSIY SHRIFT",
            "Telefoningizdagi .ttf yoki .otf emoji shriftini tanlang — u klaviaturaning emoji panelida ishlatiladi. " +
                "Boshqa ilovalarning matn maydonida emoji oʻsha ilovaning shriftida koʻrinadi.")
        val fontRow = row(fontSec, tile(cIndigo) { c, x, y, s, p -> Icons.font(c, x, y, s, p) }, "TTF fayl yuklash",
            fontStatusText(), chevron())
        fontSub = subtitleOf(fontRow)
        fontRow.setOnClickListener { pickFont() }
        separator(fontSec)
        actionRow(fontSec, tile(cRed) { c, x, y, s, p -> Icons.trash(c, x, y, s, p) }, "Shriftni oʻchirish", red) {
            if (!CustomFont.exists(this)) {
                toast("Shrift yuklanmagan")
                return@actionRow
            }
            CustomFont.remove(this)
            prefs.fontStamp = System.currentTimeMillis()
            if (prefs.emojiStyle == 2) prefs.emojiStyle = 0
            emojiSegPaint?.invoke(prefs.emojiStyle)
            fontSub?.text = fontStatusText()
            toast("Oʻchirildi")
        }

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

        // INFO
        val info = section(col, "INFO", "3D emoji: Microsoft Fluent Emoji (MIT litsenziya)")
        row(info, tile(cBlue) { c, x, y, s, p -> Icons.keyboard(c, x, y, s, p) }, "MROS Keyboard",
            "Versiya: ${Updater.currentVersionName(this)}", null)
        separator(info)
        row(info, tile(cBlue) { c, x, y, s, p -> Icons.plane(c, x - s * 0.04f, y, s, p) }, "Telegram kanal",
            "t.me/mrosapp", chevron()).setOnClickListener { openTelegram("mrosapp") }
        separator(info)
        row(info, tile(cBlue) { c, x, y, s, p -> Icons.plane(c, x - s * 0.04f, y, s, p) }, "Creator: mvhammadiso",
            "t.me/mvhammadiso", chevron()).setOnClickListener { openTelegram("mvhammadiso") }

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

    private fun segmentRow(
        parent: LinearLayout, icon: View, title: String, options: List<String>, selected: Int,
        canSelect: (Int) -> Boolean = { true }, onSelect: (Int) -> Unit,
    ): (Int) -> Unit {
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
                if (!canSelect(i)) return@setOnClickListener
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
        return { sel -> paint(sel) }
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

    private fun chevron(): TextView = TextView(this).apply {
        text = "›"
        textSize = 24f
        setTextColor(if (dark) 0x66EBEBF5 else 0x4D3C3C43)
    }

    private fun subtitleOf(row: LinearLayout): TextView? =
        (row.getChildAt(1) as? LinearLayout)?.getChildAt(1) as? TextView

    /** Telegram ilovasini to'g'ridan-to'g'ri ochadi; Telegram o'rnatilmagan bo'lsa — brauzerda. */
    private fun openTelegram(username: String) {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(app)
            return
        } catch (_: Exception) {
        }
        openLink("https://t.me/$username")
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Havolani ochib boʻlmadi")
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            try {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            } catch (_: Exception) {
            }
        }
    }

    // ======================= shaxsiy shrift =======================

    private fun fontStatusText(): String {
        val f = CustomFont.file(this)
        if (!f.exists()) return "Fayl tanlanmagan"
        val mb = f.length() / (1024.0 * 1024.0)
        return "Yuklangan (%.1f MB)".format(mb) + if (prefs.emojiStyle == 2) " · faol" else ""
    }

    private fun pickFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, REQ_FONT)
        } catch (_: Exception) {
            toast("Fayl tanlash oynasini ochib boʻlmadi")
        }
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FONT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        fontSub?.text = "Yuklanmoqda…"
        Thread {
            val err = CustomFont.import(this, uri)
            runOnUiThread {
                if (err != null) {
                    toast(err)
                } else {
                    prefs.fontStamp = System.currentTimeMillis()
                    prefs.emojiStyle = 2
                    emojiSegPaint?.invoke(2)
                    toast("Shrift oʻrnatildi ✓ Emoji panelini ochib koʻring")
                }
                fontSub?.text = fontStatusText()
            }
        }.start()
    }

    // ======================= yangilanishlar =======================

    private fun updateStatusText(): String {
        val cur = Updater.currentVersion(this)
        return when {
            Updater.repo.isBlank() -> "Manba sozlanmagan"
            prefs.latestVersion > cur -> "Yangi versiya bor: ${prefs.latestTag}"
            prefs.lastUpdateCheck > 0L -> "Sizda eng soʻnggi versiya ✓"
            else -> "Hozirgi versiya: ${Updater.currentVersionName(this)}"
        }
    }

    private fun setUpdateBusy(busy: Boolean, text: String?) {
        updateTile?.fast = busy
        if (text != null) updateSub?.text = text
    }

    private fun checkUpdates(user: Boolean) {
        if (checking || installing) return
        if (Updater.repo.isBlank()) {
            toast("Yangilanish manbasi sozlanmagan (README'ga qarang)")
            return
        }
        checking = true
        setUpdateBusy(true, "Tekshirilmoqda…")
        Thread {
            try {
                val r = Updater.fetchLatest()
                Updater.remember(this, r)
                runOnUiThread {
                    checking = false
                    if (isFinishing) return@runOnUiThread
                    if (r.version > Updater.currentVersion(this)) {
                        setUpdateBusy(false, "Yangi versiya bor: ${r.tag}")
                        showUpdateDialog(r)
                    } else {
                        setUpdateBusy(false, "Sizda eng soʻnggi versiya ✓")
                        if (user) toast("Sizda eng soʻnggi versiya")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    checking = false
                    setUpdateBusy(false, e.message ?: "Tekshirib boʻlmadi")
                    if (user) toast("Internetni tekshiring")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(r: Updater.Release) {
        val size = if (r.size > 0) "\n\nHajmi: %.1f MB".format(r.size / (1024.0 * 1024.0)) else ""
        val notes = r.notes.ifBlank { "Yangi versiyani oʻrnatasizmi?" }
        AlertDialog.Builder(this)
            .setTitle("Yangi versiya: ${r.tag}")
            .setMessage(notes + size)
            .setPositiveButton("Yangilash") { _, _ -> startInstall(r) }
            .setNegativeButton("Keyinroq", null)
            .show()
    }

    private fun startInstall(r: Updater.Release) {
        if (installing) return
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstall = r
            toast("“Nomaʼlum ilovalarni oʻrnatish”ga ruxsat bering")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
            return
        }
        pendingInstall = null
        installing = true
        setUpdateBusy(true, "Yuklanmoqda… 0%")
        Thread {
            try {
                Updater.install(this, r, fromUser = true) { pct -> runOnUiThread { updateSub?.text = "Yuklanmoqda… $pct%" } }
                runOnUiThread {
                    installing = false
                    setUpdateBusy(false, "Oʻrnatish oynasida “Yangilash”ni bosing")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    installing = false
                    setUpdateBusy(false, "Yuklab boʻlmadi: ${e.message ?: "xato"}")
                }
            }
        }.start()
    }

    // ======================= qo'llanma =======================

    private fun maybeShowGuide() {
        val cur = Updater.currentVersion(this)
        if (prefs.lastSeenVersion == cur) return
        val updated = prefs.lastSeenVersion != 0L
        prefs.lastSeenVersion = cur
        showGuide(updated)
    }

    private fun showGuide(updated: Boolean) {
        val cur = Updater.currentVersion(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22f), dp(20f), dp(22f), dp(8f))
        box.addView(TextView(this).apply {
            text = if (updated) "Yangilandi! 🎉" else "Qoʻllanma"
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(textColor)
        })
        box.addView(TextView(this).apply {
            text = "MROS Keyboard ${Updater.currentVersionName(this@SettingsActivity)}"
            textSize = 14f
            setTextColor(subColor)
            setPadding(0, dp(2f), 0, dp(12f))
        })
        val notes = if (prefs.latestVersion == cur && prefs.latestNotes.isNotBlank()) prefs.latestNotes else WHATS_NEW
        guideHeader(box, "NIMA YANGI")
        box.addView(TextView(this).apply {
            text = notes.trim()
            textSize = 15f
            setTextColor(textColor)
            setLineSpacing(0f, 1.15f)
        })
        guideHeader(box, "QANDAY ISHLATILADI")
        guideTip(box, cBlue, { c, x, y, s, p -> Icons.keyboard(c, x, y, s, p) }, "Yoqish",
            "Sozlamalar tepasidagi “1. Yoqish” va “2. Tanlash” tugmalari.")
        guideTip(box, cIndigo, { c, x, y, s, p -> Icons.globe(c, x, y, s, p) }, "Tilni almashtirish",
            "Boʻsh joyni bosib turing yoki yuqoridagi 🌐 UZ belgisini bosing.")
        guideTip(box, cTeal, { c, x, y, s, p -> Icons.arrowRight(c, x, y, s, p) }, "Kursorni siljitish",
            "Boʻsh joy ustida barmoqni chapga-oʻngga suring.")
        guideTip(box, cPurple, { c, x, y, s, p ->
            p.textSize = s * 0.62f; p.style = Paint.Style.FILL
            c.drawText("oʻ", x, y - (p.descent() + p.ascent()) / 2f, p)
        }, "Qoʻshimcha harflar", "Harfni bosib turing: o → oʻ, g → gʻ, у → ў, tepada — raqamlar.")
        guideTip(box, cGray, { c, x, y, s, p -> Icons.shift(c, x, y, s, p, 2) }, "CAPS LOCK",
            "Shift’ni ikki marta tez bosing.")
        guideTip(box, cYellow, { c, x, y, s, p -> Icons.smiley(c, x, y, s, p) }, "Emoji",
            "😊 tugmasi. Emoji ustida bosib turing — teri rangi. Tepada — qidiruv.")
        guideTip(box, cOrange, { c, x, y, s, p -> Icons.clipboard(c, x, y, s, p) }, "Bufer",
            "Yuqoridagi 📋 — nusxa olingan matnlar. Bosib turing — oʻchirish.")
        guideTip(box, cIndigo, { c, x, y, s, p -> Icons.font(c, x, y, s, p) }, "Shaxsiy shrift",
            "Sozlamalar → Shaxsiy shrift → “TTF fayl yuklash”.")
        guideTip(box, cGreen, { c, x, y, s, p -> Icons.sync(c, x, y, s, p) }, "Yangilanishlar",
            "Yangi versiya chiqsa bildirishnoma keladi — bir bosishda oʻrnatiladi.")
        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setView(sv)
            .setPositiveButton("Tushunarli", null)
            .show()
    }

    private fun guideHeader(box: LinearLayout, t: String) {
        box.addView(TextView(this).apply {
            text = t
            textSize = 12.5f
            setTextColor(subColor)
            setPadding(0, dp(16f), 0, dp(6f))
        })
    }

    private fun guideTip(box: LinearLayout, color: Int, draw: (Canvas, Float, Float, Float, Paint) -> Unit, title: String, text: String) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(6f), 0, dp(6f))
        r.addView(IconTile(this, color, draw))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(dp(12f), 0, 0, 0)
        t.addView(TextView(this).apply {
            this.text = title
            textSize = 15.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(textColor)
        })
        t.addView(TextView(this).apply {
            this.text = text
            textSize = 13.5f
            setTextColor(subColor)
        })
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(r)
    }

    companion object {
        private const val REQ_FONT = 71
        private const val REQ_NOTIF = 72
        private const val WHATS_NEW = "• Ilova ichidan yangilanish va bildirishnomalar\n" +
            "• Shaxsiy shrift (TTF) yuklash\n" +
            "• Qoʻllanma oynasi\n" +
            "• Info: Telegram kanal va muallif"
    }
}
