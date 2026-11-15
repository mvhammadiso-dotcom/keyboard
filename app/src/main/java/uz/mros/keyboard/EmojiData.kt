package uz.mros.keyboard

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import android.os.Looper

class EmojiItem(
    val base: String,
    val template: String?,
    val keywords: Array<String>,
    val category: Int,
) {
    val hasTones: Boolean get() = template != null

    /** tone: 0 = sariq (asosiy), 1..5 = teri ranglari */
    fun variant(tone: Int): String =
        if (template == null || tone <= 0) base else template.replace(EmojiData.TPL, EmojiData.TONES[tone - 1])
}

/** Emoji ro'yxati (Unicode 16), iOS tartibidagi 8 bo'lim. */
object EmojiData {
    const val TPL = ""
    val TONES = arrayOf("🏻", "🏼", "🏽", "🏾", "🏿")

    val CATEGORY_TITLES = arrayOf(
        "SMAYLIKLAR VA ODAMLAR",
        "HAYVONLAR VA TABIAT",
        "TAOMLAR VA ICHIMLIKLAR",
        "FAOLIYAT",
        "SAYOHAT VA JOYLAR",
        "BUYUMLAR",
        "BELGILAR",
        "BAYROQLAR",
    )
    const val RECENT_TITLE = "TEZ-TEZ ISHLATILADI"
    val CATEGORY_ICONS = arrayOf("😀", "🐻", "🍔", "⚽", "🚗", "💡", "🔣", "🏳️")
    const val RECENT_ICON = "🕘"

    @Volatile
    var ready = false
        private set

    @Volatile
    var categories: List<List<EmojiItem>> = emptyList()
        private set

    @Volatile
    private var all: List<EmojiItem> = emptyList()

    /** Har qanday variant (teri rangli ham) -> element */
    @Volatile
    private var lookup: Map<String, EmojiItem> = emptyMap()

    private var started = false
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, onReady: (() -> Unit)? = null) {
        if (ready) {
            onReady?.invoke()
            return
        }
        if (onReady != null) synchronized(listeners) { listeners.add(onReady) }
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        Thread {
            val paint = Paint()
            val cats = List(8) { ArrayList<EmojiItem>() }
            val map = HashMap<String, EmojiItem>()
            try {
                app.assets.open("emoji.tsv").bufferedReader(Charsets.UTF_8).useLines { seq ->
                    seq.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size >= 4) {
                            val cat = parts[0].toIntOrNull() ?: 0
                            val base = parts[1]
                            if (base.isNotEmpty() && paint.hasGlyph(base)) {
                                var tpl: String? = parts[2].ifEmpty { null }
                                if (tpl != null && !paint.hasGlyph(tpl.replace(TPL, TONES[2]))) tpl = null
                                val kw = parts[3].split(' ').filter { it.isNotEmpty() }.toTypedArray()
                                val item = EmojiItem(base, tpl, kw, cat.coerceIn(0, 7))
                                cats[item.category].add(item)
                                map[base] = item
                                map[base.replace("️", "")] = item
                                if (tpl != null) for (t in 1..5) map[item.variant(t)] = item
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            categories = cats
            all = cats.flatten()
            lookup = map
            ready = true
            main.post {
                val ls = synchronized(listeners) { listeners.toList().also { listeners.clear() } }
                ls.forEach { it() }
            }
        }.start()
    }

    fun itemFor(emoji: String): EmojiItem? = lookup[emoji] ?: lookup[emoji.replace("️", "")]

    fun search(query: String, limit: Int = 80): List<EmojiItem> {
        val q = normalize(query)
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<EmojiItem, Int>>()
        for (item in all) {
            var score = 0
            var ok = true
            for (t in tokens) {
                var best = 0
                for (k in item.keywords) {
                    if (k == t) { best = 3; break }
                    if (k.startsWith(t)) best = maxOf(best, 1)
                }
                if (best == 0) { ok = false; break }
                score += best
            }
            if (ok) scored.add(item to score)
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            when (ch) {
                'ʻ', 'ʼ', '’', '‘', '\'', '`' -> {}
                'ё' -> sb.append('е')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }
}
