package uz.mros.keyboard

import android.graphics.RectF

enum class Lang(val code: String, val label: String, val lettersLabel: String) {
    UZ("uz", "Oʻzbekcha", "ABC"),
    RU("ru", "Русский", "АБВ"),
    EN("en", "English", "ABC");

    companion object {
        fun of(code: String?): Lang? = values().firstOrNull { it.code == code }
    }
}

enum class KeyType { CHAR, SHIFT, BACKSPACE, SPACE, ENTER, SYMBOLS, SYMBOLS2, LETTERS, EMOJI, GLOBE, SPACER }

enum class LayoutKind { LETTERS, SYMBOLS, SYMBOLS2, NUMPAD }

/** Maydon turi: oddiy, email, havola, parol */
enum class FieldVariant { NORMAL, EMAIL, URL, PASSWORD }

class Key(
    val type: KeyType,
    val label: String = "",
    val output: String = label,
    val width: Float = 1f,
    val alternates: List<String> = emptyList(),
    val hint: String? = null,
    /** Harf tugmasi (shift ta'sir qiladi) */
    val isLetter: Boolean = false,
) {
    val rect = RectF()
    val isFunctional: Boolean
        get() = type != KeyType.CHAR && type != KeyType.SPACE && type != KeyType.SPACER
}

class Row(val keys: List<Key>, val fill: Boolean = false) {
    val units: Float get() = keys.fold(0f) { acc, k -> acc + k.width }
}

class KeyboardLayout(
    val kind: LayoutKind,
    val lang: Lang,
    val rows: List<Row>,
) {
    val allKeys: List<Key> = rows.flatMap { it.keys }
}

class LayoutOptions(
    val numberRow: Boolean,
    val showGlobe: Boolean,
    val variant: FieldVariant,
    val officialApostrophe: Boolean,
)

object Layouts {

    private const val DIGITS = "1234567890"

    private val latinAccents = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "ā", "æ"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "ı"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "ō", "œ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
        "s" to listOf("ß", "ś", "š", "ş"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "g" to listOf("ğ"),
        "l" to listOf("ł"),
    )

    private val ruAlternates = mapOf(
        "е" to listOf("ё"),
        "ь" to listOf("ъ"),
        "х" to listOf("ъ", "ҳ"),
        "у" to listOf("ў"),
        "к" to listOf("қ"),
        "г" to listOf("ғ"),
        "и" to listOf("й", "і"),
        "з" to listOf("ҙ"),
    )

    fun build(kind: LayoutKind, lang: Lang, o: LayoutOptions): KeyboardLayout = when (kind) {
        LayoutKind.LETTERS -> letters(lang, o)
        LayoutKind.SYMBOLS -> symbols(lang, o)
        LayoutKind.SYMBOLS2 -> symbols2(lang, o)
        LayoutKind.NUMPAD -> numpad(lang)
    }

    private fun letterKey(ch: String, digit: String?, extra: List<String>): Key {
        val alts = ArrayList<String>()
        if (digit != null) alts.add(digit)
        alts.addAll(extra)
        return Key(KeyType.CHAR, ch, ch, 1f, alts, hint = digit, isLetter = true)
    }

    private fun numberRow(): Row = Row(DIGITS.map { d ->
        val alts = when (d) {
            '1' -> listOf("¹", "½", "⅓", "¼")
            '2' -> listOf("²", "⅔")
            '3' -> listOf("³", "¾")
            '0' -> listOf("⁰", "∅")
            else -> emptyList()
        }
        Key(KeyType.CHAR, d.toString(), d.toString(), 1f, alts)
    })

    private fun letters(lang: Lang, o: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<Row>()
        if (o.numberRow) rows.add(numberRow())
        val hints = !o.numberRow
        val apo = if (o.officialApostrophe) "ʻ" else "'"
        when (lang) {
            Lang.RU -> {
                val r1 = "йцукенгшщзх"
                val r2 = "фывапролджэ"
                val r3 = "ячсмитьбю"
                rows.add(Row(r1.mapIndexed { i, c ->
                    val digit = if (hints && i < 10) DIGITS[i].toString() else null
                    letterKey(c.toString(), digit, ruAlternates[c.toString()] ?: emptyList())
                }))
                rows.add(Row(r2.map { c -> letterKey(c.toString(), null, ruAlternates[c.toString()] ?: emptyList()) }))
                val k3 = ArrayList<Key>()
                k3.add(Key(KeyType.SHIFT, width = 1.0f))
                r3.forEach { c -> k3.add(letterKey(c.toString(), null, ruAlternates[c.toString()] ?: emptyList())) }
                k3.add(Key(KeyType.BACKSPACE, width = 1.0f))
                rows.add(Row(k3))
            }
            Lang.UZ, Lang.EN -> {
                val r1 = "qwertyuiop"
                rows.add(Row(r1.mapIndexed { i, c ->
                    val s = c.toString()
                    val extra = ArrayList<String>()
                    if (lang == Lang.UZ && s == "o") extra.add("o$apo")
                    extra.addAll(latinAccents[s] ?: emptyList())
                    letterKey(s, if (hints) DIGITS[i].toString() else null, extra)
                }))
                val r2 = ArrayList<Key>()
                "asdfghjkl".forEach { c ->
                    val s = c.toString()
                    val extra = ArrayList<String>()
                    if (lang == Lang.UZ && s == "g") extra.add("g$apo")
                    extra.addAll(latinAccents[s] ?: emptyList())
                    r2.add(letterKey(s, null, extra))
                }
                if (lang == Lang.UZ) {
                    val alts = if (o.officialApostrophe) listOf("ʼ", "'", "’", "`") else listOf("ʻ", "ʼ", "’", "`")
                    r2.add(Key(KeyType.CHAR, apo, apo, 1f, alts))
                }
                rows.add(Row(r2))
                val k3 = ArrayList<Key>()
                k3.add(Key(KeyType.SHIFT, width = 1.3f))
                k3.add(Key(KeyType.SPACER, width = 0.2f))
                "zxcvbnm".forEach { c ->
                    val s = c.toString()
                    k3.add(letterKey(s, null, latinAccents[s] ?: emptyList()))
                }
                k3.add(Key(KeyType.SPACER, width = 0.2f))
                k3.add(Key(KeyType.BACKSPACE, width = 1.3f))
                rows.add(Row(k3))
            }
        }
        rows.add(bottomRow(KeyType.SYMBOLS, "?123", lang, o))
        return KeyboardLayout(LayoutKind.LETTERS, lang, rows)
    }

