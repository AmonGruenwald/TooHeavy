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
) {
    /** Created by the user (stored in AppData.customExercises) rather than bundled. */
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    companion object {
        const val CUSTOM_PREFIX = "custom-"
        fun newCustomId() = CUSTOM_PREFIX + newId()
    }
}

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
)

data class RoutineExercise(
    val exerciseId: String = "",
    val sets: Int = 3,
    val reps: Int? = null,
    val weight: Double? = null,
    val restSeconds: Int = 90,
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
    val defaultRestSeconds: Int = 90,
    /** Home dashboard widgets in display order; null until the user customizes it (default layout). */
    val dashboard: List<DashboardWidget>? = null,
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
)

// ---------- helpers ----------

/** Total volume (weight x reps) of completed sets. */
fun Workout.volume(): Double =
    exercises.sumOf { we -> we.sets.filter { it.completed }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } }

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
