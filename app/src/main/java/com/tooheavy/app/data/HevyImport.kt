package com.tooheavy.app.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Reads the workout CSV that Hevy exports (Settings → Export & Import Data → Export Workouts). It has one row per
 * set with the columns title, start_time, end_time, description, exercise_title, superset_id, exercise_notes,
 * set_index, set_type, weight_kg (or weight_lbs), reps, distance_km (or distance_miles), duration_seconds and rpe.
 * Pure Kotlin so it can be unit tested; Repo.importWorkouts stores the result.
 */
object HevyImport {
    /** [rir] comes from Hevy's RPE (10 - RPE). */
    data class ImportSet(val type: String, val weightKg: Double?, val reps: Int?, val rir: Int? = null)
    /** [superset] is Hevy's superset id within the workout, null when the exercise stands alone. */
    data class ImportExercise(val title: String, val notes: String, val sets: List<ImportSet>, val superset: String? = null)
    data class ImportWorkout(val title: String, val start: Long, val end: Long?, val notes: String, val exercises: List<ImportExercise>)

    /** [skippedSets]: sets with neither weight nor reps (timed or distance-only cardio), which TooHeavy can't store. */
    data class Parsed(val workouts: List<ImportWorkout>, val skippedSets: Int) {
        /** Distinct exercise titles with how many sets each has, most used first. */
        val exerciseTitles: List<Pair<String, Int>>
            get() = workouts.flatMap { w -> w.exercises.map { it.title to it.sets.size } }
                .groupBy({ it.first }, { it.second }).map { (t, n) -> t to n.sum() }
                .sortedByDescending { it.second }
    }

    class FormatException(message: String) : Exception(message)

    // ---------- CSV ----------

    /** RFC 4180 CSV: quoted fields may contain commas, line breaks and doubled quotes. */
    fun csv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private val timeFormats = listOf("d MMM yyyy, HH:mm", "d MMM yyyy, H:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")

