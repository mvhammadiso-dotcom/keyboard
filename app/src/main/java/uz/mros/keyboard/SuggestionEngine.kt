package uz.mros.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * So'z takliflari: tayyor lug'at (assets) + foydalanuvchi yozgan so'zlarni o'rganish.
 */
class SuggestionEngine(private val context: Context) {

    private class Dict(val keys: Array<String>, val scores: IntArray)

    private val dicts = ConcurrentHashMap<Lang, Dict>()
    private val loading = HashSet<Lang>()
    private val user = HashMap<Lang, HashMap<String, Int>>()
    private val dirty = HashSet<Lang>()
    private var learnedSinceSave = 0
    private val main = Handler(Looper.getMainLooper())

    /** Lug'at yuklangach chaqiriladi (takliflarni yangilash uchun). */
    var onDictLoaded: (() -> Unit)? = null

    fun prepare(lang: Lang) {
        if (dicts.containsKey(lang)) return
        synchronized(loading) {
            if (!loading.add(lang)) return
        }
        Thread {
            val d = try {
                loadDict(lang)
            } catch (e: Exception) {
                Dict(emptyArray(), IntArray(0))
            }
            dicts[lang] = d
            synchronized(loading) { loading.remove(lang) }
            main.post { onDictLoaded?.invoke() }
        }.start()
    }

    private fun loadDict(lang: Lang): Dict {
        val words = ArrayList<String>()
        context.assets.open("dict_${lang.code}.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.forEach { line ->
                val w = line.trim()
                if (w.isNotEmpty()) words.add(normalize(w))
            }
        }
        val n = words.size
        // takrorlarni olib tashlab, eng yuqori bahoni saqlaymiz
        val best = HashMap<String, Int>(n * 2)
        words.forEachIndexed { idx, w ->
            val score = n - idx
            val old = best[w]
            if (old == null || old < score) best[w] = score
        }
        val keys = best.keys.toTypedArray()
        keys.sort()
        val scores = IntArray(keys.size) { best[keys[it]] ?: 0 }
        return Dict(keys, scores)
    }

    private fun userMap(lang: Lang): HashMap<String, Int> {
        return user.getOrPut(lang) {
            val m = HashMap<String, Int>()
            val f = userFile(lang)
            if (f.exists()) {
                try {
                    f.forEachLine(Charsets.UTF_8) { line ->
                        val tab = line.lastIndexOf('\t')
                        if (tab > 0) {
                            val c = line.substring(tab + 1).toIntOrNull()
                            if (c != null) m[line.substring(0, tab)] = c
                        }
                    }
                } catch (_: Exception) {
                }
            }
            m
        }
    }

    private fun userFile(lang: Lang) = File(context.filesDir, "user_${lang.code}.txt")

    fun suggest(lang: Lang, typed: String, officialApostrophe: Boolean, limit: Int = 3): List<String> {
        val key = normalize(typed)
        if (key.isEmpty()) return emptyList()
        val scores = HashMap<String, Long>()

        val d = dicts[lang]
        if (d != null && d.keys.isNotEmpty()) {
            var i = lowerBound(d.keys, key)
            var scanned = 0
            while (i < d.keys.size && scanned < 6000) {
                val w = d.keys[i]
                if (!w.startsWith(key)) break
                if (w != key) scores[w] = d.scores[i].toLong()
                i++
                scanned++
            }
        }
        for ((w, cnt) in userMap(lang)) {
            if (w.length > key.length && w.startsWith(key)) {
                val base = scores[w] ?: 0L
                scores[w] = base + 50_000L + cnt * 20_000L
            }
        }
        if (scores.isEmpty()) return emptyList()
        val top = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.length })
            .take(limit)
            .map { format(it.key, typed, lang, officialApostrophe) }
        return top
    }

    fun isKnown(lang: Lang, word: String): Boolean {
        val key = normalize(word)
        val d = dicts[lang]
        if (d != null) {
            val i = lowerBound(d.keys, key)
            if (i < d.keys.size && d.keys[i] == key) return true
        }
        return userMap(lang).containsKey(key)
    }

    fun learn(lang: Lang, word: String) {
        val key = normalize(word)
        if (key.length < 2 || key.length > 32) return
        if (key.none { it.isLetter() }) return
        if (key.any { it.isDigit() }) return
        val m = userMap(lang)
        m[key] = (m[key] ?: 0) + 1
        dirty.add(lang)
        learnedSinceSave++
        if (learnedSinceSave >= 15) save()
    }

    fun save() {
        learnedSinceSave = 0
        val langs = dirty.toList()
        dirty.clear()
        for (lang in langs) {
            val m = user[lang] ?: continue
            try {
                // eng ko'p ishlatilgan 5000 so'zni saqlaymiz
                val entries = m.entries.sortedByDescending { it.value }.take(5000)
                userFile(lang).bufferedWriter(Charsets.UTF_8).use { w ->
                    for (e in entries) {
                        w.write(e.key); w.write("\t"); w.write(e.value.toString()); w.write("\n")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun clearLearned() {
        user.clear()
        dirty.clear()
        for (l in Lang.values()) userFile(l).delete()
    }

    private fun format(word: String, typed: String, lang: Lang, officialApostrophe: Boolean): String {
        var w = word
        if (lang == Lang.UZ && w.contains('\'')) {
            val official = when {
                typed.any { it == 'ʻ' || it == 'ʼ' } -> true
                typed.contains('\'') -> false
                else -> officialApostrophe
            }
            if (official) {
                val sb = StringBuilder()
                for (i in w.indices) {
                    val ch = w[i]
                    if (ch == '\'') {
                        val prev = if (i > 0) w[i - 1].lowercaseChar() else ' '
                        sb.append(if (prev == 'o' || prev == 'g') 'ʻ' else 'ʼ')
                    } else sb.append(ch)
                }
                w = sb.toString()
            }
        }
        val letters = typed.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> w.uppercase()
            typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.uppercaseChar() }
            else -> w
        }
    }

    companion object {
        fun normalize(w: String): String {
            val sb = StringBuilder(w.length)
            for (ch in w) {
                when (ch) {
                    'ʻ', 'ʼ', '’', '‘', '`', '´' -> sb.append('\'')
                    else -> sb.append(ch.lowercaseChar())
                }
            }
            return sb.toString()
        }

        private fun lowerBound(arr: Array<String>, key: String): Int {
            var lo = 0
            var hi = arr.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (arr[mid] < key) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
