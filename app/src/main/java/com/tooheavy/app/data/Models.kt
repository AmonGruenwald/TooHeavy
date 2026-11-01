package com.tooheavy.app.data

import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Static exercise from the bundled library (assets/exercises.json, public-domain free-exercise-db). */
data class Exercise(
    val id: String = "",
    val name: String = "",
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val category: String? = null,
    /** Asset paths, e.g. "exercises/Barbell_Squat/0.webp". 0 = start position, 1 = end position. May be empty. */
    val images: List<String> = emptyList(),
    /** What a set logs: Tracking.TIME for a duration, Tracking.DISTANCE for distance and time, null for weight and reps. */
    val tracking: String? = null,
) {
    /** Created by the user (stored in AppData.customExercises) rather than bundled. */
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** Sets log a duration (and maybe a distance) instead of weight and reps. */
    val isTimed: Boolean get() = tracking == Tracking.TIME || tracking == Tracking.DISTANCE

    /** Sets log a distance and a time, e.g. a run. */
    val tracksDistance: Boolean get() = tracking == Tracking.DISTANCE

    companion object {
        const val CUSTOM_PREFIX = "custom-"
        fun newCustomId() = CUSTOM_PREFIX + newId()
    }
}

/** What the sets of an exercise log (Exercise.tracking). */
object Tracking {
    /** Only how long the set took, e.g. a general warm-up. */
    const val TIME = "time"
    /** Distance and time, e.g. a run; pace comes from both. */
    const val DISTANCE = "distance"
}

/** Exercise.category of warm-ups; they come first in a workout and don't need muscles. */
const val CATEGORY_WARMUP = "warmup"

object SetType {
    const val NORMAL = "normal"
    const val WARMUP = "warmup"
    const val DROP = "drop"
    const val FAILURE = "failure"
}

data class SetEntry(
    val id: String = newId(),
    /** Weight in the user's unit (Settings.unit) as entered. */
    val weight: Double? = null,
    val reps: Int? = null,
    val completed: Boolean = false,
    val type: String = SetType.NORMAL,
    /** Reps in reserve the lifter reported, null when not logged. */
    val rir: Int? = null,
    /** Partial reps after the full ones; each counts as half a rep in volume. */
    val halfReps: Int? = null,
    /** Duration of a set of a timed exercise (Exercise.isTimed). */
    val seconds: Int? = null,
    /** Distance of a set of a distance exercise (Exercise.tracksDistance), always in km. */
    val distanceKm: Double? = null,
    /** Id of the GPS route recorded for this set (RouteStore), null when it wasn't recorded. */
    val routeId: String? = null,
    /** Epoch ms when the set's stopwatch was started, null while it isn't running. */
    val timerStartedAt: Long? = null,
)

data class WorkoutExercise(
    val id: String = newId(),
    val exerciseId: String = "",
    val sets: List<SetEntry> = listOf(SetEntry()),
    val notes: String = "",
    val restSeconds: Int = 90,
    /** ProgramExercise.id when this exercise came from the training program. */
    val programExerciseId: String? = null,
    /** What the program prescribed for this session. */
    val target: SetTarget? = null,
    /** Consecutive exercises with the same id form a superset; null when the exercise stands alone. */
    val supersetId: String? = null,
)

data class Workout(
    val id: String = newId(),
    val name: String = "Workout",
    val startTime: Long = System.currentTimeMillis(),
    /** null while the workout is in progress. */
    val endTime: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val notes: String = "",
    val routineId: String? = null,
    /** ProgramDay.id when started from the training program; finishing it updates the program. */
    val programDayId: String? = null,
    /** PlannedRun.id when started from the running plan; finishing it ticks the run off. */
    val plannedRunId: String? = null,
)

data class RoutineExercise(
    val exerciseId: String = "",
    val sets: Int = 3,
    val reps: Int? = null,
    val weight: Double? = null,
    val restSeconds: Int = 90,
    val supersetId: String? = null,
    /** Duration per set, for timed exercises. */
    val seconds: Int? = null,
    /** Distance per set in km, for distance exercises. */
    val distanceKm: Double? = null,
)

data class Routine(
    val id: String = newId(),
    val name: String = "New Routine",
    val exercises: List<RoutineExercise> = emptyList(),
    val notes: String = "",
)

