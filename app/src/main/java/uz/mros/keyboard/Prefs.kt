package uz.mros.keyboard

import android.content.Context
import android.content.SharedPreferences

/** Barcha sozlamalar bitta joyda. */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mros_keyboard", Context.MODE_PRIVATE)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setB(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun setI(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun l(key: String, def: Long) = sp.getLong(key, def)
    private fun setL(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    private fun s(key: String, def: String) = sp.getString(key, def) ?: def
    private fun setS(key: String, v: String) = sp.edit().putString(key, v).apply()

    /** 0 = tizimga qarab, 1 = yorug', 2 = qorong'i */
    var theme: Int
        get() = i("theme", 0)
        set(v) = setI("theme", v)

    var sound: Boolean
        get() = b("sound", false)
        set(v) = setB("sound", v)

    var vibrate: Boolean
        get() = b("vibrate", true)
        set(v) = setB("vibrate", v)

    /** 0 yengil, 1 o'rta, 2 kuchli */
    var vibrateStrength: Int
        get() = i("vibrate_strength", 0)
        set(v) = setI("vibrate_strength", v)

    var numberRow: Boolean
        get() = b("number_row", false)
        set(v) = setB("number_row", v)

    var keyPopup: Boolean
        get() = b("key_popup", true)
        set(v) = setB("key_popup", v)

    var blur: Boolean
        get() = b("blur", false)
        set(v) = setB("blur", v)

    /** Suyuq shisha effektlari: jele animatsiya, suv to'lqini, yorug'lik */
    var liquidEffects: Boolean
        get() = b("liquid", true)
        set(v) = setB("liquid", v)

    /** Navigatsiya paneli ustidan qo'shimcha masofa (dp) */
    var bottomOffset: Int
        get() = i("bottom_offset", 6)
        set(v) = setI("bottom_offset", v)

    // ---- yangilanishlar ----
    /** Yangi versiyani o'zi yuklab o'rnatish (Wi-Fi'da) */
    var autoUpdate: Boolean
        get() = b("auto_update", true)
        set(v) = setB("auto_update", v)

    var autoInstalledVersion: Long
        get() = l("auto_installed_version", 0L)
        set(v) = setL("auto_installed_version", v)

    var updateNotify: Boolean
        get() = b("update_notify", true)
        set(v) = setB("update_notify", v)

    /** Qo'llanma oxirgi marta ko'rsatilgan versiya */
    var lastSeenVersion: Long
        get() = l("last_seen_version", 0L)
        set(v) = setL("last_seen_version", v)

    /** Qaysi versiya haqida bildirishnoma yuborilgan */
    var notifiedVersion: Long
        get() = l("notified_version", 0L)
        set(v) = setL("notified_version", v)

    var lastUpdateCheck: Long
        get() = l("last_update_check", 0L)
        set(v) = setL("last_update_check", v)

    var latestVersion: Long
        get() = l("latest_version", 0L)
        set(v) = setL("latest_version", v)

    var latestTag: String
        get() = s("latest_tag", "")
        set(v) = setS("latest_tag", v)

    var latestNotes: String
        get() = s("latest_notes", "")
        set(v) = setS("latest_notes", v)

    /** Shaxsiy shrift o'zgargan vaqt (keshni yangilash uchun) */
    var fontStamp: Long
        get() = l("font_stamp", 0L)
        set(v) = setL("font_stamp", v)

    /** 0 = 3D emoji (bor bo'lsa), 1 = tizim emojisi, 2 = shaxsiy shrift */
    var emojiStyle: Int
        get() = i("emoji_style", 0)
        set(v) = setI("emoji_style", v)

    var heightPercent: Int
        get() = i("height_pct", 100)
        set(v) = setI("height_pct", v)

    var autoCap: Boolean
        get() = b("auto_cap", true)
        set(v) = setB("auto_cap", v)

    var doubleSpacePeriod: Boolean
        get() = b("double_space", true)
        set(v) = setB("double_space", v)

    var suggestions: Boolean
        get() = b("suggestions", true)
        set(v) = setB("suggestions", v)

    var learnWords: Boolean
        get() = b("learn", true)
        set(v) = setB("learn", v)

    /** 0 = rasmiy (oʻ gʻ maʼno), 1 = oddiy apostrof (o' g') */
    var apostropheStyle: Int
        get() = i("apostrophe", 0)
        set(v) = setI("apostrophe", v)

    var languages: List<Lang>
        get() {
            val list = s("langs", "uz,ru,en").split(',').mapNotNull { Lang.of(it.trim()) }.distinct()
            return list.ifEmpty { listOf(Lang.UZ) }
        }
        set(v) = setS("langs", v.joinToString(",") { it.code })

    var currentLang: Lang
        get() {
            val langs = languages
            val c = Lang.of(s("cur_lang", langs.first().code))
            return if (c != null && c in langs) c else langs.first()
        }
        set(v) = setS("cur_lang", v.code)

    var recentEmoji: List<String>
        get() = s("recent_emoji", "").split(SEP).filter { it.isNotEmpty() }
        set(v) = setS("recent_emoji", v.joinToString(SEP))

    var clips: List<String>
        get() = s("clips", "").split(SEP).filter { it.isNotEmpty() }
        set(v) = setS("clips", v.joinToString(SEP))

    fun toneFor(base: String): Int = sp.getInt("tone_$base", 0)
    fun setTone(base: String, tone: Int) = sp.edit().putInt("tone_$base", tone).apply()

    companion object {
        const val SEP = "\u0001"
    }
}
