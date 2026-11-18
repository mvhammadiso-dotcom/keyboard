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
import android.net.ConnectivityManager
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Avtomatik yangilanish: GitHub Releases'dan oxirgi versiyani tekshiradi,
 * APK'ni yuklab, tizim o'rnatuvchisi (PackageInstaller) orqali o'rnatadi.
 *
 * Manba: BuildConfig.UPDATE_REPO (workflow o'zi yozadi — odatda shu repo).
 * Repo yopiq bo'lsa — BuildConfig.UPDATE_TOKEN (faqat o'qish huquqli token) bilan kiradi.
 */
object Updater {

    class Release(
        val tag: String,
        val version: Long,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val assetApiUrl: String,
        val size: Long,
    )

    val repo: String get() = BuildConfig.UPDATE_REPO
    private val token: String get() = BuildConfig.UPDATE_TOKEN

    @Volatile
    private var busy = false

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

    /** "v10.0.5" -> 10*1_000_000 + 0*10_000 + 5 (build.gradle.kts dagi versionCode bilan bir xil formula) */
    fun versionFromTag(tag: String): Long {
        val m = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(tag)
        if (m != null) {
            val (a, b, c) = m.destructured
            return a.toLong() * 1_000_000 + b.toLong() * 10_000 + c.toLong()
        }
        return Regex("(\\d+)\\D*$").find(tag)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun open(url: String, accept: String, auth: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "MROS-Keyboard")
        if (auth && token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }

    /** Tarmoq so'rovi — asosiy oqimda chaqirmang. */
    @Throws(IOException::class)
    fun fetchLatest(): Release {
        if (repo.isBlank()) throw IOException("Yangilanish manbasi sozlanmagan")
        val conn = open("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json", true)
        try {
            val code = conn.responseCode
            if (code == 404) {
                throw IOException(
                    if (token.isBlank()) "Repo yopiq: reponi ochiq qiling yoki UPDATE_TOKEN qoʻshing"
                    else "Release topilmadi yoki token huquqi yetmaydi"
                )
            }
            if (code == 401) throw IOException("Token yaroqsiz yoki muddati tugagan")
            if (code == 403) throw IOException("GitHub vaqtincha cheklov qoʻydi, keyinroq urinib koʻring")
            if (code != 200) throw IOException("GitHub javobi: $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(text)
            val tag = o.optString("tag_name")
            var url = ""
            var api = ""
            var size = 0L
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                        api = a.optString("url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (url.isEmpty() && api.isEmpty()) throw IOException("Oxirgi releaseda APK fayl yoʻq")
            val title = o.optString("name").ifBlank { tag }
            return Release(tag, versionFromTag(tag), title, o.optString("body"), url, api, size)
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

    private fun isMetered(ctx: Context): Boolean = try {
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    } catch (_: Exception) {
        true
    }

    /**
     * Fon tekshiruvi. Yangi versiya bo'lsa:
     *  - "Avtomatik yangilash" yoqilgan va Wi-Fi bo'lsa — o'zi yuklab o'rnatadi;
     *  - aks holda bildirishnoma yuboradi.
     */
    fun backgroundCheck(ctx: Context) {
        val p = Prefs(ctx)
        if (repo.isBlank() || busy) return
        val r = fetchLatest()
        remember(ctx, r)
        if (r.version <= currentVersion(ctx)) return
        if (p.autoUpdate && p.autoInstalledVersion != r.version && !isMetered(ctx)) {
            p.autoInstalledVersion = r.version
            try {
                install(ctx, r, fromUser = false) {}
                return
            } catch (_: Exception) {
            }
        }
        if (p.updateNotify && r.version != p.notifiedVersion) {
            Notifier.newVersion(ctx, r)
            p.notifiedVersion = r.version
        }
    }

    /** Klaviatura ochilganda: oxirgi tekshiruvdan 6 soat o'tgan bo'lsa — fonda tekshiradi. */
    fun maybeCheckInBackground(ctx: Context) {
        val p = Prefs(ctx)
        if (repo.isBlank() || busy) return
        if (!p.autoUpdate && !p.updateNotify) return
        if (System.currentTimeMillis() - p.lastUpdateCheck < 6L * 60 * 60 * 1000) return
        p.lastUpdateCheck = System.currentTimeMillis()
        val app = ctx.applicationContext
        Thread {
            try {
                backgroundCheck(app)
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun openDownload(r: Release): HttpURLConnection {
        if (token.isBlank() || r.assetApiUrl.isBlank()) {
            return open(r.apkUrl, "application/octet-stream", false)
        }
        // Yopiq repo: API orqali, token bilan; keyin imzolangan havolaga (tokensiz) o'tamiz
        val c = open(r.assetApiUrl, "application/octet-stream", true)
        c.instanceFollowRedirects = false
        val code = c.responseCode
        if (code in 300..399) {
            val loc = c.getHeaderField("Location")
            c.disconnect()
            if (loc.isNullOrBlank()) throw IOException("Yuklab olish havolasi topilmadi")
            return open(loc, "application/octet-stream", false)
        }
        if (code != 200) {
            c.disconnect()
            throw IOException("Yuklab boʻlmadi: $code")
        }
        return c
    }

    /**
     * APK'ni yuklab, o'rnatish sessiyasini ochadi. Android 12+ da ilovaning o'zi o'rnatgan bo'lsa,
     * keyingi yangilanishlar so'rovsiz o'rnatiladi; aks holda tizim tasdiqlash oynasi chiqadi.
     * Bloklovchi funksiya — fon oqimida chaqiring.
     */
    @Throws(Exception::class)
    fun install(ctx: Context, r: Release, fromUser: Boolean, progress: (Int) -> Unit) {
        busy = true
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            val session = installer.openSession(id)
            try {
                val conn = openDownload(r)
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
                val intent = Intent(ctx, InstallReceiver::class.java)
                    .setAction(InstallReceiver.ACTION)
                    .putExtra(InstallReceiver.EXTRA_FROM_USER, fromUser)
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
        } finally {
            busy = false
        }
    }
}

/** O'rnatish natijasi: tasdiqlash oynasini ochadi (yoki bildirishnoma beradi) yoki xatoni bildiradi. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.getBooleanExtra(EXTRA_FROM_USER, false)) {
                    try {
                        context.startActivity(confirm)
                        return
                    } catch (_: Exception) {
                    }
                }
                Notifier.readyToInstall(context, confirm)
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
        const val EXTRA_FROM_USER = "from_user"
    }
}

/** Ilova yangilangandan keyin: "Yangilandi" bildirishnomasi + fon tekshiruvini qayta rejalash. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.updated(context, Updater.currentVersionName(context))
        UpdateJobService.ensureScheduled(context)
    }
}

/** Har 6 soatda yangilanishni tekshiruvchi fon vazifasi. */
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
                val p = Prefs(ctx)
                if ((!p.updateNotify && !p.autoUpdate) || Updater.repo.isBlank()) {
                    js.cancel(JOB_ID)
                    return
                }
                if (js.getPendingJob(JOB_ID) != null) return
                val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build()
                js.schedule(job)
            } catch (_: Exception) {
            }
        }
    }
}
