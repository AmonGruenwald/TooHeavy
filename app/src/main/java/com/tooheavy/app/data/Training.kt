package com.tooheavy.app.data

import kotlin.math.max
import kotlin.math.min

// Workout helpers shared by the program, routines and the active workout: muscle groups and the sets
// they received, automatic rest times, automatic warm-up sets and supersets.

// ---------- muscle groups ----------

/** The ten groups programs plan around, each made of one or more library muscles. */
object MuscleGroup {
    const val CHEST = "chest"
    const val BACK = "back"
    const val SHOULDERS = "shoulders"
    const val BICEPS = "biceps"
    const val TRICEPS = "triceps"
    const val ABS = "abs"
    const val QUADS = "quads"
    const val HAMSTRINGS = "hamstrings"
    const val GLUTES = "glutes"
    const val CALVES = "calves"
    val all = listOf(CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, ABS, QUADS, HAMSTRINGS, GLUTES, CALVES)

    private val musclesOf = mapOf(
        CHEST to setOf("chest"),
        BACK to setOf("lats", "middle back", "lower back", "traps"),
        SHOULDERS to setOf("shoulders"),
        BICEPS to setOf("biceps"),
        TRICEPS to setOf("triceps"),
        ABS to setOf("abdominals"),
        QUADS to setOf("quadriceps"),
        HAMSTRINGS to setOf("hamstrings"),
        GLUTES to setOf("glutes", "abductors"),
        CALVES to setOf("calves"),
    )

    fun label(group: String) = when (group) {
        ABS -> "Abs"
        QUADS -> "Quads"
        else -> group.titleCase()
    }

    fun muscles(group: String): Set<String> = musclesOf[group].orEmpty()

    /** The group a library muscle belongs to, null for small ones such as forearms or neck. */
    fun of(muscle: String): String? = musclesOf.entries.firstOrNull { muscle in it.value }?.key
}

/** Library muscles in body order, for lists and the body map. */
val allMuscles = listOf(
    "chest", "shoulders", "traps", "lats", "middle back", "lower back", "biceps", "triceps", "forearms",
    "abdominals", "quadriceps", "hamstrings", "glutes", "abductors", "adductors", "calves", "neck",
)

/** Working sets credited per library muscle: a whole set for each primary muscle, half for each secondary. */
fun muscleCredit(exercises: List<WorkoutExercise>, exercise: (String) -> Exercise?, completedOnly: Boolean = true): Map<String, Double> {
    val out = HashMap<String, Double>()
    exercises.forEach { we ->
        val e = exercise(we.exerciseId) ?: return@forEach
        val n = we.sets.count { (it.completed || !completedOnly) && it.type != SetType.WARMUP }
        if (n == 0) return@forEach
        e.primaryMuscles.forEach { m -> out[m] = (out[m] ?: 0.0) + n }
        e.secondaryMuscles.filter { it !in e.primaryMuscles }.forEach { m -> out[m] = (out[m] ?: 0.0) + n * 0.5 }
    }
    return out
}

/**
 * Working sets credited per MuscleGroup: a whole set to each group a primary muscle belongs to, half to the
 * other groups the exercise works. Each group counts once per set, however many of its muscles are involved.
 */
fun groupCredit(exercises: List<WorkoutExercise>, exercise: (String) -> Exercise?, completedOnly: Boolean = true): Map<String, Double> {
    val out = HashMap<String, Double>()
    exercises.forEach { we ->
        val e = exercise(we.exerciseId) ?: return@forEach
        val n = we.sets.count { (it.completed || !completedOnly) && it.type != SetType.WARMUP }
        if (n == 0) return@forEach
        val primary = e.primaryMuscles.mapNotNull(MuscleGroup::of).toSet()
        val secondary = e.secondaryMuscles.mapNotNull(MuscleGroup::of).toSet() - primary
        primary.forEach { g -> out[g] = (out[g] ?: 0.0) + n }
        secondary.forEach { g -> out[g] = (out[g] ?: 0.0) + n * 0.5 }
    }
    return out
}

/** Credited sets of [group] per week for the last [weeks] weeks, oldest first. */
fun weeklyGroupCredit(workouts: List<Workout>, group: String, weeks: Int, exercise: (String) -> Exercise?, now: Long = System.currentTimeMillis()): List<Double> =
    weeklyBuckets(workouts, weeks, now).map { b -> groupCredit(b.workouts.flatMap { it.exercises }, exercise)[group] ?: 0.0 }

/** Credited sets per muscle across [workouts] started in [from, to). */
fun muscleCredit(workouts: List<Workout>, from: Long, to: Long, exercise: (String) -> Exercise?): Map<String, Double> =
    muscleCredit(workouts.filter { it.startTime in from until to }.flatMap { it.exercises }, exercise)

/** Credited sets of [muscle] per week for the last [weeks] weeks, oldest first. */
fun weeklyMuscleSets(workouts: List<Workout>, muscle: String, weeks: Int, exercise: (String) -> Exercise?, now: Long = System.currentTimeMillis()): List<Double> =
    weeklyBuckets(workouts, weeks, now).map { b -> muscleCredit(b.workouts.flatMap { it.exercises }, exercise)[muscle] ?: 0.0 }

/** Weekly sets most lifters grow on: about 10 to 20 per muscle. */
val weeklySetGuide = 10.0..20.0

// ---------- rest times ----------

private val loadedEquipment = setOf("barbell", "e-z curl bar", "dumbbell", "kettlebells", "cable", "machine", "other")
private val conditioning = setOf("cardio", "stretching", "plyometrics")

object RestTimes {
    /** Rest that suits the kind of exercise: long for heavy compounds, short for small muscles and conditioning. */
    fun auto(e: Exercise?): Int = when {
        e == null -> 90
        e.category in conditioning -> 60
        e.mechanic == "compound" && (e.equipment == "barbell" || e.category == "powerlifting" || e.category == "olympic weightlifting") -> 180
        e.mechanic == "compound" && e.equipment in loadedEquipment -> 120
        e.primaryMuscles.any { it == "abdominals" || it == "calves" || it == "forearms" || it == "neck" } -> 60
        else -> 90
    }

    /**
     * Seconds to rest after completing set [setId] of exercise [weIndex] in [w], 0 for none: no rest between
     * the exercises of a superset or before a drop set, a short one after a warm-up, the longest rest of the
     * group after the last exercise of a superset.
     */
    fun after(w: Workout, weIndex: Int, setId: String): Int {
        val we = w.exercises.getOrNull(weIndex) ?: return 0
        val k = we.sets.indexOfFirst { it.id == setId }
        if (k < 0) return we.restSeconds
        if (we.sets.getOrNull(k + 1)?.type == SetType.DROP) return 0
        val group = supersetRange(w.exercises, weIndex)
        if (group != null && weIndex != group.last) return 0
        val rest = if (group != null) group.maxOf { w.exercises[it].restSeconds } else we.restSeconds
        return if (we.sets[k].type == SetType.WARMUP) min(rest, 60) else rest
    }
}

// ---------- warm-ups ----------

object WarmUps {
    /** Whether ramp-up sets make sense: a loaded strength exercise. */
    fun suits(e: Exercise?): Boolean =
        e != null && e.category !in conditioning && e.equipment in loadedEquipment

    /** Muscle groups (or muscles outside the groups) an exercise works, primary and secondary. */
    private fun groupsOf(e: Exercise?, primaryOnly: Boolean): Set<String> {
        if (e == null) return emptySet()
        val muscles = if (primaryOnly) e.primaryMuscles else e.primaryMuscles + e.secondaryMuscles
        return muscles.map { MuscleGroup.of(it) ?: it }.toSet()
    }

    /** Whether [index] is the first exercise in [exercises] to work one of its main muscle groups. */
    fun firstForMuscles(exercises: List<WorkoutExercise>, index: Int, exercise: (String) -> Exercise?): Boolean {
        val warmed = exercises.take(index).flatMap { groupsOf(exercise(it.exerciseId), false) }.toSet()
        return groupsOf(exercise(exercises[index].exerciseId), true).any { it !in warmed }
    }

