package com.tooheavy.app.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors

/** A run being recorded with GPS for set [setId] of workout exercise [weId]. */
data class ActiveTrack(
    val weId: String = "",
    val setId: String = "",
    val points: List<GpsPoint> = emptyList(),
    /** Distance of [points] in km, kept up to date as fixes come in. */
    val distanceKm: Double = 0.0,
    /** Segment new points go into; it goes up on each resume, so pauses aren't counted as distance. */
    val seg: Int = 0,
    /** Moving time before the current stretch, in ms. */
    val movedMs: Long = 0,
    /** When the current stretch started (epoch ms), null while paused. */
    val resumedAt: Long? = null,
    /** Accuracy of the last fix in metres, null before the first fix. */
    val lastAccuracy: Float? = null,
    /** When this was last written to disk, to recover the moving time if the app is closed while recording. */
    val savedAt: Long = 0,
) {
    val paused: Boolean get() = resumedAt == null

    fun movingMs(now: Long): Long = movedMs + (resumedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Pace so far in seconds per km, null before 50 m. */
    fun paceSecPerKm(now: Long): Double? = if (distanceKm < 0.05) null else movingMs(now) / 1000.0 / distanceKm

    /** Pace over the last [windowMs] of points in seconds per km, null without enough movement. */
    fun currentPaceSecPerKm(windowMs: Long = 30_000): Double? {
        val last = points.lastOrNull() ?: return null
        val recent = points.filter { it.seg == last.seg && last.time - it.time <= windowMs }
        if (recent.size < 2) return null
        val km = routeDistanceKm(recent)
        val s = (recent.last().time - recent.first().time) / 1000.0
        return if (km < 0.02 || s <= 0) null else s / km
    }
}

/**
 * The run being recorded, if any. RunTrackingService feeds it GPS fixes while it isn't paused; finishing it fills
 * the set with distance and moving time and keeps the route (RouteStore). It is saved to filesDir/routes/active.json
 * every few seconds, so a run survives the app being closed: it comes back paused, to resume or finish.
 * Compose-observable; main thread only.
 */
object RunTracker {
    private val gson = Gson()
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var appContext: Context
    private lateinit var file: File
    private var lastSave = 0L

    var track by mutableStateOf<ActiveTrack?>(null)
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        RouteStore.init(context.filesDir)
        file = File(context.filesDir, "routes/active.json")
        if (file.exists()) {
            runCatching { gson.fromJson(file.readText(), ActiveTrack::class.java) }.getOrNull()?.let { t ->
                // Recording stopped with the app: count the time up to the last save, then wait paused.
                val moved = t.movedMs + (t.resumedAt?.let { (t.savedAt - it).coerceAtLeast(0) } ?: 0)
                track = t.copy(movedMs = moved, resumedAt = null, points = t.points.orEmpty())
            }
        }
    }

    /** Whether the run of workout exercise [weId] is being recorded (or paused). */
    fun isFor(weId: String): Boolean = track?.weId == weId

    /** Starts recording into set [setId] of workout exercise [weId]. Needs the location permission. */
    fun start(weId: String, setId: String) {
        if (track != null) return
        track = ActiveTrack(weId = weId, setId = setId, resumedAt = System.currentTimeMillis())
        save(force = true)
        startService()
    }

    fun pause() {
        val t = track ?: return
        if (t.paused) return
        val now = System.currentTimeMillis()
        track = t.copy(movedMs = t.movingMs(now), resumedAt = null)
        save(force = true)
        stopService()
    }

    fun resume() {
        val t = track ?: return
        if (!t.paused) return
        // A new segment, so the way from where the pause started to where it ends isn't counted.
        track = t.copy(resumedAt = System.currentTimeMillis(), seg = if (t.points.any { it.seg == t.seg }) t.seg + 1 else t.seg)
        save(force = true)
        startService()
    }

    /** A GPS fix from RunTrackingService. */
    fun onLocation(lat: Double, lon: Double, accuracyM: Float?) {
        val t = track ?: return
        if (t.paused) return
        val p = GpsPoint(lat, lon, System.currentTimeMillis(), t.seg)
        val prev = t.points.lastOrNull()
        track = if (acceptPoint(prev, p, accuracyM)) {
            val added = if (prev != null && prev.seg == p.seg) distanceKm(prev, p) else 0.0
            t.copy(points = t.points + p, distanceKm = t.distanceKm + added, lastAccuracy = accuracyM)
        } else {
            t.copy(lastAccuracy = accuracyM)
        }
        save(force = false)
    }

    /**
     * Stops recording and puts the result into the set it was for: distance, moving time and the route, and marks
     * it done. Does nothing to the workout if the set is gone.
     */
    fun finish() {
        val t = track ?: return
        val now = System.currentTimeMillis()
        stopService()
        track = null
        deleteFile()
        val seconds = (t.movingMs(now) / 1000).toInt().takeIf { it > 0 }
        val routeId = if (t.points.size >= 2) newId().also { RouteStore.save(it, t.points) } else null
        val km = t.distanceKm.takeIf { it > 0 }?.let { Math.round(it * 1000) / 1000.0 }
        Repo.updateActive { w ->
            w.copy(exercises = w.exercises.map { we ->
                if (we.id != t.weId) return@map we
                // The set may have been deleted while recording: then the run gets a new one.
                val sets = if (we.sets.any { it.id == t.setId }) we.sets else we.sets + SetEntry(id = t.setId)
                we.copy(sets = sets.map { s ->
                    if (s.id != t.setId) s
                    else s.copy(distanceKm = km, seconds = seconds, routeId = routeId, timerStartedAt = null, completed = true)
                })
            })
        }
    }

    /** Stops recording and throws the run away. */
    fun discard() {
        if (track == null) return
        stopService()
        track = null
        deleteFile()
    }

    /** Deletes the saved run after any save still being written, so it can't come back. */
    private fun deleteFile() {
        val f = file
        io.execute { f.delete() }
    }

    private fun save(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 10_000) return
        lastSave = now
        val t = track ?: return
        val json = gson.toJson(t.copy(savedAt = now))
        val f = file
        io.execute { runCatching { f.parentFile?.mkdirs(); f.writeText(json) } }
    }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(appContext, Intent(appContext, RunTrackingService::class.java)) }
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, RunTrackingService::class.java))
    }
}
