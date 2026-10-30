package com.tooheavy.app.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.tooheavy.app.BuildConfig
import com.tooheavy.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Self-update from the GitHub releases built by .github/workflows/release.yml.
 *
 * In the app: the latest release is checked on start; a newer APK is downloaded in the background (after asking
 * first on metered networks) and the user is offered to install it through the system installer.
 *
 * In the background: [UpdateJobService] checks once a day. On unmetered networks it downloads the update; on
 * Android 12+ it then installs it without a confirmation screen while the app is closed and no workout is in
 * progress. Otherwise a notification says the update is ready. Android only accepts updates signed with the
 * same key as the installed app.
 */
object Updater {
    private const val LATEST_RELEASE = "https://api.github.com/repos/AmonGruenwald/TooHeavy/releases/latest"
    private const val USER_AGENT = "TooHeavy/${BuildConfig.VERSION_NAME} (Android)"
    private const val JOB_ID = 1001
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001
    const val ACTION_INSTALL_STATUS = "com.tooheavy.app.INSTALL_STATUS"

    data class Release(val version: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        /** Newer release found while on a metered network; waits for [download]. */
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val apk: File) : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Whether the update dialog is visible. Automatic checks only show it once there is something to act on. */
    var showDialog by mutableStateOf(false)
        private set

    /** True while an activity of the app is on screen (set by MainActivity); silent installs wait until it isn't. */
    @Volatile
    var appVisible = false

    private val scope: CoroutineScope = MainScope()
    private var job: Job? = null
    private lateinit var app: Context

    /** Looks for a newer release. Automatic checks run once per app start and fail silently; [manual] ones show every result. */
    fun check(context: Context, manual: Boolean = false) {
        app = context.applicationContext
        cancelNotification(app)
        if (manual) showDialog = true
        if (job?.isActive == true || (!manual && state != State.Idle)) return
        job = scope.launch {
            state = State.Checking
            val release = try {
                latestRelease()
            } catch (e: Exception) {
                state = State.Failed(e.message ?: "No connection")
                return@launch
            }
            if (release == null) {
                withContext(Dispatchers.IO) { updatesDir().deleteRecursively() }
                state = State.UpToDate
                return@launch
            }
            val cached = apkFile(release)
            when {
                cached.length() == release.size -> verify(release, cached)
                isMetered() -> {
                    state = State.Available(release)
                    showDialog = true
                }
                else -> fetch(release)
            }
        }
    }

