package com.tooheavy.app.data

import java.util.Locale
import kotlin.math.roundToInt

// Running: distance-tracked exercises (distance and time per set, pace from both) and a running plan of
// weeks the user writes or pastes in. Everything here is pure, so it can be unit tested.

/** Id of the bundled outdoor run (exercises_extra.json); planned runs log with it unless they say otherwise. */
const val RUNNING_EXERCISE_ID = "Running"

const val KM_PER_MILE = 1.609344

/** Library cardio exercises that log distance and time instead of weight and reps. */
private val distanceExercises = setOf(
    "Running_Treadmill", "Jogging_Treadmill", "Walking_Treadmill", "Trail_Running_Walking",
    "Bicycling", "Bicycling_Stationary", "Recumbent_Bike", "Rowing_Stationary", "Elliptical_Trainer",
)

/** [this] with the tracking the library gives it: distance for the bundled runs, rides and rows. */
fun Exercise.withDefaultTracking(): Exercise =
    if (tracking == null && id in distanceExercises) copy(tracking = Tracking.DISTANCE) else this

// ---------- units ----------

/** Distance unit that goes with the weight unit: miles with pounds, else kilometres. */
fun distanceUnit(weightUnit: String): String = if (weightUnit == "lb") "mi" else "km"

/** Kilometres in [unit] ("km" or "mi"). */
fun kmIn(km: Double, unit: String): Double = if (unit == "mi") km / KM_PER_MILE else km

/** A distance in [unit] in kilometres. */
fun kmFrom(value: Double, unit: String): Double = if (unit == "mi") value * KM_PER_MILE else value

/** 5.0 -> "5", 5.25 -> "5.25", 21.0975 -> "21.1" (in [unit], without the unit). */
fun formatDistanceValue(km: Double, unit: String): String {
    val v = kmIn(km, unit)
    val decimals = if (v >= 20) 1 else 2
    return String.format(Locale.US, "%.${decimals}f", v).trimEnd('0').trimEnd('.')
}

/** "5 km", "3.1 mi". */
fun formatDistance(km: Double, unit: String): String = "${formatDistanceValue(km, unit)} $unit"

/** Pace per [unit], e.g. "5:30 /km"; null without a distance and time. */
fun formatPace(seconds: Int?, km: Double?, unit: String): String? {
    if (seconds == null || seconds <= 0 || km == null || km <= 0) return null
    val perUnit = (seconds / kmIn(km, unit)).roundToInt()
    return "${formatClock(perUnit * 1000L)} /$unit"
}

/** "5 km · 27:30 · 5:30 /km" for a set of a distance or timed exercise; null for weight-and-reps sets. */
fun SetEntry.cardioText(unit: String): String? {
    val km = distanceKm?.takeIf { it > 0 }
    if (km == null && seconds == null) return null
    return listOfNotNull(km?.let { formatDistance(it, unit) }, seconds?.let { formatSetTime(it) }, formatPace(seconds, km, unit))
        .joinToString(" · ")
}

// ---------- running plan ----------

/** A running plan the user follows week by week. Week 1 starts on [startDate] (a Monday, "yyyy-MM-dd"). */
data class RunPlan(
    val id: String = newId(),
    val name: String = "Running plan",
    val startDate: String = "",
    val weeks: List<RunWeek> = emptyList(),
)

data class RunWeek(
    val id: String = newId(),
    val runs: List<PlannedRun> = emptyList(),
)

/** One run of a plan, with optional targets. */
data class PlannedRun(
    val id: String = newId(),
    /** What to do, e.g. "Easy run 5 km" or "6 × 400 m at 5K pace". */
    val name: String = "Run",
    val notes: String = "",
    val distanceKm: Double? = null,
    val seconds: Int? = null,
    /** Exercise the run is logged with. */
    val exerciseId: String = RUNNING_EXERCISE_ID,
    /** Workout that completed the run, or DONE_BY_HAND when it was ticked off without one. */
    val doneWorkoutId: String? = null,
    val skipped: Boolean = false,
) {
    val done: Boolean get() = doneWorkoutId != null
    val open: Boolean get() = !done && !skipped

    /** "5 km · 30:00", "" without targets. */
    fun targetText(unit: String): String =
        listOfNotNull(distanceKm?.let { formatDistance(it, unit) }, seconds?.let { formatSetTime(it) }).joinToString(" · ")

    companion object {
        const val DONE_BY_HAND = "manual"
    }
}

/** Week index (0-based) [date] falls in; negative before the plan starts, past the end after it. */
fun RunPlan.weekIndexOn(date: String): Int = Math.floorDiv(daysBetween(startDate, date), 7)

/** The next run to do on [date]: the first open run of the current week or a later one; null once none is left. */
fun RunPlan.nextRun(date: String): Pair<Int, PlannedRun>? {
    val from = weekIndexOn(date).coerceAtLeast(0)
    for (w in from until weeks.size) {
        weeks[w].runs.firstOrNull { it.open }?.let { return w to it }
    }
    return null
}

fun RunPlan.findRun(runId: String): PlannedRun? = weeks.firstNotNullOfOrNull { w -> w.runs.firstOrNull { it.id == runId } }

/** [this] with run [runId] changed by [transform]. */
fun RunPlan.updateRun(runId: String, transform: (PlannedRun) -> PlannedRun): RunPlan =
    copy(weeks = weeks.map { w -> w.copy(runs = w.runs.map { if (it.id == runId) transform(it) else it }) })

/** Runs done and runs in the plan. */
fun RunPlan.progress(): Pair<Int, Int> = weeks.sumOf { w -> w.runs.count { it.done } } to weeks.sumOf { it.runs.size }

/** Monday of the week containing [date]. */
fun mondayOf(date: String): String = shiftDate(date, -((dayOfWeek(date) + 5) % 7))

// ---------- plan text ----------

private val weekHeader = Regex("""^(?:week|wk|woche)\s*(\d+)\b\s*[:.)\-–—]*\s*(.*)$""", RegexOption.IGNORE_CASE)
private val bullet = Regex("""^(?:[-*•·]+|\d+[.)])\s*""")
private val restLine = Regex("""^(?:rest\b.*|off|day off|cross[- ]?train(?:ing)?\b.*)$""", RegexOption.IGNORE_CASE)
private val kmPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(km|k|kilometers?|kilometres?|mi|miles?)\b""", RegexOption.IGNORE_CASE)
// Not "5:30 min/km": a pace isn't a duration.
private val minPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(h|hrs?|hours?|min|mins|minutes?)\b(?!\s*/)""", RegexOption.IGNORE_CASE)
private val repeatPattern = Regex("""(?:^|[\s\d])[x×]\s*\d|\brepeats?\b|\breps?\b""", RegexOption.IGNORE_CASE)

/**
 * Runs from a line of a plan: the line is the run's name, the longest distance in it ("5 km", "10k", "3 miles")
 * its target distance, and a single duration ("30 min", "1 h") its target time. Intervals ("8 × 1 min") get no
 * target time, since no one number is the run's length. Rest days give null.
 */
fun parsePlannedRun(line: String): PlannedRun? {
    val text = line.trim().replace(bullet, "").trim()
    if (text.isEmpty() || restLine.matches(text)) return null
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val km = kmPattern.findAll(text).mapNotNull { m ->
        val v = num(m.groupValues[1]) ?: return@mapNotNull null
        if (m.groupValues[2].lowercase(Locale.US).startsWith("mi")) v * KM_PER_MILE else v
    }.maxOrNull()
    val times = minPattern.findAll(text).mapNotNull { m ->
        val v = num(m.groupValues[1]) ?: return@mapNotNull null
        (if (m.groupValues[2].lowercase(Locale.US).startsWith("h")) v * 3600 else v * 60).roundToInt()
    }.toList()
    val seconds = times.singleOrNull()?.takeIf { !repeatPattern.containsMatchIn(text) }
    return PlannedRun(name = text, distanceKm = km?.let { Math.round(it * 100) / 100.0 }, seconds = seconds)
}

/**
 * Weeks from a plan written as text, e.g.
 * ```
 * Week 1
 * Easy run 3 km
 * Run 1 min, walk 90 s × 8
 * Week 2: 4 km easy; 5 km long run
 * ```
 * "Week N" lines start a week (runs may follow on the same line, separated by ";"); every other line is a run,
 * and rest days are skipped. Without any "Week" line, blank lines separate the weeks.
 */
fun parseRunPlan(text: String): List<RunWeek> {
    val lines = text.lines().map { it.trim() }
    val hasHeaders = lines.any { weekHeader.matches(it) }
    val weeks = ArrayList<MutableList<PlannedRun>>()
    var current: MutableList<PlannedRun>? = null
    fun newWeek() = ArrayList<PlannedRun>().also { weeks.add(it); current = it }
    for (line in lines) {
        val header = weekHeader.matchEntire(line)
        when {
            header != null -> {
                val week = newWeek()
                header.groupValues[2].split(';').mapNotNullTo(week) { parsePlannedRun(it) }
            }
            line.isEmpty() -> if (!hasHeaders) current = null
            else -> (current ?: newWeek()).let { week -> parsePlannedRun(line)?.let { week.add(it) } }
        }
    }
    return weeks.map { RunWeek(runs = it) }
}