/** One widget on the Home dashboard; [id] is a DashboardWidgets id, [wide] = full width (else half). */
data class DashboardWidget(
    val id: String = "",
    val wide: Boolean = true,
)

data class Settings(
    /** "kg" or "lb" */
    val unit: String = "kg",
    /** Rest for exercises without a remembered or automatic time. */
    val defaultRestSeconds: Int = 90,
    /** Pick rest times per exercise from the kind of lift (heavy compound, isolation, core...). */
    val autoRest: Boolean = true,
    /** Rest times the user chose per exercise id; they win over automatic ones. */
    val restByExercise: Map<String, Int> = emptyMap(),
    /** Add ramp-up sets to the first exercise of each muscle group in a workout. */
    val autoWarmups: Boolean = true,
    /** Home dashboard widgets in display order; null until the user customizes it (default layout). */
    val dashboard: List<DashboardWidget>? = null,
    /** Pause a GPS-recorded run by itself while standing still, and carry on once moving again. */
    val autoPauseRuns: Boolean = true,
    /** The battery optimisation explanation was shown once before recording a run. */
    val batteryPromptShown: Boolean = false,
)

/** Everything persisted to filesDir/data.json. */
data class AppData(
    val workouts: List<Workout> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val activeWorkout: Workout? = null,
    val settings: Settings = Settings(),
    val program: Program? = null,
    /** Exercises the user created; ids start with Exercise.CUSTOM_PREFIX. */
    val customExercises: List<Exercise> = emptyList(),
    /** The running plan being followed, null without one. */
    val runPlan: RunPlan? = null,
)

// ---------- helpers ----------

/** Full reps plus half a rep per partial. */
val SetEntry.effectiveReps: Double get() = (reps ?: 0) + (halfReps ?: 0) * 0.5

/** Weight × reps of one set, partials counted as half reps. */
val SetEntry.volume: Double get() = (weight ?: 0.0) * effectiveReps

/** Total volume (weight x reps) of completed sets. */
fun Workout.volume(): Double =
    exercises.sumOf { we -> we.sets.filter { it.completed }.sumOf { it.volume } }

fun Workout.completedSetCount(): Int = exercises.sumOf { we -> we.sets.count { it.completed } }

fun Workout.durationMs(): Long = (endTime ?: System.currentTimeMillis()) - startTime

/** Epley estimated one-rep max, null if weight/reps missing. */
fun SetEntry.estimated1RM(): Double? {
    val w = weight ?: return null
    val r = reps ?: return null
    if (r <= 0 || w <= 0) return null
    return if (r == 1) w else w * (1 + r / 30.0)
}

/** "1h 5m" / "12m" / "45s" */
fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${totalSec}s"
    }
}

/** Seconds of a set with its stopwatch running counted up to [now]. */
fun SetEntry.secondsAt(now: Long): Int? =
    timerStartedAt?.let { (seconds ?: 0) + ((now - it) / 1000).toInt().coerceAtLeast(0) } ?: seconds

/** "5:00" / "45s" / "1:02:03" for a set duration. */
fun formatSetTime(seconds: Int): String = if (seconds < 60) "${seconds}s" else formatClock(seconds * 1000L)

/** "5" -> 300 (minutes), "5:30" -> 330, "1:02:03" -> 3723; null when it isn't a time. */
fun parseSetTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 3 }) return null
    val n = parts.map { it.toInt() }
    val total = when (n.size) {
        1 -> n[0] * 60
        2 -> n[0] * 60 + n[1]
        else -> n[0] * 3600 + n[1] * 60 + n[2]
    }
    return total.takeIf { it > 0 }
}

/** "4:05" / "1:02:03" */
fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** 60.0 -> "60", 62.5 -> "62.5", null -> "" */
fun formatWeight(w: Double?): String {
    if (w == null) return ""
    return if (w == Math.floor(w)) w.toLong().toString() else String.format(Locale.US, "%.2f", w).trimEnd('0').trimEnd('.')
}

/** 12345.0 -> "12,345" */
fun formatVolume(v: Double): String = String.format(Locale.US, "%,d", v.toLong())

/** "lower back" -> "Lower Back" */
fun String.titleCase(): String =
    split(" ").joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } }
