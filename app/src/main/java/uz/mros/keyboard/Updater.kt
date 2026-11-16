package uz.mros.keyboard

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ilova ichidan yangilanish: GitHub Releases'dan oxirgi versiyani tekshiradi,
 * APK'ni yuklab, tizim o'rnatuvchisi (PackageInstaller) orqali o'rnatadi.
 * Manba repo build paytida BuildConfig.UPDATE_REPO ga yoziladi (ochiq repo bo'lishi kerak).
 */
object Updater {

    class Release(
        val tag: String,
        val version: Long,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val size: Long,
    )

    val repo: String get() = BuildConfig.UPDATE_REPO

    fun currentVersion(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    } catch (_: Exception) {
        0L
    }

    fun currentVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    fun versionFromTag(tag: String): Long =
        Regex("(\\d+)\\D*$").find(tag)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Tarmoq so'rovi — asosiy oqimda chaqirmang. */
    @Throws(IOException::class)
    fun fetchLatest(): Release {
        if (repo.isBlank()) throw IOException("Yangilanish manbasi sozlanmagan")
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MROS-Keyboard")
            val code = conn.responseCode
            if (code == 404) throw IOException("Releases topilmadi. Repo yopiq bo'lsa, ochiq releases repo kerak.")
            if (code == 403) throw IOException("GitHub vaqtincha cheklov qo'ydi, birozdan keyin urinib ko'ring.")
            if (code != 200) throw IOException("GitHub javobi: $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(text)
            val tag = o.optString("tag_name")
            var url = ""
            var size = 0L
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (url.isEmpty()) throw IOException("Oxirgi releaseda APK fayl yo'q")
            val title = o.optString("name").ifBlank { tag }
            return Release(tag, versionFromTag(tag), title, o.optString("body"), url, size)
        } finally {
            conn.disconnect()
        }
    }

    /** Natijani saqlab qo'yadi (sozlamalar va qo'llanma uchun). */
    fun remember(ctx: Context, r: Release) {
        val p = Prefs(ctx)
        p.lastUpdateCheck = System.currentTimeMillis()
        p.latestVersion = r.version
        p.latestTag = r.tag
        p.latestNotes = r.notes
    }

    /** Fon tekshiruvi: yangi versiya bo'lsa bildirishnoma yuboradi. */
    fun backgroundCheck(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.updateNotify || repo.isBlank()) return
        val r = fetchLatest()
        remember(ctx, r)
        if (r.version > currentVersion(ctx) && r.version != p.notifiedVersion) {
            Notifier.newVersion(ctx, r)
            p.notifiedVersion = r.version
        }
    }

    /**
     * APK'ni yuklab, o'rnatish sessiyasini ochadi. Oxirida tizim "O'rnatish" oynasini ko'rsatadi.
     * Bloklovchi funksiya — fon oqimida chaqiring.
     */
    @Throws(Exception::class)
    fun install(ctx: Context, r: Release, progress: (Int) -> Unit) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val conn = URL(r.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "MROS-Keyboard")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else r.size
            conn.inputStream.use { input ->
                session.openWrite("base.apk", 0, if (total > 0) total else -1).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (pct != last) {
                            last = pct
                            progress(pct)
                        }
                    }
                    session.fsync(out)
                }
            }
            conn.disconnect()
            val intent = Intent(ctx, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            try {
                session.abandon()
            } catch (_: Exception) {
            }
            throw e
        } finally {
            session.close()
        }
    }
}

/** O'rnatish natijasi: tasdiqlash oynasini ochadi yoki xatoni bildiradi. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (_: Exception) {
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Nomaʼlum xato"
                Notifier.installFailed(context, msg)
            }
        }
    }

    companion object {
        const val ACTION = "uz.mros.keyboard.INSTALL_STATUS"
    }
}

/** Ilova yangilangandan keyin: "Yangilandi" bildirishnomasi + fon tekshiruvini qayta rejalash. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.updated(context, Updater.currentVersionName(context))
        UpdateJobService.ensureScheduled(context)
    }
}

/** Har 12 soatda yangilanishni tekshiruvchi fon vazifasi. */
class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                Updater.backgroundCheck(app)
            } catch (_: Exception) {
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4201

        fun ensureScheduled(ctx: Context) {
            try {
                val js = ctx.getSystemService(JobScheduler::class.java) ?: return
                if (!Prefs(ctx).updateNotify || Updater.repo.isBlank()) {
                    js.cancel(JOB_ID)
                    return
                }
                if (js.getPendingJob(JOB_ID) != null) return
                val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(12L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build()
                js.schedule(job)
            } catch (_: Exception) {
            }
        }
    }
}
