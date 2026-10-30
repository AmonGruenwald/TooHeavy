package com.tooheavy.app.data

import com.google.gson.annotations.SerializedName
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Adaptive training programs, built on the main findings of resistance-training research:
//
// - Volume: hypertrophy rises with weekly hard sets per muscle up to roughly 10-20 sets, with
//   diminishing returns beyond (Schoenfeld 2017, Baz-Valle 2022, Pelland 2024). Beginners grow on less.
//   The generator balances every muscle group's weekly sets into a range for the lifter's experience,
//   counting indirect work from compound lifts as half a set.
// - Frequency: training a muscle at least twice a week beats once (Schoenfeld 2016), so the splits
//   hit each group about twice per week.
// - Load and reps: similar growth from about 6 to 30 reps when sets end close to failure (Schoenfeld
//   2017); strength favours heavier loads. Rep ranges follow the goal and the exercise's role.
// - Effort: 0-3 reps in reserve (Refalo 2023, Helms 2016). Blocks ramp from 3 RIR to 1 RIR, and logged
//   RIR steers progression: reps to spare speed it up, sets ground out at failure hold it back.
// - Rest: 2-3 minutes for compound lifts, shorter for isolation work (Schoenfeld 2016, Singer 2024).
// - Fatigue: deloads every 4-6 weeks (Bell 2023). A deload comes early when stalls pile up.
// - Order: prioritised muscles are trained early in the session, while fresh (Simão 2012).
// - Maintenance: about a third of the usual volume, even once a week, keeps muscle and strength
//   (Bickel 2011, Spiering 2021), so reduced muscle groups get 3-6 weekly sets.
//
// Adaptation runs at two speeds. After every workout, double progression sets load and reps, sets
// follow performance, and repeated stalls reset the weight. At the end of every block, each exercise's
// base volume moves toward what the lifter recovered from, and the next block gets longer when progress
// was steady or shorter when the deload had to come early.

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

object Split {
    const val AUTO = "auto"
    const val FULL_BODY = "full_body"
    const val UPPER_LOWER = "upper_lower"
    const val PPL = "ppl"
    /** Upper and lower days plus push, pull and legs; what AUTO picks for 5 days. */
    const val HYBRID = "hybrid"
    val choices = listOf(AUTO, FULL_BODY, UPPER_LOWER, PPL)
    fun resolve(split: String, days: Int): String =
        if (split != AUTO) split
        else when (days) {
            2, 3 -> FULL_BODY
            4 -> UPPER_LOWER
            5 -> HYBRID
            else -> PPL
        }
    fun label(split: String) = when (split) {
        FULL_BODY -> "Full body"
        UPPER_LOWER -> "Upper/Lower"
        PPL -> "Push/Pull/Legs"
        HYBRID -> "Upper/Lower + PPL"
        else -> "Recommended"
    }
    fun hint(split: String, days: Int) = when (split) {
        FULL_BODY -> "Every session trains the whole body. Suits 2 to 4 days."
        UPPER_LOWER -> "Alternating upper- and lower-body days. Suits 4 days."
        PPL -> "Push, pull and leg days. Suits 3 or 6 days."
        else -> "${label(resolve(AUTO, days))} for $days days a week"
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
    const val GLUTES = "glutes"
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
    /** Shared by isolation exercises paired into a superset in short sessions; null when it stands alone. */
    val supersetId: String? = null,
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
    /** Training weeks per mesocycle before its deload week; adapts between 3 and 6 from block to block. */
    val mesoWeeks: Int = 4,
    val sessionsCompleted: Int = 0,
    /** Index into days of the last finished session, -1 before the first. */
    val lastDayIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    /** Split.* the lifter chose. */
    val split: String = Split.AUTO,
    /** MuscleGroup.* to prioritise: more weekly sets, trained first. */
    val focus: List<String> = emptyList(),
    /** MuscleGroup.* kept at maintenance volume. Programs from 1.7.0 stored these as "skip". */
    @SerializedName(value = "reduce", alternate = ["skip"])
    val reduce: List<String> = emptyList(),
    /** Sessions per week; 0 means one pass through [days]. */
    val perWeek: Int = 0,
    /** sessionsCompleted when the current mesocycle began. */
    val mesoStart: Int = 0,
    /** 0-based number of the current mesocycle. */
    val mesoIndex: Int = 0,
    /** Training weeks of the current block when fatigue brought the deload forward, else 0. */
    val blockWeeks: Int = 0,
    /** Minutes a session should take at most; 0 for no limit. */
    val minutes: Int = 0,
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

val Program.sessionsPerWeek: Int get() = if (perWeek > 0) perWeek else max(1, days.size)
/** Training weeks before this block's deload. */
val Program.trainingWeeks: Int get() = if (blockWeeks > 0) blockWeeks else mesoWeeks
/** 0-based week within the current mesocycle. */
val Program.weekIndex: Int get() = max(0, sessionsCompleted - mesoStart) / sessionsPerWeek
val Program.mesocycle: Int get() = mesoIndex + 1
/** 1-based week within the mesocycle; trainingWeeks + 1 is the deload. */
val Program.mesoWeek: Int get() = weekIndex + 1
val Program.isDeload: Boolean get() = weekIndex >= trainingWeeks

/** Each day's exercises in the order [orderSession] suggests; programs from before 1.8.0 get it on load. */
fun Program.ordered(): Program = copy(days = days.map { d ->
    d.copy(exercises = orderSession(d.exercises.map { pe ->
        val g = patternGroup(pe.pattern)
        val priority = if (g in focus) 0 else if (g in reduce) 2 else 1
        OrderItem(pe, programTier(pe), patternGroups[pe.pattern].orEmpty().toSet(), priority, pe.supersetId, setOfNotNull(g))
    }))
})

/** Programs saved before 1.7.0 counted mesocycles from the start; this moves them to a stored block start. */
fun Program.migrated(): Program {
    if (perWeek != 0 || mesoStart != 0 || mesoIndex != 0 || days.isEmpty()) return this
    val cycle = (mesoWeeks + 1) * days.size
    val n = sessionsCompleted / cycle
    return if (n == 0) this else copy(mesoIndex = n, mesoStart = n * cycle)
}
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
                supersetId = pe.supersetId,
                sets = List(t.sets) { SetEntry(weight = t.weight, reps = t.reps) },
            )
        },
    )
}

