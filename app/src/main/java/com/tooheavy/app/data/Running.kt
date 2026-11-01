package com.tooheavy.app.data

import java.util.Locale
import kotlin.math.roundToInt

// Running: distance-tracked exercises (distance and time per set, pace from both) and a running plan of
// weeks the user builds run by run. Everything here is pure, so it can be unit tested.

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

/** A pace in seconds per km as "5:30" per [unit] ("km" or "mi"), "–" when there is none. */
fun paceText(secPerKm: Double?, unit: String): String {
    if (secPerKm == null || secPerKm <= 0 || secPerKm.isInfinite() || secPerKm.isNaN()) return "–"
    val perUnit = if (unit == "mi") secPerKm * KM_PER_MILE else secPerKm
    return formatClock(Math.round(perUnit) * 1000L)
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
    /** The user's note for the week, e.g. "Recovery week" or "Race on Sunday". */
    val note: String = "",
)

/** Kinds of run in a plan; a run's name is its kind's label unless it came with its own. */
object RunType {
    const val EASY = "easy"
    const val LONG = "long"
    const val TEMPO = "tempo"
    const val INTERVALS = "intervals"
    /** Unstructured speed play: bursts of faster running mixed into an easy run, by feel. */
    const val FARTLEK = "fartlek"
    const val RECOVERY = "recovery"
    const val RACE = "race"
    val all = listOf(EASY, LONG, TEMPO, INTERVALS, FARTLEK, RECOVERY, RACE)
    fun label(type: String?): String = when (type) {
        EASY -> "Easy run"
        LONG -> "Long run"
        TEMPO -> "Tempo run"
        INTERVALS -> "Intervals"
        FARTLEK -> "Fartlek"
        RECOVERY -> "Recovery run"
        RACE -> "Race"
        else -> "Run"
    }
}

/** "Tue · Easy run", or just the name for a run on any day. */
fun PlannedRun.title(): String = listOfNotNull(day?.let { weekdayShort(it) }, name).joinToString(" · ")

/** "Mon" .. "Sun" for 1..7. */
fun weekdayShort(day: Int): String = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrElse(day - 1) { "" }

/** [runs] with the ones on a weekday in weekday order; runs without a day keep their place after them. */
fun sortRuns(runs: List<PlannedRun>): List<PlannedRun> = runs.sortedBy { it.day ?: 8 }

/** One run of a plan, with optional targets. */
data class PlannedRun(
    val id: String = newId(),
    /** What to do: the RunType label, e.g. "Easy run". */
    val name: String = "Run",
    /** RunType of the run, null for runs made before there were types. */
    val type: String? = null,
    /** Weekday 1 (Monday) to 7 (Sunday) the run is planned for, null for any day of the week. */
    val day: Int? = null,
    /** The user's note, e.g. "6 × 400 m at 5K pace, 90 s jog between". */
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

/**
 * [this] with [run] saved: replacing the run with its id wherever it is, or added to week [weekIndex]. The
 * runs of a week stay in weekday order.
 */
fun RunPlan.saveRun(weekIndex: Int, run: PlannedRun): RunPlan {
    val exists = weeks.any { w -> w.runs.any { it.id == run.id } }
    return copy(weeks = weeks.mapIndexed { i, w ->
        val runs = when {
            exists -> w.runs.map { if (it.id == run.id) run else it }
            i == weekIndex -> w.runs + run
            else -> w.runs
        }
        w.copy(runs = sortRuns(runs))
    })
}

/** [this] with the runs of week [weekIndex] copied, not done, into the week after it (if there is one). */
fun RunPlan.copyRunsToNextWeek(weekIndex: Int): RunPlan {
    val from = weeks.getOrNull(weekIndex) ?: return this
    if (weekIndex + 1 >= weeks.size) return this
    val copies = from.runs.map { it.copy(id = newId(), doneWorkoutId = null, skipped = false) }
    return copy(weeks = weeks.mapIndexed { i, w -> if (i == weekIndex + 1) w.copy(runs = sortRuns(w.runs + copies)) else w })
}

/** Runs done and runs in the plan. */
fun RunPlan.progress(): Pair<Int, Int> = weeks.sumOf { w -> w.runs.count { it.done } } to weeks.sumOf { it.runs.size }

/** Monday of the week containing [date]. */
fun mondayOf(date: String): String = shiftDate(date, -((dayOfWeek(date) + 5) % 7))
