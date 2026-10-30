package com.tooheavy.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors

/**
 * Single app-wide store. All state is Compose-observable; every mutation persists to filesDir/data.json.
 * Call only from the main thread.
 */
object Repo {
    private val gson = Gson()
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var file: File

    /** Full exercise library (bundled + custom), sorted by name. */
    var exercises by mutableStateOf<List<Exercise>>(emptyList())
        private set
    private var byId: Map<String, Exercise> = emptyMap()
    private var bundled: List<Exercise> = emptyList()
    /** Exercises the user created, in creation order. */
    var customExercises by mutableStateOf<List<Exercise>>(emptyList())
        private set

    /** Completed workouts, newest first. */
    val workouts = mutableStateListOf<Workout>()
    val routines = mutableStateListOf<Routine>()

    var activeWorkout by mutableStateOf<Workout?>(null)
        private set
    var settings by mutableStateOf(Settings())
        private set
    /** Adaptive training program, null until the user creates one. */
    var program by mutableStateOf<Program?>(null)
        private set
    /** Changes the last finished program workout made to the program (not persisted). */
    var programUpdate by mutableStateOf<ProgramUpdate?>(null)

    /** Routine currently being edited in RoutineEditScreen (survives trips to the exercise picker). */
    var routineDraft by mutableStateOf<Routine?>(null)

