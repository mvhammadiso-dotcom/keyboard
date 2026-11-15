package uz.mros.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/**
 * 3D emoji rasmlari (Microsoft Fluent Emoji, MIT litsenziya).
 * GitHub build paytida assets/emoji3d/ ga WebP qilib joylanadi. Yo'q bo'lsa — tizim emojisi ishlatiladi.
 */
object EmojiImages {
    private const val DIR = "emoji3d"

    @Volatile
    var available = false
        private set

    @Volatile
    private var names: Set<String> = emptySet()
    private var started = false
    private var app: Context? = null

    private val cache = object : LruCache<String, Bitmap>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pending = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<() -> Unit>()
    private var notifyPosted = false

    fun init(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
            app = context.applicationContext
        }
        executor.execute {
            try {
                val list = app?.assets?.list(DIR) ?: emptyArray()
                val set = HashSet<String>(list.size * 2)
                for (n in list) if (n.endsWith(".webp")) set.add(n.removeSuffix(".webp"))
                names = set
                available = set.isNotEmpty()
            } catch (_: Exception) {
                available = false
            }
        }
    }

    fun keyFor(emoji: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFE0F) continue
            if (sb.isNotEmpty()) sb.append('_')
            sb.append(Integer.toHexString(cp))
        }
        return sb.toString()
    }

    fun has(emoji: String): Boolean = available && keyFor(emoji) in names

    /** Ko'rinish yangilanishi kerak bo'lganda chaqiriladigan funksiya */
    fun addListener(l: () -> Unit) {
        synchronized(listeners) { if (l !in listeners) listeners.add(l) }
    }

    fun removeListener(l: () -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    /** Keshdan oladi; yo'q bo'lsa fon oqimida yuklaydi va null qaytaradi. */
    fun get(emoji: String): Bitmap? {
        val key = keyFor(emoji)
        cache.get(key)?.let { return it }
        if (key !in names) return null
        synchronized(pending) {
            if (!pending.add(key)) return null
        }
        executor.execute {
            var bmp: Bitmap? = null
            try {
                app?.assets?.open("$DIR/$key.webp")?.use { input ->
                    val bytes = input.readBytes()
                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Exception) {
            }
            val b = bmp
            if (b != null) cache.put(key, b)
            synchronized(pending) { pending.remove(key) }
            scheduleNotify()
        }
        return null
    }

    private fun scheduleNotify() {
        synchronized(this) {
            if (notifyPosted) return
            notifyPosted = true
        }
        main.postDelayed({
            synchronized(this) { notifyPosted = false }
            val ls = synchronized(listeners) { listeners.toList() }
            ls.forEach { it() }
        }, 16L)
    }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /**
     * Emoji chizish: 3D rasm bor bo'lsa — rasm, aks holda tizim shrifti.
     * size — emoji kvadratining o'lchami (px).
     */
    fun draw(c: Canvas, emoji: String, cx: Float, cy: Float, size: Float, textPaint: Paint, use3d: Boolean, alpha: Int = 255) {
        if (use3d && available && has(emoji)) {
            val b = get(emoji)
            if (b != null) {
                val s = size * 0.96f
                dst.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
                bmpPaint.alpha = alpha
                c.drawBitmap(b, null, dst, bmpPaint)
            }
            return
        }
        val old = textPaint.alpha
        if (alpha != 255) textPaint.alpha = alpha
        c.drawText(emoji, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        if (alpha != 255) textPaint.alpha = old
    }
}