    private fun bottomRow(switchType: KeyType, switchLabel: String, lang: Lang, o: LayoutOptions): Row {
        // iOS/Gboard kabi: [?123] [😊] [,] [ bo'shliq ] [.] [⏎]  — til almashtirish: bo'shliqni bosib turish
        val keys = ArrayList<Key>()
        keys.add(Key(switchType, switchLabel, width = 1.45f))
        keys.add(Key(KeyType.EMOJI, width = 1.1f))
        when (o.variant) {
            FieldVariant.EMAIL -> keys.add(Key(KeyType.CHAR, "@", "@", 1f, listOf(".com", ".uz", ".ru", "_", "-")))
            FieldVariant.URL -> keys.add(Key(KeyType.CHAR, "/", "/", 1f, listOf(".com", ".uz", ":", "_", "-")))
            else -> keys.add(Key(KeyType.CHAR, ",", ",", 1f, listOf(";", ":", "'", "\"", "(", ")")))
        }
        keys.add(Key(KeyType.SPACE, " ", " ", 3.85f))
        keys.add(Key(KeyType.CHAR, ".", ".", 1f, listOf("?", "!", ",", "'", "\"", ":", ";", "-", "…")))
        keys.add(Key(KeyType.ENTER, width = 1.6f))
        return Row(keys, fill = true)
    }

    private fun sym(s: String, vararg alts: String) = Key(KeyType.CHAR, s, s, 1f, alts.toList())

    private fun symbols(lang: Lang, o: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<Row>()
        rows.add(numberRow())
        rows.add(Row(listOf(
            sym("@"), sym("#", "№"), sym("$", "€", "£", "¥", "₽", "¢", "₩"), sym("_"), sym("&"),
            sym("-", "–", "—", "·"), sym("+", "±"), sym("(", "<", "[", "{"), sym(")", ">", "]", "}"), sym("/", "\\", "|"),
        )))
        rows.add(Row(listOf(
            Key(KeyType.SYMBOLS2, "=\\<", width = 1.3f),
            Key(KeyType.SPACER, width = 0.2f),
            sym("*", "★", "†", "‡"), sym("\"", "«", "»", "“", "”", "„"), sym("'", "‘", "’", "ʻ", "ʼ"), sym(":"), sym(";"),
            sym("!", "¡"), sym("?", "¿"),
            Key(KeyType.SPACER, width = 0.2f),
            Key(KeyType.BACKSPACE, width = 1.3f),
        )))
        rows.add(bottomRow(KeyType.LETTERS, lang.lettersLabel, lang, o))
        return KeyboardLayout(LayoutKind.SYMBOLS, lang, rows)
    }

    private fun symbols2(lang: Lang, o: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<Row>()
        rows.add(Row(listOf(sym("~"), sym("`"), sym("|"), sym("•", "◦", "○", "●"), sym("√"), sym("π", "Π"),
            sym("÷"), sym("×"), sym("¶", "§"), sym("∆"))))
        rows.add(Row(listOf(sym("£"), sym("¢"), sym("€"), sym("¥"), sym("^", "↑", "↓", "←", "→"), sym("°", "′", "″"),
            sym("=", "≠", "≈", "∞"), sym("{"), sym("}"), sym("\\"))))
        rows.add(Row(listOf(
            Key(KeyType.SYMBOLS, "?123", width = 1.3f),
            Key(KeyType.SPACER, width = 0.2f),
            sym("%", "‰"), sym("©"), sym("®"), sym("™"), sym("✓"), sym("["), sym("]"),
            Key(KeyType.SPACER, width = 0.2f),
            Key(KeyType.BACKSPACE, width = 1.3f),
        )))
        rows.add(bottomRow(KeyType.LETTERS, lang.lettersLabel, lang, o))
        return KeyboardLayout(LayoutKind.SYMBOLS2, lang, rows)
    }

    private fun numpad(lang: Lang): KeyboardLayout {
        fun n(s: String, vararg alts: String) = Key(KeyType.CHAR, s, s, 1f, alts.toList())
        val rows = listOf(
            Row(listOf(n("1"), n("2"), n("3"), n("-", "/", "(", ")"))),
            Row(listOf(n("4"), n("5"), n("6"), n("+", "*", "#", "="))),
            Row(listOf(n("7"), n("8"), n("9"), Key(KeyType.BACKSPACE))),
            Row(listOf(Key(KeyType.SYMBOLS, "!?#"), n("0", "+"), n(".", ",", ":"), Key(KeyType.ENTER))),
        )
        return KeyboardLayout(LayoutKind.NUMPAD, lang, rows)
    }
}