    /**
     * Ramp-up sets toward a [working] weight: 50 % × 8, 70 % × 5 and 85 % × 2 for compounds, 50 % × 10 for
     * isolation exercises, rounded to loadable weights and never lighter than the empty bar. Without a known
     * working weight it is one light set of 10 for the lifter to fill in.
     */
    fun sets(working: Double?, e: Exercise?, unit: String): List<SetEntry> {
        if (working == null || working <= 0) return listOf(SetEntry(reps = 10, type = SetType.WARMUP))
        val step = loadStep(e?.equipment, "", unit).rounding
        val barbell = e?.equipment == "barbell"
        val floor = if (barbell) (if (unit == "lb") 45.0 else 20.0) else step
        val scheme = if (e?.mechanic == "compound") listOf(0.5 to 8, 0.7 to 5, 0.85 to 2) else listOf(0.5 to 10)
        return scheme
            .map { (f, r) -> max(floor, roundLoad(working * f, step)) to r }
            .filter { (w, _) -> w < working - step / 2 }
            .distinctBy { it.first }
            .map { (w, r) -> SetEntry(weight = w, reps = r, type = SetType.WARMUP) }
    }

    /**
     * Adds warm-up sets to the exercises in [w] (only those in [only], when given) that are the first of the
     * workout to train their muscle group, haven't started and have no warm-ups yet. [lastWorking] is the
     * weight of the last working set logged for an exercise, used when the workout has none filled in.
     */
    fun apply(
        w: Workout,
        unit: String,
        exercise: (String) -> Exercise?,
        lastWorking: (String) -> Double?,
        only: Set<String>? = null,
    ): Workout = w.copy(
        exercises = w.exercises.mapIndexed { i, we ->
            val e = exercise(we.exerciseId)
            val eligible = (only == null || we.id in only) && suits(e) &&
                we.sets.none { it.completed || it.type == SetType.WARMUP } && firstForMuscles(w.exercises, i, exercise)
            if (!eligible) return@mapIndexed we
            val working = we.sets.firstOrNull { it.type != SetType.WARMUP }?.weight ?: lastWorking(we.exerciseId)
            we.copy(sets = sets(working, e, unit) + we.sets)
        },
    )
}

// ---------- supersets ----------

/** Indices of the superset [index] belongs to (consecutive exercises sharing a supersetId), null if none. */
fun supersetRange(exercises: List<WorkoutExercise>, index: Int): IntRange? {
    val id = exercises.getOrNull(index)?.supersetId ?: return null
    var a = index
    while (a > 0 && exercises[a - 1].supersetId == id) a--
    var b = index
    while (b < exercises.lastIndex && exercises[b + 1].supersetId == id) b++
    return if (a == b) null else a..b
}

/** Letter per exercise index for supersets ("A", "B" ...) in order of appearance; null when not in one. */
fun supersetLetters(ids: List<String?>): List<String?> {
    var n = -1
    return ids.mapIndexed { i, id ->
        val inGroup = id != null && (ids.getOrNull(i - 1) == id || ids.getOrNull(i + 1) == id)
        if (!inGroup) null
        else {
            if (ids.getOrNull(i - 1) != id) n++
            ('A' + n % 26).toString()
        }
    }
}

/** Clears superset ids that no longer join two neighbours (after a move or removal). */
fun <T> List<T>.normalizeSupersets(id: (T) -> String?, clear: (T) -> T): List<T> =
    mapIndexed { i, x ->
        val s = id(x) ?: return@mapIndexed x
        val joined = (i > 0 && id(this[i - 1]) == s) || (i < lastIndex && id(this[i + 1]) == s)
        if (joined) x else clear(x)
    }

/** Joins exercise [index] with the next one: into its superset if it has one, else a new one. */
fun <T> List<T>.linkWithNext(index: Int, id: (T) -> String?, set: (T, String?) -> T): List<T> {
    if (index !in 0 until lastIndex) return this
    val group = id(this[index]) ?: id(this[index + 1]) ?: newId()
    val old = id(this[index + 1])
    return mapIndexed { i, x ->
        when {
            i == index || i == index + 1 -> set(x, group)
            old != null && id(x) == old -> set(x, group)
            else -> x
        }
    }
}

/** Takes exercise [index] out of its superset; the exercises before and after it stay grouped if they can. */
fun <T> List<T>.unlink(index: Int, id: (T) -> String?, set: (T, String?) -> T): List<T> {
    val s = id(getOrNull(index) ?: return this) ?: return this
    var end = index
    while (end < lastIndex && id(this[end + 1]) == s) end++
    // The part after it gets its own id so the run splits cleanly.
    val tail = newId()
    return mapIndexed { i, x ->
        when (i) {
            index -> set(x, null)
            in index + 1..end -> set(x, tail)
            else -> x
        }
    }.normalizeSupersets(id) { set(it, null) }
}
