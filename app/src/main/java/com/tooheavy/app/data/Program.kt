package com.tooheavy.app.data

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Adaptive training programs. The generator builds a split from goal, experience, schedule and
// equipment. After every program workout the progression engine sets the next session's load, reps
// and sets from what was actually lifted: double progression, a reset after repeated stalls, volume
// that follows performance, and mesocycles that end in a deload week.

object TrainingGoal {
    const val STRENGTH = "strength"
    const val HYPERTROPHY = "hypertrophy"
    const val GENERAL = "general"
    val all = listOf(HYPERTROPHY, STRENGTH, GENERAL)
    fun label(goal: String) = when (goal) {
        STRENGTH -> "Strength"
        GENERAL -> "General fitness"
        else -> "Muscle growth"
    }
    fun hint(goal: String) = when (goal) {
        STRENGTH -> "Heavier weights, lower reps"
        GENERAL -> "A balance of strength and size"
        else -> "Moderate weights, more reps"
    }
}

object Experience {
    const val BEGINNER = "beginner"
    const val INTERMEDIATE = "intermediate"
    const val ADVANCED = "advanced"
    val all = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    fun label(level: String) = level.titleCase()
    fun hint(level: String) = when (level) {
        BEGINNER -> "Less than a year of regular lifting"
        INTERMEDIATE -> "1 to 3 years"
        else -> "More than 3 years"
    }
}

object Equipment {
    const val GYM = "gym"
    const val HOME = "home"
    const val BODYWEIGHT = "bodyweight"
    val all = listOf(GYM, HOME, BODYWEIGHT)
    fun label(equipment: String) = when (equipment) {
        HOME -> "Dumbbells"
        BODYWEIGHT -> "Bodyweight"
        else -> "Full gym"
    }
    fun hint(equipment: String) = when (equipment) {
        HOME -> "Dumbbells, a bench and a pull-up bar"
        BODYWEIGHT -> "No weights, just a pull-up bar"
        else -> "Barbells, dumbbells, cables and machines"
    }
}

object Role {
    const val PRIMARY = "primary"
    const val SECONDARY = "secondary"
    const val ACCESSORY = "accessory"
}

object Pattern {
    const val SQUAT = "squat"
    const val HINGE = "hinge"
    const val HORIZONTAL_PUSH = "horizontal_push"
    const val VERTICAL_PUSH = "vertical_push"
    const val HORIZONTAL_PULL = "horizontal_pull"
    const val VERTICAL_PULL = "vertical_pull"
    const val LUNGE = "lunge"
    const val LEG_CURL = "leg_curl"
    const val LEG_EXTENSION = "leg_extension"
    const val CALVES = "calves"
    const val BICEPS = "biceps"
    const val TRICEPS = "triceps"
    const val LATERAL_DELTS = "lateral_delts"
    const val REAR_DELTS = "rear_delts"
    const val CHEST_FLY = "chest_fly"
    const val CORE = "core"
    fun label(pattern: String) = pattern.replace('_', ' ').titleCase()
}

// ---------- models ----------

data class ProgramExercise(
    val id: String = newId(),
    val exerciseId: String = "",
    /** Pattern.* slot this exercise fills; swaps offer exercises of the same pattern first. */
    val pattern: String = "",
    val role: String = Role.ACCESSORY,
    /** Working sets for the next session. */
    val sets: Int = 3,
    /** Sets at the start of each mesocycle. Volume adjustments stay within baseSets - 1 .. baseSets + 2. */
    val baseSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    /** Target reps per set for the next session. */
    val targetReps: Int = 8,
    /** Target load for the next session in the user's unit; null until it is known, or for bodyweight. */
    val weight: Double? = null,
    val restSeconds: Int = 90,
    /** Sessions of this exercise completed in the program. */
    val sessions: Int = 0,
    /** Consecutive sessions that missed the target. */
    val stalls: Int = 0,
    /** Consecutive sessions that hit the target. */
    val streak: Int = 0,
    /** Why the prescription last changed. */
    val note: String = "",
)

data class ProgramDay(
    val id: String = newId(),
    val name: String = "",
    val exercises: List<ProgramExercise> = emptyList(),
)

data class Program(
    val id: String = newId(),
    val name: String = "",
    val goal: String = TrainingGoal.HYPERTROPHY,
    val experience: String = Experience.BEGINNER,
    val equipment: String = Equipment.GYM,
    val days: List<ProgramDay> = emptyList(),
    /** Training weeks per mesocycle; a deload week follows. */
    val mesoWeeks: Int = 4,
    val sessionsCompleted: Int = 0,
    /** Index into days of the last finished session, -1 before the first. */
    val lastDayIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
)

