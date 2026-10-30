package com.tooheavy.app.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.tooheavy.app.BuildConfig
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

/**
 * Self-update from the GitHub releases built by .github/workflows/release.yml.
 * On start the latest release is checked; a newer APK is downloaded in the background (after asking first on
 * metered networks) and the user is offered to install it. Android always shows its own install confirmation,
 * and only accepts the update if it is signed with the same key as the installed app.
 */
object Updater {
    private const val LATEST_RELEASE = "https://api.github.com/repos/AmonGruenwald/TooHeavy/releases/latest"
    private const val USER_AGENT = "TooHeavy/${BuildConfig.VERSION_NAME} (Android)"

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

    private val scope: CoroutineScope = MainScope()
    private var job: Job? = null
    private lateinit var app: Context

    /** Looks for a newer release. Automatic checks run once per app start and fail silently; [manual] ones show every result. */
    fun check(context: Context, manual: Boolean = false) {
        app = context.applicationContext
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
                app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered -> {
                    state = State.Available(release)
                    showDialog = true
                }
                else -> fetch(release)
            }
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

    /** Makes sure the file is a newer build of this app before offering it. */
    private suspend fun verify(release: Release, apk: File) {
        val info = withContext(Dispatchers.IO) { app.packageManager.getPackageArchiveInfo(apk.path, 0) }
        if (info == null || info.packageName != app.packageName || PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            apk.delete()
            state = State.Failed("The downloaded file is not a valid update.")
            return
        }
        state = State.Ready(release, apk)
        showDialog = true
    }
}