fun Program.replaceExercise(updated: ProgramExercise): Program =
    copy(days = days.map { d -> d.copy(exercises = d.exercises.map { if (it.id == updated.id) updated else it }) })

// ---------- loads ----------

/** [increment] is the jump when a lift moves up; [rounding] is the smallest practical load change. */
data class LoadStep(val increment: Double, val rounding: Double)

fun loadStep(equipment: String?, pattern: String, unit: String): LoadStep {
    val lb = unit == "lb"
    val lower = pattern == Pattern.SQUAT || pattern == Pattern.HINGE || pattern == Pattern.LUNGE || pattern == Pattern.GLUTES
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
    when {
        next.weekIndex > next.trainingWeeks -> {
            val (reviewed, review) = next.nextBlock(exercise)
            next = reviewed
            changes.add("Deload done. Mesocycle ${next.mesocycle} starts with ${next.mesoWeeks} training weeks.")
            changes.addAll(review)
        }
        next.isDeload && !isDeload -> changes.add("Next week is a deload: about half the sets at 90 % of the weight.")
        !next.isDeload -> next.fatigueCheck()?.let { (p, message) ->
            next = p
            changes.add(message)
        }
    }
    return next to changes
}

/** Share of trained exercises that missed their target twice in a row which brings the deload forward. */
private const val FATIGUE_SHARE = 0.4

/**
 * Brings the deload forward when fatigue piles up: from the third week on, once 40 % of the trained
 * exercises have missed their targets twice in a row, the current week becomes the block's last.
 */
private fun Program.fatigueCheck(): Pair<Program, String>? {
    if (blockWeeks > 0 || weekIndex < 2 || weekIndex + 1 >= trainingWeeks) return null
    val trained = days.flatMap { it.exercises }.filter { it.sessions > 0 }
    if (trained.size < 3) return null
    val stalled = trained.count { it.stalls >= 2 }
    if (stalled < trained.size * FATIGUE_SHARE) return null
    return copy(blockWeeks = weekIndex + 1) to
        "Fatigue is building: $stalled of ${trained.size} exercises missed their targets twice in a row. " +
        "The deload comes after this week instead of after week $trainingWeeks."
}

/**
 * Starts the next mesocycle and adapts the plan to how the last one went. Exercises that earned extra sets
 * without stalling keep one of them as their new base; exercises that had to drop a set start a set lower.
 * The block gets a week longer after steady progress and a week shorter after an early deload.
 */
private fun Program.nextBlock(exercise: (String) -> Exercise?): Pair<Program, List<String>> {
    val notes = ArrayList<String>()
    val trained = days.flatMap { it.exercises }.filter { it.sessions > 0 }
    val early = blockWeeks in 1 until mesoWeeks
    val steady = trained.isNotEmpty() && trained.count { it.stalls == 0 } >= trained.size * 0.8
    val weeks = when {
        early -> max(MIN_BLOCK_WEEKS, mesoWeeks - 1)
        steady -> min(MAX_BLOCK_WEEKS, mesoWeeks + 1)
        else -> mesoWeeks
    }
    if (weeks > mesoWeeks) notes.add("You progressed steadily, so this block runs a week longer.")
    if (weeks < mesoWeeks) notes.add("The last deload had to come early, so this block is a week shorter.")
    val reviewed = days.map { d ->
        d.copy(exercises = d.exercises.map { pe ->
            val base = when {
                pe.sessions == 0 -> pe.baseSets
                pe.sets > pe.baseSets && pe.stalls == 0 -> min(MAX_SETS, pe.baseSets + 1)
                pe.sets < pe.baseSets -> max(MIN_SETS, pe.baseSets - 1)
                else -> pe.baseSets
            }
            val name = exercise(pe.exerciseId)?.name ?: "Exercise"
            if (base > pe.baseSets) notes.add("$name: $base sets per session now, you recovered well from more.")
            if (base < pe.baseSets) notes.add("$name: $base sets per session now, fewer suited you better.")
            pe.copy(baseSets = base, sets = base, streak = 0)
        })
    }
    val p = copy(
        days = balanceVolume(
            reviewed, sessionsPerWeek, goal, experience, focus, reduce, minutes,
            { day -> if (day.exercises.none { it.role == Role.ACCESSORY }) MIN_SETS else compoundFloor(minutes) },
            raise = false,
        ),
        mesoWeeks = weeks, mesoStart = sessionsCompleted, mesoIndex = mesoIndex + 1, blockWeeks = 0,
    )
    return p to notes
}

/**
 * Next prescription for [pe] from the sets completed in [we], judged against what was asked for.
 *
 * Load uses double progression: reps climb toward the top of the range at the same weight, and once
 * every set reaches the top the weight goes up and reps restart at the bottom. Three sessions in a row
 * without hitting the target drop the weight by about 10 % to build back up.
 * Volume follows performance: two hits in a row add a set (up to baseSets + 2), falling below the
 * range removes one (down to baseSets - 1).
 * Logged reps in reserve autoregulate the steps: when every set with a logged RIR had 2 or more reps to
 * spare beyond the target, the step doubles (two reps, or two weight increments); when they were all
 * ground out at 0 RIR although the target was 2 or more, the reps hold where they were.
 */
internal fun progressExercise(pe: ProgramExercise, we: WorkoutExercise, unit: String, equipment: String?): ProgramExercise {
    val working = we.sets.filter { it.completed && it.type != SetType.WARMUP && (it.reps ?: 0) > 0 }
    if (working.isEmpty()) return pe.copy(note = "Skipped last time, same targets")
    val target = we.target
    val tSets = target?.sets ?: pe.sets
    val tReps = target?.reps ?: pe.targetReps
    val tWeight = target?.weight ?: pe.weight
    val effort = effortOf(working, target?.rir ?: 2)
    val top = working.maxOf { it.weight ?: 0.0 }
    return if (top <= 0.0) progressBodyweight(pe, working, tSets, tReps, effort)
    else progressLoaded(pe, working, top, tSets, tReps, tWeight, loadStep(equipment, pe.pattern, unit), unit, effort)
}

/** How hard the working sets felt from the logged RIR, NORMAL when none was logged. */
private enum class Effort { NORMAL, EASY, GRINDING }

private fun effortOf(working: List<SetEntry>, targetRir: Int): Effort {
    val rirs = working.mapNotNull { it.rir }
    return when {
        rirs.isEmpty() -> Effort.NORMAL
        rirs.min() >= targetRir + 2 -> Effort.EASY
        targetRir >= 2 && rirs.max() == 0 -> Effort.GRINDING
        else -> Effort.NORMAL
    }
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
    effort: Effort,
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
            val jumps = if (effort == Effort.EASY) 2 else 1
            weight = roundLoad(top + step.increment * jumps, step.rounding)
            nextReps = pe.repMin
            note = "+${formatWeight(weight - top)} $unit after ${pe.repMax} reps on every set" +
                if (effort == Effort.EASY) " with reps to spare" else ""
        }
        minReps >= pe.repMax -> {
            nextReps = pe.repMax
            note = "Finish all $tSets sets at ${pe.repMax} reps to move up"
        }
        minReps >= pe.repMin && effort == Effort.GRINDING -> {
            nextReps = max(min(minReps, pe.repMax), min(tReps, pe.repMax))
            note = "Same weight and $nextReps reps: the sets went to failure, so own them first"
        }
        minReps >= pe.repMin -> {
            nextReps = min(minReps + if (effort == Effort.EASY) 2 else 1, pe.repMax)
            note = "Same weight, aim for $nextReps reps" + if (effort == Effort.EASY) " (you had reps to spare)" else ""
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

private fun progressBodyweight(pe: ProgramExercise, working: List<SetEntry>, tSets: Int, tReps: Int, effort: Effort): ProgramExercise {
    val reps = working.map { it.reps ?: 0 }
    val minReps = reps.min()
    val allSets = reps.size >= tSets
    val outcome = when {
        pe.sessions == 0 -> Outcome.BASELINE
        allSets && minReps >= tReps -> Outcome.HIT
        reps.sum() >= tSets * tReps * 0.9 -> Outcome.HELD
        else -> Outcome.MISSED
    }
    val nextReps = when {
        !allSets || effort == Effort.GRINDING -> minReps
        effort == Effort.EASY -> minReps + 2
        else -> minReps + 1
    }
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
        streak >= HITS_PER_ADDED_SET && pe.sets < min(pe.baseSets + 2, MAX_SETS + 1) ->
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
            b("Barbell_Squat"), m("Leg_Press"), m("Hack_Squat"), m("Pendulum_Squat"), d("Goblet_Squat"), m("Belt_Squat"),
            d("Dumbbell_Squat"), m("Smith_Machine_Squat"), bw("Bodyweight_Squat"),
        ),
        Pattern.HINGE to listOf(
            b("Barbell_Deadlift"), b("Romanian_Deadlift"), b("Barbell_Hip_Thrust"), d("Dumbbell_Romanian_Deadlift"),
            m("Smith_Machine_Romanian_Deadlift"), d("Single-Leg_Dumbbell_Romanian_Deadlift"), d("Kettlebell_Swing"),
            bw("Single_Leg_Glute_Bridge"), bw("Butt_Lift_Bridge"),
        ),
        Pattern.HORIZONTAL_PUSH to listOf(
            b("Barbell_Bench_Press_-_Medium_Grip"), d("Dumbbell_Bench_Press"), b("Barbell_Incline_Bench_Press_-_Medium_Grip"),
            d("Incline_Dumbbell_Press"), m("Machine_Bench_Press"), bw("Pushups"), bw("Push-Ups_With_Feet_Elevated"),
        ),
        Pattern.VERTICAL_PUSH to listOf(
            b("Standing_Military_Press"), d("Dumbbell_Shoulder_Press"), b("Seated_Barbell_Military_Press"),
            d("Arnold_Dumbbell_Press"), m("Machine_Shoulder_Military_Press"), b("Landmine_Press"), bw("Push-Ups_With_Feet_Elevated"),
        ),
        Pattern.HORIZONTAL_PULL to listOf(
            b("Bent_Over_Barbell_Row"), c("Seated_Cable_Rows"), d("One-Arm_Dumbbell_Row"), m("Chest-Supported_Machine_Row"),
            d("Bent_Over_Two-Dumbbell_Row"), c("Single-Arm_Cable_Row"), b("Pendlay_Row"), b("Seal_Row"), m("Leverage_Iso_Row"),
            bw("Inverted_Row"),
        ),
        Pattern.VERTICAL_PULL to listOf(
            c("Wide-Grip_Lat_Pulldown"), bw("Pullups"), c("Close-Grip_Front_Lat_Pulldown"), bw("Chin-Up"), m("Assisted_Pull-Up"),
        ),
        Pattern.LUNGE to listOf(
            d("Split_Squat_with_Dumbbells"), d("Bulgarian_Split_Squat"), d("Dumbbell_Lunges"), d("Dumbbell_Rear_Lunge"),
            b("Barbell_Lunge"), d("Dumbbell_Step_Ups"), bw("Bodyweight_Walking_Lunge"),
        ),
        Pattern.LEG_CURL to listOf(m("Lying_Leg_Curls"), m("Seated_Leg_Curl"), bw("Nordic_Hamstring_Curl")),
        Pattern.LEG_EXTENSION to listOf(m("Leg_Extensions"), m("Single-Leg_Leg_Extension"), bw("Reverse_Nordic")),
        Pattern.CALVES to listOf(
            m("Standing_Calf_Raises"), m("Seated_Calf_Raise"), d("Standing_Dumbbell_Calf_Raise"), d("Calf_Raise_On_A_Dumbbell"),
            m("Calf_Press_On_The_Leg_Press_Machine"),
        ),
        Pattern.BICEPS to listOf(
            b("Barbell_Curl"), d("Dumbbell_Bicep_Curl"), d("Hammer_Curls"), d("Incline_Dumbbell_Curl"),
            c("Bayesian_Cable_Curl"), c("Standing_Biceps_Cable_Curl"), bw("Chin-Up"),
        ),
        Pattern.TRICEPS to listOf(
            c("Triceps_Pushdown"), b("EZ-Bar_Skullcrusher"), c("Cable_Rope_Overhead_Triceps_Extension"),
            d("Standing_Dumbbell_Triceps_Extension"), d("Lying_Dumbbell_Tricep_Extension"), bw("Dips_-_Triceps_Version"),
            c("Cable_Triceps_Kickback"), bw("Bench_Dips"), bw("Push-Ups_-_Close_Triceps_Position"),
        ),
        Pattern.LATERAL_DELTS to listOf(
            d("Side_Lateral_Raise"), c("Single-Arm_Cable_Lateral_Raise"), m("Machine_Lateral_Raise"), c("Cable_Seated_Lateral_Raise"),
            d("Seated_Side_Lateral_Raise"), c("Cable_Y-Raise"), band("Lateral_Raise_-_With_Bands"),
        ),
        Pattern.REAR_DELTS to listOf(
            c("Face_Pull"), d("Reverse_Flyes"), d("Seated_Bent-Over_Rear_Delt_Raise"), m("Reverse_Machine_Flyes"),
            band("Band_Pull_Apart"),
        ),
        Pattern.CHEST_FLY to listOf(c("Cable_Crossover"), d("Dumbbell_Flyes"), m("Butterfly"), d("Incline_Dumbbell_Flyes")),
        Pattern.GLUTES to listOf(
            b("Barbell_Hip_Thrust"), m("Machine_Hip_Thrust"), c("Pull_Through"), m("Smith_Machine_Hip_Thrust"),
            b("Barbell_Glute_Bridge"), c("One-Legged_Cable_Kickback"), c("Cable_Hip_Abduction"),
            bw("Single_Leg_Glute_Bridge"), bw("Glute_Kickback"), bw("Butt_Lift_Bridge"),
        ),
        Pattern.CORE to listOf(
            c("Cable_Crunch"), bw("Reverse_Crunch"), bw("Hanging_Knee_Raise"), c("Cable_Woodchopper"), bw("Hanging_Leg_Raise"),
            bw("Dead_Bug"), bw("Crunches"),
        ),
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

private val allGroups = MuscleGroup.all.toSet()
private val upperGroups = setOf(
    MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.ABS,
)
private val lowerGroups = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES, MuscleGroup.ABS)
private val pushGroups = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
private val pullGroups = setOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.SHOULDERS, MuscleGroup.ABS)