    /** "21 Oct 2024, 18:30" (Hevy's format, local time) and a few ISO-like variants; null if unreadable. */
    fun parseTime(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        for (f in timeFormats) {
            val fmt = SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }
            val pos = java.text.ParsePosition(0)
            val d = fmt.parse(t, pos)
            if (d != null && pos.index > 0) return d.time
        }
        return null
    }

    private fun num(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun parse(text: String): Parsed {
        val rows = csv(text)
        if (rows.isEmpty()) throw FormatException("The file is empty.")
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        fun col(vararg names: String): Int? = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val title = col("title")
        val start = col("start_time")
        val exercise = col("exercise_title")
        if (title == null || start == null || exercise == null) {
            throw FormatException("This doesn't look like a Hevy workout export (missing title, start_time or exercise_title).")
        }
        val end = col("end_time")
        val description = col("description")
        val exerciseNotes = col("exercise_notes")
        val setType = col("set_type")
        val kg = col("weight_kg")
        val lbs = col("weight_lbs")
        val reps = col("reps")
        val rpe = col("rpe")
        val superset = col("superset_id")

        class WorkoutAcc(val title: String, val start: Long, val end: Long?, val notes: String) {
            val exercises = ArrayList<ImportExercise>()
        }
        val workouts = LinkedHashMap<Pair<String, Long>, WorkoutAcc>()
        var skipped = 0
        var unreadable = 0
        for (r in rows.drop(1)) {
            fun get(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val startTime = parseTime(get(start))
            if (startTime == null) {
                unreadable++
                continue
            }
            val w = workouts.getOrPut(get(title) to startTime) {
                WorkoutAcc(get(title).ifBlank { "Workout" }, startTime, parseTime(get(end)), get(description))
            }
            val weight = num(get(kg)) ?: num(get(lbs))?.let { it * 0.45359237 }
            val repCount = num(get(reps))?.roundToLong()?.toInt()
            if ((weight == null || weight == 0.0) && repCount == null) {
                skipped++
                continue
            }
            val set = ImportSet(
                type = when (get(setType).lowercase(Locale.US)) {
                    "warmup" -> SetType.WARMUP
                    "dropset" -> SetType.DROP
                    "failure" -> SetType.FAILURE
                    else -> SetType.NORMAL
                },
                weightKg = weight?.takeIf { it > 0 },
                reps = repCount,
                rir = num(get(rpe))?.takeIf { it in 1.0..10.0 }?.let { (10 - it).roundToLong().toInt() },
            )
            val name = get(exercise).ifBlank { "Exercise" }
            // Consecutive rows of the same exercise are one entry; the same exercise later in the workout is another.
            val last = w.exercises.lastOrNull()
            if (last != null && last.title == name) {
                w.exercises[w.exercises.lastIndex] = last.copy(sets = last.sets + set)
            } else {
                w.exercises.add(ImportExercise(name, get(exerciseNotes), listOf(set), get(superset).ifBlank { null }))
            }
        }
        if (workouts.isEmpty() && unreadable > 0) throw FormatException("Couldn't read the workout dates in this file.")
        return Parsed(
            workouts.values.filter { it.exercises.isNotEmpty() }
                .map { ImportWorkout(it.title, it.start, it.end, it.notes, it.exercises.toList()) }
                .sortedBy { it.start },
            skipped,
        )
    }

    // ---------- exercise matching ----------

    /** Hevy titles (lowercase) whose library counterpart has a different name. */
    private val aliases = mapOf(
        "bench press (barbell)" to "Barbell_Bench_Press_-_Medium_Grip",
        "bench press (dumbbell)" to "Dumbbell_Bench_Press",
        "bench press (smith machine)" to "Smith_Machine_Bench_Press",
        "bench press - close grip (barbell)" to "Close-Grip_Barbell_Bench_Press",
        "bench press - wide grip (barbell)" to "Wide-Grip_Barbell_Bench_Press",
        "incline bench press (barbell)" to "Barbell_Incline_Bench_Press_-_Medium_Grip",
        "incline bench press (dumbbell)" to "Incline_Dumbbell_Press",
        "incline bench press (smith machine)" to "Smith_Machine_Incline_Bench_Press",
        "decline bench press (barbell)" to "Decline_Barbell_Bench_Press",
        "decline bench press (dumbbell)" to "Decline_Dumbbell_Bench_Press",
        "chest press (machine)" to "Leverage_Chest_Press",
        "incline chest press (machine)" to "Leverage_Incline_Chest_Press",
        "chest fly (dumbbell)" to "Dumbbell_Flyes",
        "chest fly (machine)" to "Butterfly",
        "butterfly (pec deck)" to "Butterfly",
        "incline chest fly (dumbbell)" to "Incline_Dumbbell_Flyes",
        "cable fly crossovers" to "Cable_Crossover",
        "chest dip" to "Dips_-_Chest_Version",
        "triceps dip" to "Dips_-_Triceps_Version",
        "push up" to "Pushups",
        "squat (barbell)" to "Barbell_Squat",
        "squat (dumbbell)" to "Dumbbell_Squat",
        "squat (smith machine)" to "Smith_Machine_Squat",
        "full squat" to "Barbell_Full_Squat",
        "front squat" to "Front_Barbell_Squat",
        "front squat (barbell)" to "Front_Barbell_Squat",
        "goblet squat" to "Goblet_Squat",
        "hack squat (machine)" to "Hack_Squat",
        "hack squat" to "Hack_Squat",
        "bulgarian split squat" to "Bulgarian_Split_Squat",
        "leg press (machine)" to "Leg_Press",
        "leg press" to "Leg_Press",
        "deadlift (barbell)" to "Barbell_Deadlift",
        "romanian deadlift (barbell)" to "Romanian_Deadlift",
        "romanian deadlift (dumbbell)" to "Dumbbell_Romanian_Deadlift",
        "single leg romanian deadlift (dumbbell)" to "Single-Leg_Dumbbell_Romanian_Deadlift",
        "romanian deadlift (smith machine)" to "Smith_Machine_Romanian_Deadlift",
        "straight leg deadlift" to "Stiff-Legged_Barbell_Deadlift",
        "sumo deadlift" to "Sumo_Deadlift",
        "trap bar deadlift" to "Trap_Bar_Deadlift",
        "hip thrust (barbell)" to "Barbell_Hip_Thrust",
        "hip thrust (machine)" to "Machine_Hip_Thrust",
        "hip thrust (smith machine)" to "Smith_Machine_Hip_Thrust",
        "good morning (barbell)" to "Good_Morning",
        "overhead press (barbell)" to "Standing_Military_Press",
        "overhead press (dumbbell)" to "Dumbbell_Shoulder_Press",
        "shoulder press (dumbbell)" to "Dumbbell_Shoulder_Press",
        "seated overhead press (dumbbell)" to "Seated_Dumbbell_Press",
        "seated overhead press (barbell)" to "Seated_Barbell_Military_Press",
        "shoulder press (machine plates)" to "Leverage_Shoulder_Press",
        "shoulder press (machine)" to "Machine_Shoulder_Military_Press",
        "arnold press (dumbbell)" to "Arnold_Dumbbell_Press",
        "lateral raise (dumbbell)" to "Side_Lateral_Raise",
        "lateral raise (cable)" to "Single-Arm_Cable_Lateral_Raise",
        "single arm lateral raise (cable)" to "Single-Arm_Cable_Lateral_Raise",
        "lateral raise (machine)" to "Machine_Lateral_Raise",
        "front raise (dumbbell)" to "Front_Dumbbell_Raise",
        "rear delt reverse fly (dumbbell)" to "Reverse_Flyes",
        "rear delt reverse fly (machine)" to "Reverse_Machine_Flyes",
        "rear delt reverse fly (cable)" to "Cable_Rear_Delt_Fly",
        "face pull" to "Face_Pull",
        "face pull (cable)" to "Face_Pull",
        "shrug (barbell)" to "Barbell_Shrug",
        "shrug (dumbbell)" to "Dumbbell_Shrug",
        "upright row (barbell)" to "Upright_Barbell_Row",
        "pull up" to "Pullups",
        "pull up (weighted)" to "Pullups",
        "pull up (assisted)" to "Band_Assisted_Pull-Up",
        "chin up" to "Chin-Up",
        "chin up (weighted)" to "Chin-Up",
        "lat pulldown (cable)" to "Wide-Grip_Lat_Pulldown",
        "lat pulldown - close grip (cable)" to "Close-Grip_Front_Lat_Pulldown",
        "straight arm lat pulldown (cable)" to "Straight-Arm_Pulldown",
        "seated cable row - v grip (cable)" to "Seated_Cable_Rows",
        "seated cable row - bar grip" to "Seated_Cable_Rows",
        "seated row (machine)" to "Leverage_Iso_Row",
        "bent over row (barbell)" to "Bent_Over_Barbell_Row",
        "bent over row (dumbbell)" to "Bent_Over_Two-Dumbbell_Row",
        "pendlay row (barbell)" to "Pendlay_Row",
        "seal row (barbell)" to "Seal_Row",
        "meadows rows (barbell)" to "Meadows_Row",
        "chest supported row (machine)" to "Chest-Supported_Machine_Row",
        "single arm cable row" to "Single-Arm_Cable_Row",
        "assisted pull up" to "Assisted_Pull-Up",
        "landmine press" to "Landmine_Press",
        "bayesian curl (cable)" to "Bayesian_Cable_Curl",
        "triceps kickback (cable)" to "Cable_Triceps_Kickback",
        "nordic hamstrings curls" to "Nordic_Hamstring_Curl",
        "hanging knee raise" to "Hanging_Knee_Raise",
        "hip abduction (cable)" to "Cable_Hip_Abduction",
        "dead hang" to "Dead_Hang",
        "copenhagen plank" to "Copenhagen_Plank",
        "woodchopper (cable)" to "Cable_Woodchopper",
        "pendulum squat (machine)" to "Pendulum_Squat",
        "belt squat (machine)" to "Belt_Squat",
        "dumbbell row" to "One-Arm_Dumbbell_Row",
        "t bar row" to "T-Bar_Row_with_Handle",
        "inverted row" to "Inverted_Row",
        "bicep curl (barbell)" to "Barbell_Curl",
        "bicep curl (dumbbell)" to "Dumbbell_Bicep_Curl",
        "bicep curl (cable)" to "Standing_Biceps_Cable_Curl",
        "bicep curl (machine)" to "Machine_Bicep_Curl",
        "ez bar biceps curl" to "EZ-Bar_Curl",
        "hammer curl (dumbbell)" to "Hammer_Curls",
        "hammer curl (cable)" to "Cable_Hammer_Curls_-_Rope_Attachment",
        "preacher curl (barbell)" to "Preacher_Curl",
        "preacher curl (dumbbell)" to "One_Arm_Dumbbell_Preacher_Curl",
        "preacher curl (machine)" to "Machine_Preacher_Curls",
        "concentration curl" to "Concentration_Curls",
        "seated incline curl (dumbbell)" to "Incline_Dumbbell_Curl",
        "triceps pushdown" to "Triceps_Pushdown",
        "triceps rope pushdown" to "Triceps_Pushdown_-_Rope_Attachment",
        "triceps extension (dumbbell)" to "Standing_Dumbbell_Triceps_Extension",
        "triceps extension (cable)" to "Cable_Rope_Overhead_Triceps_Extension",
        "overhead triceps extension (cable)" to "Cable_Rope_Overhead_Triceps_Extension",
        "skullcrusher (barbell)" to "EZ-Bar_Skullcrusher",
        "skullcrusher (dumbbell)" to "Lying_Dumbbell_Tricep_Extension",
        "triceps kickback (dumbbell)" to "Tricep_Dumbbell_Kickback",
        "leg extension (machine)" to "Leg_Extensions",
        "lying leg curl (machine)" to "Lying_Leg_Curls",
        "seated leg curl (machine)" to "Seated_Leg_Curl",
        "standing calf raise (machine)" to "Standing_Calf_Raises",
        "standing calf raise" to "Standing_Calf_Raises",
        "seated calf raise" to "Seated_Calf_Raise",
        "calf press (machine)" to "Calf_Press_On_The_Leg_Press_Machine",
        "lunge (dumbbell)" to "Dumbbell_Lunges",
        "lunge (barbell)" to "Barbell_Lunge",
        "walking lunge" to "Bodyweight_Walking_Lunge",
        "walking lunge (dumbbell)" to "Dumbbell_Lunges",
        "step up" to "Dumbbell_Step_Ups",
        "hip abduction (machine)" to "Thigh_Abductor",
        "hip adduction (machine)" to "Thigh_Adductor",
        "plank" to "Plank",
        "crunch" to "Crunches",
        "cable crunch" to "Cable_Crunch",
        "sit up" to "Sit-Up",
        "hanging leg raise" to "Hanging_Leg_Raise",
        "russian twist" to "Russian_Twist",
        "ab wheel" to "Ab_Roller",
        "back extension (hyperextension)" to "Hyperextensions_Back_Extensions",
        "back extension" to "Hyperextensions_Back_Extensions",
        "power clean" to "Power_Clean",
        "clean and jerk" to "Clean_and_Jerk",
        "farmers walk" to "Farmers_Walk",
        "kettlebell swing" to "Kettlebell_Swing",
        "jump rope" to "Rope_Jumping",
        "mountain climber" to "Mountain_Climbers",
        "running" to "Running_Treadmill",
        "treadmill" to "Running_Treadmill",
        "walking" to "Walking_Treadmill",
        "cycling" to "Bicycling",
        "spinning" to "Bicycling_Stationary",
        "stationary bike" to "Bicycling_Stationary",
        "rowing machine" to "Rowing_Stationary",
        "elliptical trainer" to "Elliptical_Trainer",
        "stair machine" to "Stairmaster",
    )

    /** Hevy's equipment in parentheses → the library's equipment values. */
    private val equipmentWords = mapOf(
        "barbell" to "barbell", "dumbbell" to "dumbbell", "cable" to "cable", "machine" to "machine",
        "smith machine" to "machine", "machine plates" to "machine", "kettlebell" to "kettlebells", "band" to "bands",
        "resistance band" to "bands", "bodyweight" to "body only", "weighted" to "body only", "assisted" to "body only",
        "ez bar" to "e-z curl bar", "plate" to "other", "medicine ball" to "medicine ball",
    )

    private fun tokens(s: String): List<String> =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim().split(" ").filter { it.isNotEmpty() }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }

    /** "Bench Press (Barbell)" → ("Bench Press", "Barbell"). */
    private fun split(title: String): Pair<String, String?> {
        val m = Regex("^(.*?)\\s*\\(([^)]*)\\)\\s*$").find(title.trim()) ?: return title.trim() to null
        return m.groupValues[1].trim() to m.groupValues[2].trim()
    }

    /** The library exercise a Hevy title most likely means, or null when nothing fits well enough. */
    fun match(title: String, library: List<Exercise>): Exercise? {
        val byId = library.associateBy { it.id }
        val key = title.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
        // A previous import (or the user) may already have an exercise with exactly this name.
        library.firstOrNull { it.name.trim().equals(title.trim(), ignoreCase = true) }?.let { return it }
        aliases[key]?.let { byId[it] }?.let { return it }
        val (base, equipmentText) = split(title)
        val equipment = equipmentText?.lowercase(Locale.US)?.let { equipmentWords[it] }
        val baseTokens = tokens(base)
        if (baseTokens.isEmpty()) return null
        val equipTokens = equipmentText?.let { tokens(it) }.orEmpty()
        val want = (baseTokens + equipTokens).toSet()
        library.firstOrNull { tokens(it.name).toSet() == want }?.let { return it }
        library.firstOrNull { tokens(it.name).toSet() == baseTokens.toSet() && (equipment == null || it.equipment == equipment) }
            ?.let { return it }
        // Every word of the base name appears in the library name, with at most two extra words.
        return library
            .filter { e -> equipment == null || e.equipment == equipment }
            .mapNotNull { e ->
                val t = tokens(e.name).toSet()
                val extra = (t - want).size
                if (t.containsAll(baseTokens) && extra <= 2) e to extra else null
            }
            .minByOrNull { (e, extra) -> extra * 100 + e.name.length }
            ?.first
    }

    /** First match wins, so the more specific patterns come first ("leg curl" before "curl", "leg raise" before "raise"). */
    private val muscleHints = listOf(
        Regex("leg curl|hamstring|good morning|romanian|stiff") to "hamstrings",
        Regex("calf") to "calves",
        Regex("abduct") to "abductors",
        Regex("adduct") to "adductors",
        Regex("tricep|pushdown|skull|\\bdips?\\b") to "triceps",
        Regex("hip thrust|glute|bridge|kickback") to "glutes",
        Regex("squat|leg press|lunge|leg extension|step up|sissy") to "quadriceps",
        Regex("deadlift|back extension|hyperextension|superman") to "lower back",
        Regex("pulldown|pull ?up|chin ?up|\\blat\\b|pullover") to "lats",
        Regex("shrug") to "traps",
        Regex("\\brow\\b") to "middle back",
        Regex("curl") to "biceps",
        Regex("crunch|plank|sit ?up|leg raise|twist|\\bab\\b|\\babs\\b|core|hollow|v up") to "abdominals",
        Regex("bench|chest|\\bfly|push ?up|pec") to "chest",
        Regex("press|raise|face pull|delt|shoulder") to "shoulders",
        Regex("wrist|forearm|farmer|grip") to "forearms",
        Regex("neck") to "neck",
    )
    private val cardioHint = Regex(
        "\\b(run|running|jog|jogging|cycling|bike|biking|spinning|rowing machine|rower|elliptical|walk|walking|" +
            "swim|swimming|stair|stairs|jump rope|hike|hiking|sprint|sprints|skiing|erg)\\b",
    )
    private val stretchHint = Regex("stretch|mobility|yoga")

    /** A custom exercise for a Hevy title the library doesn't have, with muscles and type guessed from the name. */
    fun newExercise(title: String): Exercise {
        val (_, equipmentText) = split(title)
        val lower = " ${title.lowercase(Locale.US)} "
        val category = when {
            cardioHint.containsMatchIn(lower) -> "cardio"
            stretchHint.containsMatchIn(lower) -> "stretching"
            else -> "strength"
        }
        return Exercise(
            id = Exercise.newCustomId(),
            name = title.trim(),
            equipment = equipmentText?.lowercase(Locale.US)?.let { equipmentWords[it] },
            primaryMuscles = if (category == "cardio") emptyList()
            else listOfNotNull(muscleHints.firstOrNull { it.first.containsMatchIn(lower) }?.second),
            category = category,
        )
    }

    // ---------- conversion ----------

    /**
     * TooHeavy workouts for [parsed], skipping ones already in [existing] (same name, started within a minute).
     * [exerciseIds] maps each Hevy title to an exercise id; weights are converted to [unit].
     */
    fun toWorkouts(parsed: Parsed, exerciseIds: Map<String, String>, unit: String, existing: List<Workout>): List<Workout> {
        fun weight(kg: Double?): Double? = kg?.let {
            val v = if (unit == "lb") it / 0.45359237 else it
            Math.round(v * 100.0) / 100.0
        }
        return parsed.workouts
            .filter { w -> existing.none { it.name == w.title && abs(it.startTime - w.start) < 60_000 } }
            .map { w ->
                Workout(
                    name = w.title,
                    startTime = w.start,
                    endTime = w.end?.takeIf { it > w.start } ?: w.start,
                    notes = w.notes,
                    exercises = w.exercises.mapNotNull { e ->
                        val id = exerciseIds[e.title] ?: return@mapNotNull null
                        WorkoutExercise(
                            exerciseId = id,
                            notes = e.notes,
                            sets = e.sets.map { s ->
                                SetEntry(weight = weight(s.weightKg), reps = s.reps, completed = true, type = s.type, rir = s.rir)
                            },
                            supersetId = e.superset?.let { "${w.start}-$it" },
                        )
                    }.normalizeSupersets({ it.supersetId }) { it.copy(supersetId = null) },
                )
            }
            .filter { it.exercises.isNotEmpty() }
    }

    /** How many of [parsed]'s workouts are already in [existing]. */
    fun alreadyImported(parsed: Parsed, existing: List<Workout>): Int =
        parsed.workouts.count { w -> existing.any { it.name == w.title && abs(it.startTime - w.start) < 60_000 } }
}
