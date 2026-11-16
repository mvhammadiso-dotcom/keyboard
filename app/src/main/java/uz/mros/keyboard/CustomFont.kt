package uz.mros.keyboard

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * Foydalanuvchi o'zi tanlagan shrift fayli (TTF/OTF/TTC) — emoji panelida ishlatiladi.
 * Fayl ilovaning ichki papkasiga nusxalanadi.
 */
object CustomFont {
    private var cached: Typeface? = null
    private var cachedStamp = -1L

    fun file(ctx: Context) = File(ctx.filesDir, "custom_emoji_font.ttf")

    fun exists(ctx: Context) = file(ctx).exists()

    /** stamp o'zgarsa fayl qayta o'qiladi. */
    fun typeface(ctx: Context, stamp: Long): Typeface? {
        if (stamp != cachedStamp) {
            cachedStamp = stamp
            cached = load(file(ctx))
        }
        return cached
    }

    private fun load(f: File): Typeface? {
        if (!f.exists()) return null
        return try {
            Typeface.Builder(f).build()
        } catch (_: Exception) {
            null
        }
    }

    /** Faylni import qiladi. Xato bo'lsa — xabar matni, muvaffaqiyatli bo'lsa — null. */
    fun import(ctx: Context, uri: Uri): String? {
        val tmp = File(ctx.filesDir, "font_import.tmp")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return "Faylni oʻqib boʻlmadi"
            input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            val len = tmp.length()
            if (len < 1024) return "Fayl juda kichik — bu shrift emas"
            if (len > 80L * 1024 * 1024) return "Fayl juda katta (80 MB dan oshmasin)"
            val head = ByteArray(4)
            tmp.inputStream().use { it.read(head) }
            val sig = String(head, Charsets.ISO_8859_1)
            val ok = (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0) ||
                sig == "true" || sig == "OTTO" || sig == "ttcf"
            if (!ok) return "Bu TTF/OTF shrift fayli emas"
            val tf = try {
                Typeface.Builder(tmp).build()
            } catch (_: Exception) {
                null
            }
            if (tf == null) return "Shriftni ochib boʻlmadi"
            val dst = file(ctx)
            dst.delete()
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
            }
            return null
        } catch (e: Exception) {
            return "Xato: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            tmp.delete()
        }
    }

    fun remove(ctx: Context) {
        file(ctx).delete()
        cachedStamp = -1L
        cached = null
    }
}