    /** Schedules the daily background check; does nothing if it is already scheduled. */
    fun scheduleDailyCheck(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(6))
                .setPersisted(true)
                .build(),
        )
    }

    /** Background check from [UpdateJobService]; [done] runs when it has finished. */
    fun checkInBackground(context: Context, done: () -> Unit) {
        app = context.applicationContext
        if (job?.isActive == true) {
            done()
            return
        }
        job = scope.launch {
            try {
                backgroundCheck()
            } finally {
                done()
            }
        }
    }

    fun cancelBackgroundCheck() {
        job?.cancel()
    }

    private suspend fun backgroundCheck() {
        val release = try {
            latestRelease()
        } catch (e: Exception) {
            return // try again tomorrow
        } ?: return
        var apk = apkFile(release)
        if (apk.length() != release.size) {
            if (isMetered()) {
                state = State.Available(release)
                showDialog = true
                notify(app, "TooHeavy ${release.version} is available", "Tap to download the update (${release.size / 1_000_000} MB).")
                return
            }
            state = State.Downloading(release, 0f)
            apk = try {
                withContext(Dispatchers.IO) { downloadApk(release) }
            } catch (e: Exception) {
                state = State.Idle
                return
            }
        }
        if (!withContext(Dispatchers.IO) { isValidUpdate(apk) }) {
            apk.delete()
            state = State.Idle
            return
        }
        state = State.Ready(release, apk)
        showDialog = true
        when {
            appVisible -> Unit // the dialog shows right away
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && withContext(Dispatchers.IO) { canInstallSilently() } ->
                installSilently(release, apk)
            else -> notifyReady(app, release.version)
        }
    }

    /** Starts the download of an [State.Available] release. */
    fun download() {
        val s = state as? State.Available ?: return
        job = scope.launch { fetch(s.release) }
    }

    /** Opens the system installer. The first time, Android asks to allow installing apps from TooHeavy. */
    fun install(context: Context) {
        val s = state as? State.Ready ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", s.apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun dismiss() {
        showDialog = false
    }

    // ---------- silent install (Android 12+) ----------

    /**
     * Android 12+ lets an app update itself without a confirmation screen once the user has allowed it to install
     * apps (the first in-app update asks). Only done while the app is closed, so a workout isn't interrupted.
     */
    private fun canInstallSilently(): Boolean =
        !appVisible && app.packageManager.canRequestPackageInstalls() && !Repo.hasActiveWorkout(app)

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun installSilently(release: Release, apk: File) {
        try {
            withContext(Dispatchers.IO) {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(apk.length())
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    // Read after the update by onPackageReplaced, in the new version's process.
                    silentMarker(app).writeText(release.version)
                    val status = PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        Intent(app, UpdateReceiver::class.java).setAction(ACTION_INSTALL_STATUS),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(status.intentSender)
                }
            }
        } catch (e: Exception) {
            silentMarker(app).delete()
            notifyReady(app, release.version)
        }
    }

    /** Result of a silent install session. On success the app is replaced and this process ends. */
    fun onInstallStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_SUCCESS) return
        // Android wants a confirmation after all, or the install failed: abandon it and let the user install in the app.
        val marker = silentMarker(context)
        val version = marker.takeIf { it.exists() }?.readText()
        marker.delete()
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        if (version != null) notifyReady(context, version)
    }

    /** Runs in the new version after any update; announces it if it was installed in the background. */
    fun onPackageReplaced(context: Context) {
        val marker = silentMarker(context)
        if (!marker.exists()) return
        marker.delete()
        notify(context, "TooHeavy updated to ${BuildConfig.VERSION_NAME}", "Installed in the background. Tap to see what's new.")
    }

    private fun silentMarker(context: Context) = File(context.noBackupFilesDir, "silent-update")

    // ---------- notifications ----------

    private fun notifyReady(context: Context, version: String) =
        notify(context, "TooHeavy $version is ready", "Tap to install the update. Your data is kept.")

    private fun notify(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    // ---------- download ----------

    private fun isMetered() = app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    private fun updatesDir() = File(app.cacheDir, "updates")

    private fun apkFile(release: Release) = File(updatesDir(), "TooHeavy-${release.version}.apk")

    /** Same scheme as app/build.gradle.kts: 1.2.3 -> 10203. */
    private fun versionCode(version: String): Int? =
        version.split(".").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }
            ?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

    /** The latest release if it is newer than this build and has an APK attached, else null. */
    private suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        val conn = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        val status = conn.responseCode
        if (status == 404) return@withContext null // no release published yet
        if (status !in 200..299) throw IOException("Server error ($status)")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val version = json.optString("tag_name").removePrefix("v")
        val code = versionCode(version) ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        Release(version, apk.getString("browser_download_url"), apk.optLong("size"))
    }

    private suspend fun fetch(release: Release) {
        state = State.Downloading(release, 0f)
        val apk = try {
            withContext(Dispatchers.IO) { downloadApk(release) }
        } catch (e: Exception) {
            state = State.Failed("Download failed: ${e.message ?: "no connection"}")
            return
        }
        verify(release, apk)
    }

    private fun downloadApk(release: Release): File {
        val dir = updatesDir()
        dir.deleteRecursively() // drop older or partial downloads
        dir.mkdirs()
        val part = File(dir, "download.part")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        if (conn.responseCode !in 200..299) throw IOException("server error (${conn.responseCode})")
        val total = release.size.coerceAtLeast(1)
        conn.inputStream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var shown = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = (done * 100 / total).toInt()
                    if (pct != shown) {
                        shown = pct
                        state = State.Downloading(release, pct / 100f)
                    }
                }
            }
        }
        if (part.length() != release.size) throw IOException("incomplete file")
        val apk = apkFile(release)
        if (!part.renameTo(apk)) throw IOException("could not save the file")
        return apk
    }

    /** Whether [apk] is a newer build of this app. */
    private fun isValidUpdate(apk: File): Boolean {
        val info = app.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        return info.packageName == app.packageName && PackageInfoCompat.getLongVersionCode(info) > BuildConfig.VERSION_CODE
    }

    /** Makes sure the file is a newer build of this app before offering it. */
    private suspend fun verify(release: Release, apk: File) {
        if (!withContext(Dispatchers.IO) { isValidUpdate(apk) }) {
            apk.delete()
            state = State.Failed("The downloaded file is not a valid update.")
            return
        }
        state = State.Ready(release, apk)
        showDialog = true
    }
}