/** What a program workout asked for on one exercise; kept on WorkoutExercise.target. */
data class SetTarget(
    val sets: Int = 3,
    val reps: Int = 8,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val weight: Double? = null,
    val rir: Int = 2,
    val deload: Boolean = false,
)

/** Shown after finishing a program workout. */
data class ProgramUpdate(val workoutName: String, val changes: List<String>)

// ---------- schedule ----------

/** 0-based program week; one pass through all days counts as a week. */
val Program.weekIndex: Int get() = if (days.isEmpty()) 0 else sessionsCompleted / days.size
val Program.mesocycle: Int get() = weekIndex / (mesoWeeks + 1) + 1
/** 1-based week within the mesocycle; mesoWeeks + 1 is the deload. */
val Program.mesoWeek: Int get() = weekIndex % (mesoWeeks + 1) + 1
val Program.isDeload: Boolean get() = mesoWeek > mesoWeeks
val Program.nextDayIndex: Int get() = if (days.isEmpty()) 0 else (lastDayIndex + 1).mod(days.size)

/** Reps in reserve to aim for: 3 in the first week down to 1 in the last, 4 on deload. */
val Program.targetRir: Int
    get() = when {
        isDeload -> 4
        mesoWeeks <= 1 -> 2
        else -> 3 - (2.0 * (mesoWeek - 1) / (mesoWeeks - 1)).roundToInt()
    }

fun Program.prescription(pe: ProgramExercise, unit: String, equipment: String?): SetTarget =
    if (isDeload) SetTarget(
        sets = max(1, (pe.baseSets + 1) / 2),
        reps = min(pe.targetReps, pe.repMin),
        repMin = pe.repMin,
        repMax = pe.repMax,
        weight = pe.weight?.let { roundLoad(it * 0.9, loadStep(equipment, pe.pattern, unit).rounding) },
        rir = targetRir,
        deload = true,
    )
    else SetTarget(pe.sets, pe.targetReps, pe.repMin, pe.repMax, pe.weight, targetRir)

/** The workout for [dayIndex], with sets prefilled from this week's prescription. */
fun Program.buildWorkout(dayIndex: Int, unit: String, exercise: (String) -> Exercise?): Workout? {
    val day = days.getOrNull(dayIndex) ?: return null
    return Workout(
        name = day.name,
        programDayId = day.id,
        exercises = day.exercises.map { pe ->
            val equipment = exercise(pe.exerciseId)?.equipment
            val t = prescription(pe, unit, equipment)
            WorkoutExercise(
                exerciseId = pe.exerciseId,
                restSeconds = pe.restSeconds,
                programExerciseId = pe.id,
                target = t,
                sets = warmups(pe, t, equipment, unit) + List(t.sets) { SetEntry(weight = t.weight, reps = t.reps) },
            )
        },
    )
}

/** Two ramp-up sets before a heavy barbell main lift. */
private fun warmups(pe: ProgramExercise, t: SetTarget, equipment: String?, unit: String): List<SetEntry> {
    val w = t.weight ?: return emptyList()
    val bar = if (unit == "lb") 45.0 else 20.0
    if (pe.role != Role.PRIMARY || equipment != "barbell" || w < bar * 2) return emptyList()
    val step = loadStep(equipment, pe.pattern, unit).rounding
    return listOf(0.5 to 8, 0.75 to 3).map { (f, r) ->
        SetEntry(weight = max(bar, roundLoad(w * f, step)), reps = r, type = SetType.WARMUP)
    }
}

fun Program.replaceExercise(updated: ProgramExercise): Program =
    copy(days = days.map { d -> d.copy(exercises = d.exercises.map { if (it.id == updated.id) updated else it }) })

// ---------- loads ----------

/** [increment] is the jump when a lift moves up; [rounding] is the smallest practical load change. */
data class LoadStep(val increment: Double, val rounding: Double)