/** A day in the rotation; [groups] are the muscle groups it may train when filling or adding focus work. */
private class DayPlan(val name: String, val slots: List<Slot>, val groups: Set<String>, val variantOffset: Int = 0)

private fun fullBody(i: Int) = DayPlan("Full Body ${'A' + i}", listOf(fullA, fullB, fullC)[i % 3], allGroups, i / 3)

/** The rotation of days for [split]; the program runs [daysPerWeek] sessions of it per week. */
private fun rotation(split: String, daysPerWeek: Int): List<DayPlan> = when (split) {
    Split.FULL_BODY -> List(daysPerWeek) { fullBody(it) }
    Split.UPPER_LOWER -> {
        val week = listOf(
            DayPlan("Upper A", upperA, upperGroups), DayPlan("Lower A", lowerA, lowerGroups),
            DayPlan("Upper B", upperB, upperGroups), DayPlan("Lower B", lowerB, lowerGroups),
        )
        when {
            daysPerWeek <= 2 -> listOf(DayPlan("Upper", upperA, upperGroups), DayPlan("Lower", lowerA, lowerGroups))
            daysPerWeek <= 5 -> week
            else -> week + listOf(DayPlan("Upper C", upperA, upperGroups, 1), DayPlan("Lower C", lowerA, lowerGroups, 1))
        }
    }
    Split.HYBRID -> listOf(
        DayPlan("Upper", upperA, upperGroups), DayPlan("Lower", lowerA, lowerGroups),
        DayPlan("Push", push, pushGroups, 1), DayPlan("Pull", pull, pullGroups, 1), DayPlan("Legs", legs, lowerGroups, 1),
    )
    else ->
        if (daysPerWeek <= 3) listOf(DayPlan("Push", push, pushGroups), DayPlan("Pull", pull, pullGroups), DayPlan("Legs", legs, lowerGroups))
        else listOf(
            DayPlan("Push A", push, pushGroups), DayPlan("Pull A", pull, pullGroups), DayPlan("Legs A", legs, lowerGroups),
            DayPlan("Push B", push, pushGroups, 1), DayPlan("Pull B", pull, pullGroups, 1), DayPlan("Legs B", legs, lowerGroups, 1),
        )
}

/** Muscle groups each pattern works: the main one first, then those it trains indirectly. */
private val patternGroups: Map<String, List<String>> = mapOf(
    Pattern.SQUAT to listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
    Pattern.HINGE to listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.BACK),
    Pattern.LUNGE to listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
    Pattern.LEG_CURL to listOf(MuscleGroup.HAMSTRINGS),
    Pattern.LEG_EXTENSION to listOf(MuscleGroup.QUADS),
    Pattern.CALVES to listOf(MuscleGroup.CALVES),
    Pattern.GLUTES to listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
    Pattern.HORIZONTAL_PUSH to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
    Pattern.VERTICAL_PUSH to listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
    Pattern.CHEST_FLY to listOf(MuscleGroup.CHEST),
    Pattern.HORIZONTAL_PULL to listOf(MuscleGroup.BACK, MuscleGroup.BICEPS),
    Pattern.VERTICAL_PULL to listOf(MuscleGroup.BACK, MuscleGroup.BICEPS),
    Pattern.BICEPS to listOf(MuscleGroup.BICEPS),
    Pattern.TRICEPS to listOf(MuscleGroup.TRICEPS),
    Pattern.LATERAL_DELTS to listOf(MuscleGroup.SHOULDERS),
    Pattern.REAR_DELTS to listOf(MuscleGroup.SHOULDERS),
    Pattern.CORE to listOf(MuscleGroup.ABS),
)

