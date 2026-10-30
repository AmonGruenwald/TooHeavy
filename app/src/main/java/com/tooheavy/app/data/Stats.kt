package com.tooheavy.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Aggregations over finished workouts, shared by the dashboard, history and profile.

/** Local midnight of the Monday of the week containing [ms]. */
fun weekStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    // DAY_OF_WEEK: SUNDAY=1 .. SATURDAY=7 -> days since Monday
    c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7))
    return c.timeInMillis
}

fun addWeeks(ms: Long, n: Int): Long {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.timeInMillis = ms
    c.add(Calendar.WEEK_OF_YEAR, n)
    return c.timeInMillis
}

/** One Monday-to-Sunday week and the workouts started in it. */
class WeekBucket(val start: Long, val workouts: List<Workout>) {
    val label: String get() = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(start))
}

/** The last [weeks] weeks, oldest first, ending with the current week. */
fun weeklyBuckets(workouts: List<Workout>, weeks: Int, now: Long = System.currentTimeMillis()): List<WeekBucket> {
    val thisWeek = weekStart(now)
    val bounds = (-(weeks - 1)..1).map { addWeeks(thisWeek, it) }
    return (0 until weeks).map { i -> WeekBucket(bounds[i], workouts.filter { it.startTime >= bounds[i] && it.startTime < bounds[i + 1] }) }
}

/** Consecutive weeks (Mon-Sun) with at least one workout, ending this week or last week. */
fun weekStreak(workouts: List<Workout>, now: Long = System.currentTimeMillis()): Int {
    val thisWeek = weekStart(now)
    fun has(n: Int): Boolean {
        val lo = addWeeks(thisWeek, -n)
        val hi = addWeeks(thisWeek, -n + 1)
        return workouts.any { it.startTime >= lo && it.startTime < hi }
    }
    var n = if (has(0)) 0 else 1
    var streak = 0
    while (n < 2000 && has(n)) {
        streak++
        n++
    }
    return streak
}

private val isoDay get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Local "yyyy-MM-dd" of a timestamp. */
fun dateOf(ms: Long): String = isoDay.format(Date(ms))

/** Number of workouts per local date. */
fun workoutsByDate(workouts: List<Workout>): Map<String, Int> {
    val fmt = isoDay
    return workouts.groupingBy { fmt.format(Date(it.startTime)) }.eachCount()
}

/** Completed working sets per primary muscle since [sinceMs], most first. */
fun muscleSets(workouts: List<Workout>, sinceMs: Long, exercise: (String) -> Exercise?): List<Pair<String, Int>> {
    val out = HashMap<String, Int>()
    workouts.filter { it.startTime >= sinceMs }.forEach { w ->
        w.exercises.forEach { we ->
            val n = we.sets.count { it.completed && it.type != SetType.WARMUP }
            exercise(we.exerciseId)?.primaryMuscles?.forEach { m -> out[m] = (out[m] ?: 0) + n }
        }
    }
    return out.toList().filter { it.second > 0 }.sortedByDescending { it.second }
}

class PersonalRecord(
    val exerciseId: String,
    val sets: Int,
    val bestWeight: Double?,
    val best1RM: Double?,
)

/** Records for every trained exercise, most-trained first. */
fun personalRecords(workouts: List<Workout>): List<PersonalRecord> =
    workouts.flatMap { it.exercises }
        .groupBy { it.exerciseId }
        .map { (id, entries) ->
            val sets = entries.flatMap { it.sets }.filter { it.completed && it.type != SetType.WARMUP }
            PersonalRecord(
                exerciseId = id,
                sets = sets.size,
                bestWeight = sets.mapNotNull { it.weight }.filter { it > 0 }.maxOrNull(),
                best1RM = sets.mapNotNull { it.estimated1RM() }.maxOrNull(),
            )
        }
        .sortedByDescending { it.sets }

/** Best estimated 1RM per session of [exerciseId], oldest first, with the session start time. */
fun oneRepMaxSeries(workouts: List<Workout>, exerciseId: String): List<Pair<Long, Double>> =
    workouts.sortedBy { it.startTime }.mapNotNull { w ->
        w.exercises.filter { it.exerciseId == exerciseId }
            .flatMap { it.sets }
            .filter { it.completed && it.type != SetType.WARMUP }
            .mapNotNull { it.estimated1RM() }
            .maxOrNull()
            ?.let { w.startTime to it }
    }

/** Volume of each exercise in a workout, largest first (exerciseId to volume). */
fun Workout.volumeByExercise(): List<Pair<String, Double>> =
    exercises.groupBy { it.exerciseId }
        .map { (id, wes) -> id to wes.sumOf { we -> we.sets.filter { it.completed }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } } }
        .sortedByDescending { it.second }