fun loadStep(equipment: String?, pattern: String, unit: String): LoadStep {
    val lb = unit == "lb"
    val lower = pattern == Pattern.SQUAT || pattern == Pattern.HINGE || pattern == Pattern.LUNGE
    return when (equipment) {
        "barbell", "e-z curl bar" ->
            if (lower) (if (lb) LoadStep(10.0, 5.0) else LoadStep(5.0, 2.5))
            else (if (lb) LoadStep(5.0, 5.0) else LoadStep(2.5, 2.5))
        "dumbbell" -> if (lb) LoadStep(5.0, 2.5) else LoadStep(2.0, 1.0)
        "kettlebells" -> if (lb) LoadStep(10.0, 5.0) else LoadStep(4.0, 2.0)
        "machine", "cable" -> if (lb) LoadStep(10.0, 5.0) else LoadStep(5.0, 2.5)
        else -> if (lb) LoadStep(5.0, 2.5) else LoadStep(2.5, 1.25)
    }
}

fun roundLoad(w: Double, step: Double): Double = Math.round(w / step) * step

/** About 10 % lighter, always at least one rounding step below [w]. */
private fun lighter(w: Double, step: LoadStep): Double =
    max(step.rounding, min(roundLoad(w * 0.9, step.rounding), w - step.rounding))

/** Working weight for [reps] with [rir] in reserve, from an estimated one-rep max (Epley), rounded down. */
fun startingWeight(oneRepMax: Double?, reps: Int, rir: Int, step: LoadStep): Double? {
    val e = oneRepMax ?: return null
    val w = floor(e / (1 + (reps + rir) / 30.0) / step.rounding) * step.rounding
    return w.takeIf { it > 0 }
}

private fun e1rm(weight: Double, reps: Int): Double = weight * (1 + reps / 30.0)

// ---------- progression ----------

private const val STALL_LIMIT = 3
private const val HITS_PER_ADDED_SET = 2
private const val EPS = 1e-6

private enum class Outcome { BASELINE, HIT, HELD, MISSED }

/**
 * Applies a finished program workout: advances the rotation and, outside deload weeks, sets each
 * performed exercise's next prescription. Returns the new program and a line per change.
 */
fun Program.afterSession(workout: Workout, unit: String, exercise: (String) -> Exercise?): Pair<Program, List<String>> {
    val dayIndex = days.indexOfFirst { it.id == workout.programDayId }
    if (dayIndex < 0) return this to emptyList()
    val changes = ArrayList<String>()
    val day = days[dayIndex]
    val updated = if (isDeload) day else day.copy(
        exercises = day.exercises.map { pe ->
            val we = workout.exercises.firstOrNull { it.programExerciseId == pe.id } ?: return@map pe
            val next = progressExercise(pe, we, unit, exercise(pe.exerciseId)?.equipment)
            changes.add("${exercise(pe.exerciseId)?.name ?: "Exercise"}: ${next.note}")
            next
        },
    )
    if (isDeload) changes.add("Deload session logged. Targets hold until the next mesocycle.")
    var next = copy(
        days = days.mapIndexed { i, d -> if (i == dayIndex) updated else d },
        sessionsCompleted = sessionsCompleted + 1,
        lastDayIndex = dayIndex,
    )
    if (next.mesocycle > mesocycle) {
        next = next.copy(days = next.days.map { d -> d.copy(exercises = d.exercises.map { it.copy(sets = it.baseSets, streak = 0) }) })
        changes.add("Deload done. Mesocycle ${next.mesocycle} starts back at base volume.")
    } else if (next.isDeload && !isDeload) {
        changes.add("Next week is a deload: about half the sets at 90 % of the weight.")
    }
    return next to changes
}

/**
 * Next prescription for [pe] from the sets completed in [we], judged against what was asked for.
 *
 * Load uses double progression: reps climb toward the top of the range at the same weight, and once
 * every set reaches the top the weight goes up and reps restart at the bottom. Three sessions in a row
 * without hitting the target drop the weight by about 10 % to build back up.
 * Volume follows performance: two hits in a row add a set (up to baseSets + 2), falling below the
 * range removes one (down to baseSets - 1).
 */
internal fun progressExercise(pe: ProgramExercise, we: WorkoutExercise, unit: String, equipment: String?): ProgramExercise {
    val working = we.sets.filter { it.completed && it.type != SetType.WARMUP && (it.reps ?: 0) > 0 }
    if (working.isEmpty()) return pe.copy(note = "Skipped last time, same targets")
    val target = we.target
    val tSets = target?.sets ?: pe.sets
    val tReps = target?.reps ?: pe.targetReps
    val tWeight = target?.weight ?: pe.weight
    val top = working.maxOf { it.weight ?: 0.0 }
    return if (top <= 0.0) progressBodyweight(pe, working, tSets, tReps)
    else progressLoaded(pe, working, top, tSets, tReps, tWeight, loadStep(equipment, pe.pattern, unit), unit)
}