/** The muscle group a pattern mainly trains. */
fun patternGroup(pattern: String): String? = patternGroups[pattern]?.firstOrNull()

/** Extra work per group, in order of preference, for prioritised groups and for days that reducing left thin. */
private val groupSlots: Map<String, List<Slot>> = mapOf(
    MuscleGroup.CHEST to listOf(secondary(Pattern.HORIZONTAL_PUSH, 1), accessory(Pattern.CHEST_FLY)),
    MuscleGroup.BACK to listOf(secondary(Pattern.HORIZONTAL_PULL, 1), secondary(Pattern.VERTICAL_PULL, 1)),
    MuscleGroup.SHOULDERS to listOf(accessory(Pattern.LATERAL_DELTS), secondary(Pattern.VERTICAL_PUSH, 1), accessory(Pattern.REAR_DELTS)),
    MuscleGroup.BICEPS to listOf(accessory(Pattern.BICEPS, 1)),
    MuscleGroup.TRICEPS to listOf(accessory(Pattern.TRICEPS, 1)),
    MuscleGroup.ABS to listOf(accessory(Pattern.CORE, 1)),
    MuscleGroup.QUADS to listOf(accessory(Pattern.LEG_EXTENSION), secondary(Pattern.SQUAT, 1), secondary(Pattern.LUNGE)),
    MuscleGroup.HAMSTRINGS to listOf(accessory(Pattern.LEG_CURL), secondary(Pattern.HINGE, 1)),
    MuscleGroup.GLUTES to listOf(secondary(Pattern.GLUTES), accessory(Pattern.GLUTES, 1)),
    MuscleGroup.CALVES to listOf(accessory(Pattern.CALVES)),
)

/** Main lifts, then other compounds, then isolation work, with core and calves last; see [orderSession]. */
private fun programTier(pe: ProgramExercise) = when {
    pe.role == Role.PRIMARY -> 1
    pe.role == Role.SECONDARY -> 2
    pe.pattern == Pattern.CORE || pe.pattern == Pattern.CALVES -> 4
    else -> 3
}

private fun roleRank(role: String) = when (role) {
    Role.PRIMARY -> 0
    Role.SECONDARY -> 1
    else -> 2
}

private const val MIN_SETS = 2
private const val MAX_SETS = 5
private const val MIN_BLOCK_WEEKS = 3
private const val MAX_BLOCK_WEEKS = 6

/**
 * Weekly sets a muscle group should get: beginners grow on less, advanced lifters need more (Schoenfeld
 * 2017, Baz-Valle 2022). Prioritised groups get about four more; strength programs lean on load over volume.
 */
fun weeklySetRange(
    goal: String,
    experience: String,
    focused: Boolean,
    group: String = "",
    reduced: Boolean = false,
): ClosedFloatingPointRange<Double> {
    if (reduced) return MAINTENANCE
    var (lo, hi) = when (experience) {
        Experience.BEGINNER -> 6.0 to 12.0
        Experience.ADVANCED -> 12.0 to 20.0
        else -> 10.0 to 16.0
    }
    if (goal == TrainingGoal.STRENGTH) { lo -= 2; hi -= 2 }
    // Abs and calves get plenty of indirect work from compound lifts, so they need less direct volume.
    if (group == MuscleGroup.ABS || group == MuscleGroup.CALVES) { lo = max(4.0, lo - 4); hi -= 4 }
    if (focused) { lo += 4; hi += 4 }
    return lo..hi
}

/**
 * Working sets per session: beyond about 20 the quality of later sets drops and sessions run long
 * (per-session volume shows diminishing returns past roughly 10 sets per muscle). With 3 days or fewer a
 * week, each session has to carry more of the week's work.
 */
private fun sessionBudget(experience: String, focusGroups: Int, perWeek: Int) = when (experience) {
    Experience.BEGINNER -> 15
    Experience.ADVANCED -> 24
    else -> 20
} + min(6, 2 * focusGroups) + (if (perWeek <= 3) 4 else 0)

/** Seconds a working set takes before its rest starts, loading the bar included. */
private const val SET_SECONDS = 50

/** Setting up, adjusting a machine or walking to the next station. */
private const val CHANGEOVER_SECONDS = 90

/** Sessions never drop below this many exercises to fit a time limit. */
private const val MIN_EXERCISES = 3

/** Warm-up time before an exercise: three ramp-up sets before a main lift, one before other compounds. */
private fun warmupSeconds(pe: ProgramExercise) = when (pe.role) {
    Role.PRIMARY -> 240
    Role.SECONDARY -> 90
    else -> 0
}

/**
 * Seconds a session takes: every working set, warm-ups, moving between exercises, and the rests. Exercises in
 * a superset share one rest per round, the longest of the group.
 */
private fun sessionSeconds(exercises: List<ProgramExercise>): Int {
    var total = 0
    var i = 0
    while (i < exercises.size) {
        val id = exercises[i].supersetId
        var j = i
        while (id != null && j + 1 < exercises.size && exercises[j + 1].supersetId == id) j++
        val group = exercises.subList(i, j + 1)
        total += group.sumOf { it.sets * SET_SECONDS + warmupSeconds(it) + CHANGEOVER_SECONDS } +
            (group.maxOf { it.sets } - 1) * group.maxOf { it.restSeconds }
        i = j + 1
    }
    return total
}

/** About how long the session takes: every working set and its rest, warm-ups, and moving between exercises. */
val ProgramDay.estimatedMinutes: Int get() = (sessionSeconds(exercises) / 60.0).roundToInt()

/** Sessions of this many minutes or fewer are run the time-efficient way: compounds first, paired isolation work. */
private const val SHORT_SESSION = 45

