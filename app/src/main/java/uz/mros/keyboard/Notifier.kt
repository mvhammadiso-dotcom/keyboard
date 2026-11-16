package uz.mros.keyboard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Yangilanish bildirishnomalari. */
object Notifier {
    private const val CHANNEL = "updates"
    const val EXTRA_OPEN = "open"
    const val OPEN_UPDATE = "update"
    const val OPEN_GUIDE = "guide"

    private fun manager(ctx: Context): NotificationManager? = ctx.getSystemService(NotificationManager::class.java)

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Yangilanishlar", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "MROS Keyboard yangi versiyalari haqida"
        manager(ctx)?.createNotificationChannel(ch)
    }

    private fun post(ctx: Context, id: Int, title: String, text: String, open: String) {
        if (!canPost(ctx)) return
        try {
            ensureChannel(ctx)
            val i = Intent(ctx, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN, open)
            val pi = PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setColor(0xFF0A84FF.toInt())
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            manager(ctx)?.notify(id, n)
        } catch (_: Exception) {
        }
    }

    fun newVersion(ctx: Context, r: Updater.Release) {
        val first = r.notes.lineSequence().map { it.trim().trimStart('-', '*', '•', ' ') }.firstOrNull { it.isNotBlank() }
        post(ctx, 1, "Yangi versiya: ${r.tag}", first ?: "Yangilash uchun bosing", OPEN_UPDATE)
    }

    fun updated(ctx: Context, version: String) {
        post(ctx, 2, "MROS Keyboard yangilandi ✓", "Versiya $version. Nima yangi va qanday ishlatish — koʻrish uchun bosing.", OPEN_GUIDE)
    }

    fun installFailed(ctx: Context, msg: String) {
        post(ctx, 3, "Yangilanish oʻrnatilmadi", msg, OPEN_UPDATE)
    }
}