private fun progressLoaded(
    pe: ProgramExercise,
    working: List<SetEntry>,
    top: Double,
    tSets: Int,
    tReps: Int,
    tWeight: Double?,
    step: LoadStep,
    unit: String,
): ProgramExercise {
    val reps = working.filter { (it.weight ?: 0.0) >= top - EPS }.map { it.reps ?: 0 }
    val minReps = reps.min()
    val allSets = reps.size >= tSets
    val outcome = when {
        pe.sessions == 0 || tWeight == null -> Outcome.BASELINE
        top > tWeight + EPS -> if (minReps >= pe.repMin) Outcome.HIT else Outcome.HELD
        top >= tWeight - EPS -> when {
            allSets && minReps >= tReps -> Outcome.HIT
            minReps >= pe.repMin -> Outcome.HELD
            else -> Outcome.MISSED
        }
        // Went lighter than asked: compare estimated strength instead.
        e1rm(top, minReps) >= e1rm(tWeight, tReps) - EPS -> Outcome.HELD
        else -> Outcome.MISSED
    }

    var weight = top
    var nextReps: Int
    var note: String
    when {
        minReps >= pe.repMax && allSets -> {
            weight = roundLoad(top + step.increment, step.rounding)
            nextReps = pe.repMin
            note = "+${formatWeight(weight - top)} $unit after ${pe.repMax} reps on every set"
        }
        minReps >= pe.repMax -> {
            nextReps = pe.repMax
            note = "Finish all $tSets sets at ${pe.repMax} reps to move up"
        }
        minReps >= pe.repMin -> {
            nextReps = min(minReps + 1, pe.repMax)
            note = "Same weight, aim for $nextReps reps"
        }
        reps.max() < pe.repMin - 2 -> {
            weight = lighter(top, step)
            nextReps = pe.repMin
            note = "Too heavy for ${pe.repMin}-${pe.repMax} reps, down to ${formatWeight(weight)} $unit"
        }
        else -> {
            nextReps = pe.repMin
            note = "Same weight, aim for ${pe.repMin} reps on every set"
        }
    }

    var stalls = if (outcome == Outcome.HIT || outcome == Outcome.BASELINE) 0 else pe.stalls + 1
    if (stalls >= STALL_LIMIT) {
        weight = lighter(top, step)
        nextReps = pe.repMin
        stalls = 0
        note = "No progress in $STALL_LIMIT sessions, reset to ${formatWeight(weight)} $unit to build back up"
    }
    val (sets, streak, volumeNote) = adjustVolume(pe, outcome)
    return pe.copy(
        weight = weight, targetReps = nextReps, sets = sets, sessions = pe.sessions + 1,
        stalls = stalls, streak = streak, note = note + volumeNote,
    )
}

private fun progressBodyweight(pe: ProgramExercise, working: List<SetEntry>, tSets: Int, tReps: Int): ProgramExercise {
    val reps = working.map { it.reps ?: 0 }
    val minReps = reps.min()
    val allSets = reps.size >= tSets
    val outcome = when {
        pe.sessions == 0 -> Outcome.BASELINE
        allSets && minReps >= tReps -> Outcome.HIT
        reps.sum() >= tSets * tReps * 0.9 -> Outcome.HELD
        else -> Outcome.MISSED
    }
    val nextReps = if (allSets) minReps + 1 else minReps
    var note = if (minReps >= pe.repMax) "Aim for $nextReps reps; past the range, so add weight or try a harder variation"
    else "Aim for $nextReps reps per set"
    var stalls = if (outcome == Outcome.HIT || outcome == Outcome.BASELINE) 0 else pe.stalls + 1
    if (stalls >= STALL_LIMIT) {
        stalls = 0
        note = "No progress in $STALL_LIMIT sessions. Keep $nextReps reps and slow each rep down"
    }
    val (sets, streak, volumeNote) = adjustVolume(pe, outcome)
    return pe.copy(
        weight = null, targetReps = max(1, nextReps), sets = sets, sessions = pe.sessions + 1,
        stalls = stalls, streak = streak, note = note + volumeNote,
    )
}