/**
 * The fewest sets a compound lift is cut to for time. Short sessions keep 3 on compounds and drop isolation
 * exercises first: compounds train several muscles per set, the core of time-efficient training (Iversen 2021).
 */
private fun compoundFloor(minutes: Int) = if (minutes in 1..SHORT_SESSION) 3 else MIN_SETS

/**
 * Pairs isolation exercises for different muscle groups into supersets, so one rest covers both, as
 * time-efficient training suggests (Iversen 2021). Compound lifts keep their full rests.
 */
private fun pairIsolation(list: List<ProgramExercise>): List<ProgramExercise> {
    val rest = list.filter { it.role == Role.ACCESSORY }.toMutableList()
    val paired = ArrayList<ProgramExercise>()
    while (rest.isNotEmpty()) {
        val a = rest.removeAt(0)
        val j = rest.indexOfFirst { patternGroup(it.pattern) != patternGroup(a.pattern) }
        if (j < 0) {
            paired.add(a)
            continue
        }
        val id = newId()
        paired.add(a.copy(supersetId = id))
        paired.add(rest.removeAt(j).copy(supersetId = id))
    }
    return list.filter { it.role != Role.ACCESSORY } + paired
}

/** Weekly sets that maintain a muscle group: about a third of the usual volume. */
val MAINTENANCE = 3.0..6.0

/**
 * One exercise per reduced group, preferring compound lifts that also cover other reduced groups: a squat
 * keeps quads and glutes, a Romanian deadlift hamstrings and glutes.
 */
private val maintenanceSlots: Map<String, List<Slot>> = mapOf(
    MuscleGroup.CHEST to listOf(secondary(Pattern.HORIZONTAL_PUSH)),
    MuscleGroup.BACK to listOf(secondary(Pattern.HORIZONTAL_PULL), secondary(Pattern.VERTICAL_PULL)),
    MuscleGroup.SHOULDERS to listOf(secondary(Pattern.VERTICAL_PUSH), accessory(Pattern.LATERAL_DELTS)),
    MuscleGroup.BICEPS to listOf(accessory(Pattern.BICEPS)),
    MuscleGroup.TRICEPS to listOf(accessory(Pattern.TRICEPS)),
    MuscleGroup.ABS to listOf(accessory(Pattern.CORE)),
    MuscleGroup.QUADS to listOf(secondary(Pattern.SQUAT), accessory(Pattern.LEG_EXTENSION)),
    MuscleGroup.HAMSTRINGS to listOf(secondary(Pattern.HINGE, 1), accessory(Pattern.LEG_CURL)),
    MuscleGroup.GLUTES to listOf(secondary(Pattern.GLUTES), secondary(Pattern.HINGE, 1)),
    MuscleGroup.CALVES to listOf(accessory(Pattern.CALVES)),
)

/** Planned weekly sets per muscle group: direct sets count fully, indirect work from compound lifts half. */
fun weeklyGroupSets(days: List<ProgramDay>, perWeek: Int): Map<String, Double> {
    val out = HashMap<String, Double>()
    if (days.isEmpty()) return out
    val f = perWeek.toDouble() / days.size
    days.flatMap { it.exercises }.forEach { pe ->
        patternGroups[pe.pattern].orEmpty().forEachIndexed { i, g ->
            out[g] = (out[g] ?: 0.0) + pe.sets * f * (if (i == 0) 1.0 else 0.5)
        }
    }
    return out
}

val Program.weeklyGroupSets: Map<String, Double> get() = weeklyGroupSets(days, sessionsPerWeek)

/**
 * Moves each group's weekly sets into its [weeklySetRange] one set at a time while every session stays
 * within its set budget and, when [minutes] is set, its time limit: sessions over budget lose a set from the
 * group with the most to spare, groups above their range lose a set from accessories before main lifts, and with
 * [raise] groups below their range gain one on the exercise with the fewest sets, if its session has room.
 * Exercises stay within MIN_SETS..MAX_SETS.
 */