    /** Rest timer: epoch ms when the running rest ends, null when no rest timer is running. */
    var restEndsAt by mutableStateOf<Long?>(null)
        private set
    var restTotalSeconds by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        bundled = gson.fromJson(json, object : TypeToken<List<Exercise>>() {}.type)
        file = File(context.filesDir, "data.json")
        if (file.exists()) {
            runCatching { gson.fromJson(file.readText(), AppData::class.java) }.getOrNull()?.let { d ->
                workouts.addAll(d.workouts.orEmpty().sortedByDescending { it.startTime })
                routines.addAll(d.routines.orEmpty())
                activeWorkout = d.activeWorkout
                settings = d.settings ?: Settings()
                program = d.program
                customExercises = d.customExercises.orEmpty()
            }
        }
        rebuildLibrary()
    }

    private fun rebuildLibrary() {
        exercises = (bundled + customExercises).sortedBy { it.name.lowercase() }
        byId = exercises.associateBy { it.id }
    }

    private fun save() {
        val snapshot = gson.toJson(AppData(workouts.toList(), routines.toList(), activeWorkout, settings, program, customExercises))
        io.execute {
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(snapshot)
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }
    }

    // ---------- exercises ----------

    fun exercise(id: String): Exercise? = byId[id]

    fun muscles(): List<String> = exercises.flatMap { it.primaryMuscles }.distinct().sorted()

    fun equipments(): List<String> = exercises.mapNotNull { it.equipment }.distinct().sorted()

    /** Adds or replaces a custom exercise (a new one needs an id from Exercise.newCustomId()). */
    fun saveCustomExercise(e: Exercise) {
        val list = customExercises.toMutableList()
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list[i] = e else list.add(e)
        customExercises = list
        rebuildLibrary()
        save()
    }

    /** Adds several custom exercises at once (e.g. from an import), skipping ids that already exist. */
    fun addCustomExercises(list: List<Exercise>) {
        val known = customExercises.map { it.id }.toSet()
        customExercises = customExercises + list.filter { it.id !in known }
        rebuildLibrary()
        save()
    }

    /** How many workouts, routines and program days use this exercise; it can only be deleted when that is 0. */
    fun exerciseUses(id: String): Int =
        workouts.count { w -> w.exercises.any { it.exerciseId == id } } +
            routines.count { r -> r.exercises.any { it.exerciseId == id } } +
            (program?.days?.count { d -> d.exercises.any { it.exerciseId == id } } ?: 0) +
            (if (activeWorkout?.exercises?.any { it.exerciseId == id } == true) 1 else 0)

    fun deleteCustomExercise(id: String) {
        if (exerciseUses(id) > 0) return
        customExercises = customExercises.filter { it.id != id }
        rebuildLibrary()
        save()
    }

    // ---------- active workout ----------

    /** Whether a workout is in progress. Safe off the main thread and before init (reads the saved file). */
    fun hasActiveWorkout(context: Context): Boolean =
        if (::file.isInitialized) activeWorkout != null
        else runCatching {
            gson.fromJson(File(context.filesDir, "data.json").readText(), AppData::class.java)?.activeWorkout != null
        }.getOrDefault(false)

    fun startWorkout(routine: Routine? = null) {
        val w = if (routine == null) Workout(name = "Workout")
        else Workout(
            name = routine.name,
            routineId = routine.id,
            exercises = routine.exercises.map { re ->
                WorkoutExercise(
                    exerciseId = re.exerciseId,
                    restSeconds = re.restSeconds,
                    sets = List(re.sets.coerceAtLeast(1)) { SetEntry(weight = re.weight, reps = re.reps) },
                )
            },
        )
        activeWorkout = w
        save()
    }

    fun updateActive(transform: (Workout) -> Workout) {
        val w = activeWorkout ?: return
        activeWorkout = transform(w)
        save()
    }

    fun addExercisesToActive(ids: List<String>) = updateActive { w ->
        w.copy(exercises = w.exercises + ids.map { WorkoutExercise(exerciseId = it, restSeconds = settings.defaultRestSeconds) })
    }

    /** Drops uncompleted sets and empty exercises; discards the workout if nothing was completed. */
    fun finishWorkout() {
        val w = activeWorkout ?: return
        val cleaned = w.copy(
            endTime = System.currentTimeMillis(),
            exercises = w.exercises
                .map { we -> we.copy(sets = we.sets.filter { it.completed }) }
                .filter { it.sets.isNotEmpty() },
        )
        if (cleaned.exercises.isNotEmpty()) {
            workouts.add(0, cleaned)
            val p = program
            if (p != null && p.days.any { it.id == cleaned.programDayId }) {
                val (next, changes) = p.afterSession(cleaned, settings.unit, this::exercise)
                program = next
                programUpdate = ProgramUpdate(cleaned.name, changes)
            }
        }
        activeWorkout = null
        stopRest()
        save()
    }

    fun discardWorkout() {
        activeWorkout = null
        stopRest()
        save()
    }

    // ---------- history ----------

    fun workout(id: String): Workout? = workouts.firstOrNull { it.id == id }

    fun deleteWorkout(id: String) {
        workouts.removeAll { it.id == id }
        save()
    }

    fun updateWorkout(w: Workout) {
        val i = workouts.indexOfFirst { it.id == w.id }
        if (i >= 0) workouts[i] = w
        save()
    }

    /** Completed sets of this exercise from the most recent finished workout that contains it (empty if none). */
    fun previousSets(exerciseId: String): List<SetEntry> =
        workouts.firstOrNull { w -> w.exercises.any { it.exerciseId == exerciseId } }
            ?.exercises?.filter { it.exerciseId == exerciseId }
            ?.flatMap { it.sets }.orEmpty()

    /** Every finished workout entry for this exercise, newest first. */
    fun exerciseHistory(exerciseId: String): List<Pair<Workout, WorkoutExercise>> =
        workouts.flatMap { w -> w.exercises.filter { it.exerciseId == exerciseId }.map { w to it } }

    // ---------- routines ----------

    fun routine(id: String): Routine? = routines.firstOrNull { it.id == id }

    fun saveRoutine(r: Routine) {
        val i = routines.indexOfFirst { it.id == r.id }
        if (i >= 0) routines[i] = r else routines.add(r)
        save()
    }

    fun deleteRoutine(id: String) {
        routines.removeAll { it.id == id }
        save()
    }

    /** Sets routineDraft for editing. routineId == "new" starts a blank routine (keeps an unsaved draft in progress). */
    fun beginRoutineEdit(routineId: String) {
        val d = routineDraft
        if (routineId == "new") {
            if (d == null || routines.any { it.id == d.id }) routineDraft = Routine()
        } else if (d?.id != routineId) {
            routineDraft = routine(routineId) ?: Routine()
        }
    }

    fun addExercisesToDraft(ids: List<String>) {
        val d = routineDraft ?: return
        routineDraft = d.copy(exercises = d.exercises + ids.map { RoutineExercise(exerciseId = it, restSeconds = settings.defaultRestSeconds) })
    }

    fun routineFromWorkout(w: Workout): Routine = Routine(
        name = w.name,
        exercises = w.exercises.map { we ->
            val last = we.sets.lastOrNull()
            RoutineExercise(we.exerciseId, sets = we.sets.size, reps = last?.reps, weight = last?.weight, restSeconds = we.restSeconds)
        },
    )

    // ---------- program ----------

    fun saveProgram(p: Program?) {
        program = p
        programUpdate = null
        save()
    }

    fun startProgramWorkout(dayIndex: Int) {
        val w = program?.buildWorkout(dayIndex, settings.unit, this::exercise) ?: return
        activeWorkout = w
        save()
    }

    /** Best estimated one-rep max from the last 5 sessions of this exercise, null without history. */
    fun best1RM(exerciseId: String): Double? =
        exerciseHistory(exerciseId).take(5)
            .flatMap { (_, we) -> we.sets.filter { it.completed && it.type != SetType.WARMUP } }
            .mapNotNull { it.estimated1RM() }
            .maxOrNull()

    // ---------- settings ----------

    fun updateSettings(s: Settings) {
        settings = s
        save()
    }

    /** Saves the Home dashboard layout; null restores the default. */
    fun updateDashboard(widgets: List<DashboardWidget>?) = updateSettings(settings.copy(dashboard = widgets))

    // ---------- rest timer ----------

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restTotalSeconds = seconds
        restEndsAt = System.currentTimeMillis() + seconds * 1000L
    }

    fun adjustRest(deltaSeconds: Int) {
        val end = restEndsAt ?: return
        restEndsAt = end + deltaSeconds * 1000L
        restTotalSeconds = (restTotalSeconds + deltaSeconds).coerceAtLeast(1)
    }

    fun stopRest() {
        restEndsAt = null
    }
}