/** New (sets, streak, note suffix) after a session. */
private fun adjustVolume(pe: ProgramExercise, outcome: Outcome): Triple<Int, Int, String> {
    val streak = if (outcome == Outcome.HIT) pe.streak + 1 else 0
    return when {
        outcome == Outcome.MISSED && pe.sets > max(1, pe.baseSets - 1) ->
            Triple(pe.sets - 1, 0, ". One set fewer to recover")
        streak >= HITS_PER_ADDED_SET && pe.sets < pe.baseSets + 2 ->
            Triple(pe.sets + 1, 0, ". One more set, you're recovering well")
        else -> Triple(pe.sets, streak, "")
    }
}

// ---------- generator ----------

private enum class Gear { BARBELL, DUMBBELL, CABLE, MACHINE, BODYWEIGHT, BANDS }

private fun gearFor(equipment: String): Set<Gear> = when (equipment) {
    Equipment.HOME -> setOf(Gear.DUMBBELL, Gear.BODYWEIGHT, Gear.BANDS)
    Equipment.BODYWEIGHT -> setOf(Gear.BODYWEIGHT)
    else -> setOf(Gear.BARBELL, Gear.DUMBBELL, Gear.CABLE, Gear.MACHINE, Gear.BODYWEIGHT)
}

/** Gear a library exercise needs; null for equipment programs don't plan around. */
private fun gearOf(e: Exercise): Gear? = when (e.equipment) {
    "barbell", "e-z curl bar" -> Gear.BARBELL
    "dumbbell", "kettlebells" -> Gear.DUMBBELL
    "cable" -> Gear.CABLE
    "machine" -> Gear.MACHINE
    "body only", null -> Gear.BODYWEIGHT
    "bands" -> Gear.BANDS
    else -> null
}

private class Candidate(val id: String, val gear: Gear)

/** Exercises per pattern in order of preference; slot variants walk this list. IDs are free-exercise-db ids. */
private val candidates: Map<String, List<Candidate>> = run {
    fun b(id: String) = Candidate(id, Gear.BARBELL)
    fun d(id: String) = Candidate(id, Gear.DUMBBELL)
    fun c(id: String) = Candidate(id, Gear.CABLE)
    fun m(id: String) = Candidate(id, Gear.MACHINE)
    fun bw(id: String) = Candidate(id, Gear.BODYWEIGHT)
    fun band(id: String) = Candidate(id, Gear.BANDS)
    mapOf(
        Pattern.SQUAT to listOf(
            b("Barbell_Squat"), m("Leg_Press"), m("Hack_Squat"), d("Goblet_Squat"), d("Dumbbell_Squat"),
            m("Smith_Machine_Squat"), bw("Bodyweight_Squat"),
        ),
        Pattern.HINGE to listOf(
            b("Barbell_Deadlift"), b("Romanian_Deadlift"), b("Barbell_Hip_Thrust"), d("Stiff-Legged_Dumbbell_Deadlift"),
            bw("Single_Leg_Glute_Bridge"), bw("Butt_Lift_Bridge"),
        ),
        Pattern.HORIZONTAL_PUSH to listOf(
            b("Barbell_Bench_Press_-_Medium_Grip"), d("Dumbbell_Bench_Press"), b("Barbell_Incline_Bench_Press_-_Medium_Grip"),
            d("Incline_Dumbbell_Press"), m("Machine_Bench_Press"), bw("Pushups"), bw("Push-Ups_With_Feet_Elevated"),
        ),
        Pattern.VERTICAL_PUSH to listOf(
            b("Standing_Military_Press"), d("Dumbbell_Shoulder_Press"), b("Seated_Barbell_Military_Press"),
            d("Arnold_Dumbbell_Press"), m("Machine_Shoulder_Military_Press"), bw("Push-Ups_With_Feet_Elevated"),
        ),
        Pattern.HORIZONTAL_PULL to listOf(
            b("Bent_Over_Barbell_Row"), c("Seated_Cable_Rows"), d("One-Arm_Dumbbell_Row"), d("Bent_Over_Two-Dumbbell_Row"),
            m("Leverage_Iso_Row"), bw("Inverted_Row"),
        ),
        Pattern.VERTICAL_PULL to listOf(
            c("Wide-Grip_Lat_Pulldown"), bw("Pullups"), c("Close-Grip_Front_Lat_Pulldown"), bw("Chin-Up"),
        ),
        Pattern.LUNGE to listOf(
            d("Split_Squat_with_Dumbbells"), d("Dumbbell_Lunges"), b("Barbell_Lunge"), d("Dumbbell_Step_Ups"),
            bw("Bodyweight_Walking_Lunge"),
        ),
        Pattern.LEG_CURL to listOf(m("Lying_Leg_Curls"), m("Seated_Leg_Curl")),
        Pattern.LEG_EXTENSION to listOf(m("Leg_Extensions"), m("Single-Leg_Leg_Extension")),
        Pattern.CALVES to listOf(
            m("Standing_Calf_Raises"), m("Seated_Calf_Raise"), d("Standing_Dumbbell_Calf_Raise"), d("Calf_Raise_On_A_Dumbbell"),
        ),
        Pattern.BICEPS to listOf(
            b("Barbell_Curl"), d("Dumbbell_Bicep_Curl"), d("Hammer_Curls"), d("Incline_Dumbbell_Curl"),
            c("Standing_Biceps_Cable_Curl"), bw("Chin-Up"),
        ),
        Pattern.TRICEPS to listOf(
            c("Triceps_Pushdown"), b("EZ-Bar_Skullcrusher"), c("Cable_Rope_Overhead_Triceps_Extension"),
            d("Standing_Dumbbell_Triceps_Extension"), d("Lying_Dumbbell_Tricep_Extension"), bw("Dips_-_Triceps_Version"),
            bw("Bench_Dips"), bw("Push-Ups_-_Close_Triceps_Position"),
        ),
        Pattern.LATERAL_DELTS to listOf(
            d("Side_Lateral_Raise"), c("Cable_Seated_Lateral_Raise"), d("Seated_Side_Lateral_Raise"), band("Lateral_Raise_-_With_Bands"),
        ),
        Pattern.REAR_DELTS to listOf(
            c("Face_Pull"), d("Reverse_Flyes"), d("Seated_Bent-Over_Rear_Delt_Raise"), m("Reverse_Machine_Flyes"),
            band("Band_Pull_Apart"),
        ),
        Pattern.CHEST_FLY to listOf(c("Cable_Crossover"), d("Dumbbell_Flyes"), m("Butterfly"), d("Incline_Dumbbell_Flyes")),
        Pattern.CORE to listOf(c("Cable_Crunch"), bw("Reverse_Crunch"), bw("Hanging_Leg_Raise"), bw("Dead_Bug"), bw("Crunches")),
    )
}