private fun balanceVolume(
    days: List<ProgramDay>,
    perWeek: Int,
    goal: String,
    experience: String,
    focus: List<String>,
    reduce: List<String>,
    minutes: Int,
    compoundFloor: (ProgramDay) -> Int,
    raise: Boolean,
): List<ProgramDay> {
    val budget = sessionBudget(experience, focus.size, perWeek)
    fun tooLong(day: ProgramDay) = day.exercises.sumOf { it.sets } > budget ||
        (minutes > 0 && sessionSeconds(day.exercises) > minutes * 60)
    /** Whether time may take a set off [pe] in [day]: compounds stop at the day's [compoundFloor]. */
    fun cuttable(day: ProgramDay, pe: ProgramExercise) = pe.sets > (if (pe.role == Role.ACCESSORY) MIN_SETS else compoundFloor(day))
    var d = days
    repeat(160) {
        val weekly = weeklyGroupSets(d, perWeek)
        val all = d.flatMap { it.exercises }
        val dayOf = d.flatMap { day -> day.exercises.map { it.id to day } }.toMap()
        /** Whether one more set of [pe] still fits its session. */
        fun roomFor(pe: ProgramExercise): Boolean {
            val day = dayOf.getValue(pe.id)
            return day.exercises.sumOf { it.sets } < budget &&
                (minutes <= 0 || sessionSeconds(day.exercises.map { if (it.id == pe.id) it.copy(sets = it.sets + 1) else it }) <= minutes * 60)
        }
        fun direct(g: String) = all.filter { patternGroup(it.pattern) == g }
        fun range(g: String) = weeklySetRange(goal, experience, g in focus, g, g in reduce)
        fun surplus(pe: ProgramExercise) = patternGroup(pe.pattern)?.let { g -> (weekly[g] ?: 0.0) - range(g).start } ?: 0.0
        // Over the set budget, other compounds may go below the floor too; the floor only guards against time cuts.
        fun busyCuttable(day: ProgramDay, pe: ProgramExercise) = cuttable(day, pe) ||
            (day.exercises.sumOf { it.sets } > budget && pe.role == Role.SECONDARY && pe.sets > MIN_SETS)
        val busy = d.firstOrNull { day -> tooLong(day) && day.exercises.any { busyCuttable(day, it) } }
        // The floor only guards against cuts for time; volume above a group's range comes off as usual.
        fun overCuttable(pe: ProgramExercise) = pe.sets > MIN_SETS
        val over = MuscleGroup.all.firstOrNull { g ->
            (weekly[g] ?: 0.0) > range(g).endInclusive + EPS && direct(g).any(::overCuttable)
        }
        // The group furthest below its range, relative to the range, gets the next set.
        val under = if (busy != null || over != null || !raise) null else MuscleGroup.all.filter { g ->
            (weekly[g] ?: 0.0) < range(g).start - EPS && direct(g).any { it.sets < MAX_SETS && roomFor(it) }
        }.maxByOrNull { g -> 1 - (weekly[g] ?: 0.0) / range(g).start }
        val (id, delta) = when {
            busy != null -> busy.exercises.filter { busyCuttable(busy, it) }
                .maxWith(compareBy({ surplus(it) }, { roleRank(it.role) })).id to -1
            over != null -> direct(over).filter(::overCuttable)
                .maxWith(compareBy({ roleRank(it.role) }, { it.sets })).id to -1
            under != null -> direct(under).filter { it.sets < MAX_SETS && roomFor(it) }
                .minWith(compareBy({ it.sets }, { roleRank(it.role) })).id to 1
            else -> return d
        }
        d = d.map { day ->
            day.copy(exercises = day.exercises.map {
                if (it.id == id) it.copy(sets = it.sets + delta, baseSets = it.baseSets + delta) else it
            })
        }
    }
    return d
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

/**
 * Rest between sets: 2-3 minutes for compound lifts and 90 s for isolation work. Sessions of 45 minutes or
 * less rest a little shorter, the usual trade for time-efficient training (Iversen 2021): 150 s, 90 s, 60 s.
 */
private fun restFor(role: String, minutes: Int) = if (minutes in 1..45) {
    when (role) {
        Role.PRIMARY -> 150
        Role.SECONDARY -> 90
        else -> 60
    }
} else {
    when (role) {
        Role.PRIMARY -> 180
        Role.SECONDARY -> 120
        else -> 90
    }
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
    minutes: Int,
): ProgramExercise {
    val bodyweight = c.gear == Gear.BODYWEIGHT
    val range = repRange(goal, role, bodyweight)
    val sets = baseSets(experience, role)
    val weight = if (bodyweight) null
    else startingWeight(best1RM(c.id), range.first, STARTING_RIR, loadStep(exercise(c.id)?.equipment, pattern, unit))
    return ProgramExercise(
        exerciseId = c.id, pattern = pattern, role = role, sets = sets, baseSets = sets,
        repMin = range.first, repMax = range.last, targetReps = range.first, weight = weight,
        restSeconds = restFor(role, minutes), note = if (weight != null) "Starting weight estimated from your history" else "",
    )
}

/**
 * Builds a program. [exercise] looks up the library (candidates missing from it are skipped);
 * [best1RM] gives an estimated one-rep max from the user's history, used for starting weights.
 *
 * [split] picks the day rotation (Split.AUTO chooses from the days per week). Groups in [focus] get an
 * extra exercise on each day that trains them, come first after the main lifts, and aim for about four
 * more weekly sets. Groups in [reduce] are kept at maintenance volume: their slots are dropped and they get
 * one exercise in the rotation, coupled onto another day if need be, unless compound lifts for other
 * groups already cover them. Days with little else left disappear, and thin days are filled up.
 *
 * With [minutes] set, sessions are fitted to that time: fewer sets, and, if that isn't enough, fewer exercises
 * from the best-covered groups, never the main lifts. Sessions of 45 minutes or less are run the
 * time-efficient way: shorter rests, isolation exercises paired into supersets, and isolation exercises
 * dropped before compound lifts go below 3 sets.
 */
fun generateProgram(
    goal: String,
    experience: String,
    daysPerWeek: Int,
    equipment: String,
    unit: String,
    exercise: (String) -> Exercise?,
    split: String = Split.AUTO,
    focus: List<String> = emptyList(),
    reduce: List<String> = emptyList(),
    minutes: Int = 0,
    best1RM: (String) -> Double? = { null },
): Program {
    val n = daysPerWeek.coerceIn(2, 6)
    val gear = gearFor(equipment)
    val reduced = reduce.toSet()
    val prioritised = focus.filter { it !in reduced }
    val resolved = Split.resolve(split, n)
    val max = maxExercises(experience)
    fun rank(pe: ProgramExercise) = when (patternGroup(pe.pattern)) {
        in prioritised -> 0
        in reduced -> 2
        else -> 1
    }
    fun order(list: List<ProgramExercise>) = orderSession(list.map { pe ->
        OrderItem(pe, programTier(pe), patternGroups[pe.pattern].orEmpty().toSet(), rank(pe), pe.supersetId, setOfNotNull(patternGroup(pe.pattern)))
    })
    /** The first exercise of [slots] the day doesn't have yet, for [plan]. */
    fun make(plan: DayPlan, have: List<ProgramExercise>, slots: List<Slot>): ProgramExercise? {
        val used = have.map { it.exerciseId }.toSet()
        return slots.firstNotNullOfOrNull { slot ->
            pick(slot.pattern, slot.variant + plan.variantOffset, gear, used, experience, exercise)?.let { c ->
                newProgramExercise(c, slot.pattern, slot.role, goal, experience, unit, exercise, best1RM, minutes)
            }
        }
    }

    val built = rotation(resolved, n).mapNotNull { plan ->
        val groups = plan.groups - reduced
        // A day left with nothing but core work isn't worth a session of its own.
        if (groups.isEmpty() || groups == setOf(MuscleGroup.ABS)) return@mapNotNull null
        val list = ArrayList<ProgramExercise>()
        for (slot in plan.slots.filter { patternGroup(it.pattern) !in reduced }) {
            make(plan, list, listOf(slot))?.let(list::add)
        }
        // Over the cap, the last exercises go first, but never those for focus groups.
        while (list.size > max) {
            val i = list.indexOfLast { patternGroup(it.pattern) !in prioritised }
            if (i < 0) break
            list.removeAt(i)
        }
        // Days that lost exercises to reduced groups are filled with the day's other groups.
        val missing = groups.filter { g -> list.none { patternGroup(it.pattern) == g } }
        for (g in missing + groups) {
            if (list.size >= min(4, max)) break
            make(plan, list, groupSlots[g].orEmpty())?.let(list::add)
        }
        // Focus groups the day trains get an extra exercise, those with the least work on the day first.
        for (g in prioritised.filter { it in groups }.sortedBy { g -> list.count { patternGroup(it.pattern) == g } }) {
            if (list.size >= max + 2) break
            make(plan, list, groupSlots[g].orEmpty())?.let(list::add)
        }
        if (list.isEmpty()) null else plan to list
    }

    val short = minutes in 1..SHORT_SESSION
    // Days that ran out of isolation work to drop, whose compounds may go down to 2 sets.
    val lowered = HashSet<String>()
    fun days() = built.map { (plan, list) ->
        ProgramDay(name = plan.name, exercises = order(list).let { if (short) order(pairIsolation(it)) else it })
    }
    // Reduced groups not already covered by other lifts get one exercise, on a day that normally trains them
    // if there is one, else on the day with the fewest exercises.
    for (g in MuscleGroup.all.filter { it in reduced }) {
        if ((weeklyGroupSets(days(), n)[g] ?: 0.0) >= MAINTENANCE.start) continue
        val target = built.minWithOrNull(compareBy({ if (g in it.first.groups) 0 else 1 }, { it.second.size })) ?: break
        make(target.first, target.second, maintenanceSlots[g].orEmpty())?.let(target.second::add)
    }

    /** The days as built, keeping the sets balancing gave each exercise so far. */
    fun rebuilt(current: List<ProgramDay>): List<ProgramDay> {
        val sets = current.flatMap { it.exercises }.associate { it.id to it.sets }
        return days().map { d -> d.copy(exercises = d.exercises.map { e -> sets[e.id]?.let { e.copy(sets = it, baseSets = it) } ?: e }) }
    }
    fun balance(current: List<ProgramDay>) =
        balanceVolume(
            current, n, goal, experience, prioritised, reduce, minutes,
            { day -> if (day.name in lowered) MIN_SETS else compoundFloor(minutes) }, raise = true,
        )

    var days = balance(days())
    // Sessions still over the time limit with every exercise at its minimum lose exercises: never the main lifts
    // or focus groups while anything else is left, and first from the groups with the most weekly work to spare.
    // Short sessions drop isolation exercises first; only when none are left do compounds go down to 2 sets.
    if (minutes > 0) repeat(60) {
        val long = days.indexOfFirst {
            it.estimatedMinutes > minutes && (it.exercises.size > MIN_EXERCISES || (short && it.name !in lowered))
        }
        if (long < 0) return@repeat
        val list = built[long].second
        if (short && days[long].name !in lowered &&
            (list.none { it.role == Role.ACCESSORY && patternGroup(it.pattern) !in prioritised } || list.size <= MIN_EXERCISES)
        ) {
            lowered.add(days[long].name)
            days = balance(rebuilt(days))
            return@repeat
        }
        val weekly = weeklyGroupSets(days, n)
        fun spare(pe: ProgramExercise) = patternGroup(pe.pattern)?.let { g ->
            (weekly[g] ?: 0.0) - weeklySetRange(goal, experience, g in prioritised, g, g in reduced).start
        } ?: 0.0
        fun focus(pe: ProgramExercise) = patternGroup(pe.pattern) in prioritised
        // Short sessions give up isolation work before compounds, but focus groups keep theirs as long as
        // any exercise for another group can go instead.
        val pool = if (short) {
            list.filter { it.role == Role.ACCESSORY && !focus(it) }
                .ifEmpty { list.filter { it.role != Role.PRIMARY && !focus(it) } }
                .ifEmpty { list.filter { it.role == Role.ACCESSORY } }
        } else {
            list.filter { it.role != Role.PRIMARY }
        }
        val drop = pool.ifEmpty { list }
            .maxWith(compareBy({ if (patternGroup(it.pattern) in prioritised) 0 else 1 }, { spare(it) }, { roleRank(it.role) }))
        list.remove(drop)
        days = balance(rebuilt(days))
    }
    // A group still well short of its weekly range with every exercise at the set cap gets one more exercise
    // on the least busy day that trains it and has room in its set budget, then the volume is balanced again.
    val budget = sessionBudget(experience, prioritised.size, n)
    repeat(MuscleGroup.all.size) {
        val weekly = weeklyGroupSets(days, n)
        val all = days.flatMap { it.exercises }
        val setsOf = all.associate { it.id to it.sets }
        val short = MuscleGroup.all.filter { it !in reduced }.firstOrNull { g ->
            (weekly[g] ?: 0.0) < weeklySetRange(goal, experience, g in prioritised, g).start - 2 &&
                all.filter { patternGroup(it.pattern) == g }.all { it.sets >= MAX_SETS }
        } ?: return@repeat
        val target = built.withIndex().filter { (i, pair) ->
            val (plan, list) = pair
            short in plan.groups && list.size < max && list.sumOf { setsOf[it.id] ?: it.sets } + MIN_SETS <= budget &&
                (minutes <= 0 || days[i].estimatedMinutes + 5 <= minutes) && days[i].name !in lowered
        }.minByOrNull { it.value.second.size }?.value ?: return@repeat
        val added = make(target.first, target.second, groupSlots[short].orEmpty()) ?: return@repeat
        target.second.add(added)
        days = balance(rebuilt(days))
    }
    return Program(
        name = "${Split.label(resolved)} · $n days",
        goal = goal,
        experience = experience,
        equipment = equipment,
        days = days,
        split = split,
        focus = prioritised,
        reduce = reduce,
        perWeek = n,
        minutes = minutes,
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
