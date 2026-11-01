package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// GPS run recording: the maths on recorded points (distance, filtering, splits) and where routes are kept.
// RunTracker holds the run being recorded and RunTrackingService feeds it locations.

/** One GPS fix of a recorded route. [seg] goes up after each pause; distance isn't counted across a pause. */
data class GpsPoint(
    @SerializedName("a") val lat: Double = 0.0,
    @SerializedName("o") val lon: Double = 0.0,
    /** Epoch ms of the fix. */
    @SerializedName("t") val time: Long = 0,
    @SerializedName("s") val seg: Int = 0,
)

private const val EARTH_RADIUS_KM = 6371.0088

/** Great-circle distance between two points in km. */
fun distanceKm(a: GpsPoint, b: GpsPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Fixes less accurate than this (metres) are dropped. */
const val MAX_ACCURACY_M = 30f

/** Faster than this (m/s, about 43 km/h) between two fixes is a GPS jump, not running. */
private const val MAX_SPEED_MS = 12.0

/** Moves shorter than this (metres) are GPS jitter while standing; they'd add up to phantom distance. */
private const val MIN_MOVE_M = 3.0

/**
 * Whether fix [p] (with [accuracyM], null when unknown) belongs on the route after [prev]: accurate enough, not a
 * jump no runner could make, and far enough from the last point to be movement rather than jitter. The first
 * point of a segment only has to be accurate.
 */
fun acceptPoint(prev: GpsPoint?, p: GpsPoint, accuracyM: Float?): Boolean {
    if (accuracyM != null && accuracyM > MAX_ACCURACY_M) return false
    if (prev == null || prev.seg != p.seg) return true
    val m = distanceKm(prev, p) * 1000
    if (m < MIN_MOVE_M) return false
    val s = (p.time - prev.time) / 1000.0
    return s > 0 && m / s <= MAX_SPEED_MS
}

// ---------- auto-pause ----------

/** Slower than this (m/s, about 2.5 km/h) counts as standing still. */
const val STOP_SPEED_MS = 0.7

/** Faster than this (m/s, about 4.3 km/h) counts as moving again; above STOP_SPEED_MS so it doesn't flicker. */
const val MOVE_SPEED_MS = 1.2

/** Standing still this long pauses the run. */
const val STOP_AFTER_MS = 6_000L

/** Moving this many fixes in a row resumes it. */
const val MOVE_FIXES = 2

/** Auto-pause memory between fixes: since when the runner has been slow, and how many fast fixes in a row. */
data class AutoPauseState(val slowSince: Long? = null, val fastFixes: Int = 0)

/**
 * The result of one fix: the new [state], [pauseFrom] set when the run should pause (the time the runner stopped,
 * so standing still isn't counted as moving time), and [resume] when an auto-paused run should carry on.
 */
data class AutoPauseStep(val state: AutoPauseState, val pauseFrom: Long? = null, val resume: Boolean = false)

/**
 * Auto-pause: a running run pauses after STOP_AFTER_MS below STOP_SPEED_MS, an auto-paused one resumes after
 * MOVE_FIXES fixes above MOVE_SPEED_MS. [speedMs] is the speed of the fix at [now].
 */
fun autoPauseStep(state: AutoPauseState, autoPaused: Boolean, speedMs: Double, now: Long): AutoPauseStep =
    if (!autoPaused) {
        if (speedMs < STOP_SPEED_MS) {
            val since = state.slowSince ?: now
            if (now - since >= STOP_AFTER_MS) AutoPauseStep(AutoPauseState(), pauseFrom = since)
            else AutoPauseStep(AutoPauseState(slowSince = since))
        } else {
            AutoPauseStep(AutoPauseState())
        }
    } else {
        val fast = if (speedMs >= MOVE_SPEED_MS) state.fastFixes + 1 else 0
        if (fast >= MOVE_FIXES) AutoPauseStep(AutoPauseState(), resume = true)
        else AutoPauseStep(AutoPauseState(fastFixes = fast))
    }

/** Distance of a route in km, without the gaps between segments. */
fun routeDistanceKm(points: List<GpsPoint>): Double =
    (1 until points.size).sumOf { i -> if (points[i].seg == points[i - 1].seg) distanceKm(points[i - 1], points[i]) else 0.0 }

/** One split of a run: [km] long (the last one may be shorter), run in [seconds]. */
data class RunSplit(val number: Int, val km: Double, val seconds: Int)

/**
 * Splits of [points] every [splitKm] (1 for kilometres, 1.609 for miles). Time between segments (pauses) isn't
 * counted; the time a split boundary is crossed is interpolated between the fixes around it. A last split shorter
 * than 10 m is left out.
 */
fun splits(points: List<GpsPoint>, splitKm: Double): List<RunSplit> {
    if (points.size < 2 || splitKm <= 0) return emptyList()
    // Cumulative distance and moving time at each point.
    val dist = DoubleArray(points.size)
    val time = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val same = points[i].seg == points[i - 1].seg
        dist[i] = dist[i - 1] + if (same) distanceKm(points[i - 1], points[i]) else 0.0
        time[i] = time[i - 1] + if (same) (points[i].time - points[i - 1].time) / 1000.0 else 0.0
    }
    val total = dist.last()
    val out = ArrayList<RunSplit>()
    var lastTime = 0.0
    var k = 1
    var i = 1
    while (k * splitKm <= total) {
        val target = k * splitKm
        while (dist[i] < target) i++
        val span = dist[i] - dist[i - 1]
        val f = if (span > 0) (target - dist[i - 1]) / span else 1.0
        val t = time[i - 1] + f * (time[i] - time[i - 1])
        out.add(RunSplit(k, splitKm, Math.round(t - lastTime).toInt()))
        lastTime = t
        k++
    }
    val rest = total - (k - 1) * splitKm
    if (rest >= 0.01) out.add(RunSplit(k, rest, Math.round(time.last() - lastTime).toInt()))
    return out
}

/** Recorded routes, one file per route in filesDir/routes, kept out of data.json so it stays small. */
object RouteStore {
    private val gson = Gson()
    private val io = Executors.newSingleThreadExecutor()
    private var dir: File? = null
    private val type = object : TypeToken<List<GpsPoint>>() {}.type
    /** Routes saved or loaded this session; a route is readable right after saving, before the file is written. */
    private val cache = HashMap<String, List<GpsPoint>>()

    fun init(filesDir: File) {
        dir = File(filesDir, "routes").apply { mkdirs() }
    }

    private fun file(id: String): File? = dir?.let { File(it, "$id.json") }

    /** Saves a route; the file is written in the background. Main thread only. */
    fun save(id: String, points: List<GpsPoint>) {
        cache[id] = points
        val f = file(id) ?: return
        val json = gson.toJson(points)
        io.execute { runCatching { f.writeText(json) } }
    }

    /** The route saved as [id], null if there is none. Main thread only. */
    fun load(id: String): List<GpsPoint>? = cache[id] ?: file(id)?.takeIf { it.exists() }
        ?.let { f -> runCatching { gson.fromJson<List<GpsPoint>>(f.readText(), type) }.getOrNull() }
        ?.also { cache[id] = it }

    fun delete(id: String) {
        cache.remove(id)
        val f = file(id) ?: return
        io.execute { f.delete() }
    }
}