/** Every exercise id the generator can pick (for tests). */
internal val candidateIds: Set<String> get() = candidates.values.flatten().map { it.id }.toSet()

private class Slot(val pattern: String, val role: String, val variant: Int = 0)

private fun primary(pattern: String, variant: Int = 0) = Slot(pattern, Role.PRIMARY, variant)
private fun secondary(pattern: String, variant: Int = 0) = Slot(pattern, Role.SECONDARY, variant)
private fun accessory(pattern: String, variant: Int = 0) = Slot(pattern, Role.ACCESSORY, variant)

// Day templates, compound lifts first. Beginners get the first 5 exercises, intermediates 6, advanced 7.
private val fullA = listOf(
    primary(Pattern.SQUAT), primary(Pattern.HORIZONTAL_PUSH), secondary(Pattern.HORIZONTAL_PULL),
    accessory(Pattern.LEG_CURL), accessory(Pattern.LATERAL_DELTS), accessory(Pattern.CORE),
)
private val fullB = listOf(
    primary(Pattern.HINGE), primary(Pattern.VERTICAL_PUSH), secondary(Pattern.VERTICAL_PULL),
    secondary(Pattern.LUNGE), accessory(Pattern.BICEPS), accessory(Pattern.TRICEPS),
)
private val fullC = listOf(
    secondary(Pattern.SQUAT, 1), secondary(Pattern.HORIZONTAL_PUSH, 2), secondary(Pattern.HORIZONTAL_PULL, 1),
    secondary(Pattern.HINGE, 1), accessory(Pattern.REAR_DELTS), accessory(Pattern.CALVES),
)
private val upperA = listOf(
    primary(Pattern.HORIZONTAL_PUSH), primary(Pattern.HORIZONTAL_PULL), secondary(Pattern.VERTICAL_PUSH),
    secondary(Pattern.VERTICAL_PULL), accessory(Pattern.LATERAL_DELTS), accessory(Pattern.TRICEPS), accessory(Pattern.BICEPS),
)
private val upperB = listOf(
    primary(Pattern.VERTICAL_PUSH), primary(Pattern.VERTICAL_PULL, 2), secondary(Pattern.HORIZONTAL_PUSH, 2),
    secondary(Pattern.HORIZONTAL_PULL, 1), accessory(Pattern.CHEST_FLY), accessory(Pattern.REAR_DELTS), accessory(Pattern.BICEPS, 2),
)
private val lowerA = listOf(
    primary(Pattern.SQUAT), secondary(Pattern.HINGE, 1), secondary(Pattern.LUNGE),
    accessory(Pattern.LEG_CURL), accessory(Pattern.CALVES), accessory(Pattern.CORE),
)
private val lowerB = listOf(
    primary(Pattern.HINGE), secondary(Pattern.SQUAT, 1), secondary(Pattern.LUNGE, 1), accessory(Pattern.LEG_EXTENSION),
    accessory(Pattern.LEG_CURL, 1), accessory(Pattern.CALVES, 1), accessory(Pattern.CORE, 1),
)
private val push = listOf(
    primary(Pattern.HORIZONTAL_PUSH), secondary(Pattern.VERTICAL_PUSH), secondary(Pattern.HORIZONTAL_PUSH, 2),
    accessory(Pattern.LATERAL_DELTS), accessory(Pattern.TRICEPS), accessory(Pattern.CHEST_FLY), accessory(Pattern.TRICEPS, 2),
)
private val pull = listOf(
    primary(Pattern.VERTICAL_PULL), secondary(Pattern.HORIZONTAL_PULL), secondary(Pattern.HORIZONTAL_PULL, 1),
    accessory(Pattern.REAR_DELTS), accessory(Pattern.BICEPS), accessory(Pattern.BICEPS, 2), accessory(Pattern.CORE),
)
private val legs = listOf(
    primary(Pattern.SQUAT), secondary(Pattern.HINGE, 1), secondary(Pattern.LUNGE), accessory(Pattern.LEG_CURL),
    accessory(Pattern.LEG_EXTENSION), accessory(Pattern.CALVES), accessory(Pattern.CORE, 1),
)

private class DayPlan(val name: String, val slots: List<Slot>, val variantOffset: Int = 0)

private fun split(daysPerWeek: Int): List<DayPlan> = when (daysPerWeek) {
    2 -> listOf(DayPlan("Full Body A", fullA), DayPlan("Full Body B", fullB))
    3 -> listOf(DayPlan("Full Body A", fullA), DayPlan("Full Body B", fullB), DayPlan("Full Body C", fullC))
    4 -> listOf(DayPlan("Upper A", upperA), DayPlan("Lower A", lowerA), DayPlan("Upper B", upperB), DayPlan("Lower B", lowerB))
    5 -> listOf(
        DayPlan("Upper", upperA), DayPlan("Lower", lowerA),
        DayPlan("Push", push, 1), DayPlan("Pull", pull, 1), DayPlan("Legs", legs, 1),
    )
    else -> listOf(
        DayPlan("Push A", push), DayPlan("Pull A", pull), DayPlan("Legs A", legs),
        DayPlan("Push B", push, 1), DayPlan("Pull B", pull, 1), DayPlan("Legs B", legs, 1),
    )
}

private fun splitName(daysPerWeek: Int) = when (daysPerWeek) {
    2, 3 -> "Full Body"
    4 -> "Upper/Lower"
    5 -> "Upper/Lower + PPL"
    else -> "Push/Pull/Legs"
}

private fun repRange(goal: String, role: String, bodyweight: Boolean): IntRange = when {
    bodyweight -> if (role == Role.ACCESSORY) 10..20 else 8..15
    goal == TrainingGoal.STRENGTH -> when (role) {
        Role.PRIMARY -> 4..6
        Role.SECONDARY -> 6..10
        else -> 10..15
    }
    goal == TrainingGoal.GENERAL -> when (role) {
        Role.PRIMARY -> 5..8
        Role.SECONDARY -> 8..12
        else -> 10..15
    }
    else -> when (role) {
        Role.PRIMARY -> 6..10
        Role.SECONDARY -> 8..12
        else -> 10..15
    }
}

private fun baseSets(experience: String, role: String): Int = when {
    experience == Experience.BEGINNER && role == Role.ACCESSORY -> 2
    experience == Experience.ADVANCED && role == Role.PRIMARY -> 4
    else -> 3
}

private fun maxExercises(experience: String) = when (experience) {
    Experience.BEGINNER -> 5
    Experience.ADVANCED -> 7
    else -> 6
}

private fun restFor(role: String) = when (role) {
    Role.PRIMARY -> 180
    Role.SECONDARY -> 120
    else -> 90
}

/** RIR the starting weight is estimated at: a little conservative. */
private const val STARTING_RIR = 3

private fun pick(
    pattern: String,
    variant: Int,
    gear: Set<Gear>,
    used: Set<String>,
    experience: String,
    exercise: (String) -> Exercise?,
): Candidate? {
    val options = candidates[pattern].orEmpty().filter { c ->
        val e = exercise(c.id)
        c.gear in gear && e != null && !(experience == Experience.BEGINNER && e.level == "expert")
    }
    if (options.isEmpty()) return null
    return options.indices.map { options[(variant + it) % options.size] }.firstOrNull { it.id !in used }
}

private fun newProgramExercise(
    c: Candidate,
    pattern: String,
    role: String,
    goal: String,
    experience: String,
    unit: String,
    exercise: (String) -> Exercise?,
    best1RM: (String) -> Double?,
): ProgramExercise {
    val bodyweight = c.gear == Gear.BODYWEIGHT
    val range = repRange(goal, role, bodyweight)
    val sets = baseSets(experience, role)
    val weight = if (bodyweight) null
    else startingWeight(best1RM(c.id), range.first, STARTING_RIR, loadStep(exercise(c.id)?.equipment, pattern, unit))
    return ProgramExercise(
        exerciseId = c.id, pattern = pattern, role = role, sets = sets, baseSets = sets,
        repMin = range.first, repMax = range.last, targetReps = range.first, weight = weight,
        restSeconds = restFor(role), note = if (weight != null) "Starting weight estimated from your history" else "",
    )
}

/**
 * Builds a program. [exercise] looks up the library (candidates missing from it are skipped);
 * [best1RM] gives an estimated one-rep max from the user's history, used for starting weights.
 */
fun generateProgram(
    goal: String,
    experience: String,
    daysPerWeek: Int,
    equipment: String,
    unit: String,
    exercise: (String) -> Exercise?,
    best1RM: (String) -> Double? = { null },
): Program {
    val n = daysPerWeek.coerceIn(2, 6)
    val gear = gearFor(equipment)
    val days = split(n).map { plan ->
        val used = HashSet<String>()
        val exercises = plan.slots.mapNotNull { slot ->
            val c = pick(slot.pattern, slot.variant + plan.variantOffset, gear, used, experience, exercise)
                ?: return@mapNotNull null
            used.add(c.id)
            newProgramExercise(c, slot.pattern, slot.role, goal, experience, unit, exercise, best1RM)
        }.take(maxExercises(experience))
        ProgramDay(name = plan.name, exercises = exercises)
    }
    return Program(
        name = "${splitName(n)} · $n days",
        goal = goal,
        experience = experience,
        equipment = equipment,
        days = days,
    )
}

/** Replacements for [pe]: the same movement pattern first, then other exercises for the same main muscle. */
fun swapOptions(pe: ProgramExercise, equipment: String, library: List<Exercise>): List<Exercise> {
    val gear = gearFor(equipment)
    val byId = library.associateBy { it.id }
    val curated = candidates[pe.pattern].orEmpty().filter { it.gear in gear }.mapNotNull { byId[it.id] }
    val muscle = byId[pe.exerciseId]?.primaryMuscles?.firstOrNull() ?: curated.firstOrNull()?.primaryMuscles?.firstOrNull()
    val related = library.filter { e ->
        (e.category == "strength" || e.category == "powerlifting") &&
            e.primaryMuscles.firstOrNull() == muscle && gearOf(e)?.let { it in gear } == true
    }
    return (curated + related).distinctBy { it.id }.filter { it.id != pe.exerciseId }
}

/** [pe] with [e] swapped in: progression restarts, with a starting weight from history when there is one. */
fun ProgramExercise.swapTo(e: Exercise, goal: String, unit: String, oneRepMax: Double?): ProgramExercise {
    val bodyweight = gearOf(e) == Gear.BODYWEIGHT
    val range = repRange(goal, role, bodyweight)
    val weight = if (bodyweight) null else startingWeight(oneRepMax, range.first, STARTING_RIR, loadStep(e.equipment, pattern, unit))
    return copy(
        exerciseId = e.id, repMin = range.first, repMax = range.last, targetReps = range.first, weight = weight,
        sessions = 0, stalls = 0, streak = 0,
        note = if (weight != null) "Swapped in, starting weight from your history" else "Swapped in",
    )
}
