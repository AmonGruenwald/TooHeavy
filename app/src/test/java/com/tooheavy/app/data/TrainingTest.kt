package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrainingTest {
    companion object {
        private val library: List<Exercise> by lazy {
            val file = listOf("src/main/assets/exercises.json", "app/src/main/assets/exercises.json").map(::File).first { it.exists() }
            Gson().fromJson(file.readText(), object : TypeToken<List<Exercise>>() {}.type)
        }
        private val byId: Map<String, Exercise> by lazy { library.associateBy { it.id } }
    }

    private val lookup: (String) -> Exercise? = { byId[it] }

    private fun we(id: String, vararg weights: Double?, superset: String? = null) = WorkoutExercise(
        exerciseId = id, sets = weights.map { SetEntry(weight = it, reps = 8) }, supersetId = superset,
    )

    // ---------- warm-ups ----------

    @Test
    fun warmupRampsTowardTheWorkingWeight() {
        val sets = WarmUps.sets(100.0, byId["Barbell_Squat"], "kg")
        assertEquals(listOf(50.0, 70.0, 85.0), sets.map { it.weight })
        assertEquals(listOf(8, 5, 2), sets.map { it.reps })
        assertTrue(sets.all { it.type == SetType.WARMUP })
        // Never below the empty bar, and nothing at or above the working weight.
        assertEquals(listOf(20.0, 25.0), WarmUps.sets(30.0, byId["Barbell_Squat"], "kg").map { it.weight })
        // Isolation: one lighter set.
        assertEquals(listOf(10.0), WarmUps.sets(20.0, byId["Dumbbell_Bicep_Curl"], "kg").map { it.weight })
        // Unknown working weight: one light set to fill in.
        val unknown = WarmUps.sets(null, byId["Barbell_Squat"], "kg").single()
        assertNull(unknown.weight)
    }

    @Test
    fun onlyTheFirstExerciseForAMuscleGroupWarmsUp() {
        val w = Workout(
            exercises = listOf(
                we("Barbell_Bench_Press_-_Medium_Grip", 80.0, 80.0),
                we("Incline_Dumbbell_Press", 30.0), // chest already warm
                we("Barbell_Squat", 100.0), // legs not yet
                we("Pushups", null), // bodyweight: no warm-up
            ),
        )
        val out = WarmUps.apply(w, "kg", lookup, { null })
        val warmups = out.exercises.map { e -> e.sets.count { it.type == SetType.WARMUP } }
        assertEquals(listOf(3, 0, 3, 0), warmups)
        // Applying again adds nothing.
        assertEquals(out, WarmUps.apply(out, "kg", lookup, { null }))
        // Without a filled-in weight, the last logged one is used.
        val fromHistory = WarmUps.apply(Workout(exercises = listOf(we("Barbell_Squat", null))), "kg", lookup, { 60.0 })
        assertEquals(listOf(30.0, 42.5, 50.0), fromHistory.exercises[0].sets.filter { it.type == SetType.WARMUP }.map { it.weight })
    }

    // ---------- rest ----------

    @Test
    fun restTimesFollowTheKindOfExercise() {
        assertEquals(180, RestTimes.auto(byId["Barbell_Squat"]))
        assertEquals(120, RestTimes.auto(byId["Dumbbell_Bench_Press"]))
        assertEquals(90, RestTimes.auto(byId["Dumbbell_Bicep_Curl"]))
        assertEquals(60, RestTimes.auto(byId["Standing_Calf_Raises"]))
    }

    @Test
    fun restSkipsSupersetPartnersAndDropSets() {
        val a = we("Dumbbell_Bicep_Curl", 12.0, 12.0, superset = "s").copy(restSeconds = 60)
        val b = we("Triceps_Pushdown", 20.0, superset = "s").copy(restSeconds = 90)
        val c = we("Barbell_Squat", 60.0, 100.0).let { e ->
            e.copy(restSeconds = 180, sets = listOf(e.sets[0].copy(type = SetType.WARMUP), e.sets[1], SetEntry(type = SetType.DROP)))
        }
        val w = Workout(exercises = listOf(a, b, c))
        assertEquals(0, RestTimes.after(w, 0, a.sets[0].id))
        assertEquals(90, RestTimes.after(w, 1, b.sets[0].id))
        assertEquals(60, RestTimes.after(w, 2, c.sets[0].id))
        assertEquals(0, RestTimes.after(w, 2, c.sets[1].id))
        assertEquals(180, RestTimes.after(w, 2, c.sets[2].id))
    }

    // ---------- supersets ----------

    @Test
    fun supersetsLinkSplitAndLetter() {
        val list = listOf(we("a"), we("b"), we("c"), we("d"))
        val id: (WorkoutExercise) -> String? = { it.supersetId }
        val set: (WorkoutExercise, String?) -> WorkoutExercise = { e, s -> e.copy(supersetId = s) }
        var l = list.linkWithNext(0, id, set).linkWithNext(1, id, set)
        assertEquals(listOf("A", "A", "A", null), supersetLetters(l.map(id)))
        assertEquals(0..2, supersetRange(l, 1))
        l = l.unlink(0, id, set)
        assertEquals(listOf(null, "A", "A", null), supersetLetters(l.map(id)))
        l = l.linkWithNext(2, id, set)
        assertEquals(1..3, supersetRange(l, 3))
        // Removing an exercise leaves a lone partner, which loses its superset.
        val lone = listOf(l[0], l[1]).normalizeSupersets(id) { it.copy(supersetId = null) }
        assertTrue(lone.all { it.supersetId == null })
        assertEquals(listOf("A", "A", "B", "B"), supersetLetters(listOf("x", "x", "y", "y")))
    }

    // ---------- muscles ----------

    @Test
    fun secondaryMusclesCountHalf() {
        val bench = we("Barbell_Bench_Press_-_Medium_Grip", 80.0, 80.0).let { e -> e.copy(sets = e.sets.map { it.copy(completed = true) }) }
        val credit = muscleCredit(listOf(bench), lookup)
        assertEquals(2.0, credit["chest"]!!, 1e-9)
        assertEquals(1.0, credit["triceps"]!!, 1e-9)
        assertEquals("back", MuscleGroup.of("lats"))
        assertEquals(MuscleGroup.GLUTES, MuscleGroup.of("abductors"))
        assertNull(MuscleGroup.of("forearms"))
    }

    // ---------- program generator ----------

    @Test
    fun weeklyVolumeLandsInTheResearchRange() {
        for (level in Experience.all) for (n in 2..6) {
            val p = generateProgram(TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup)
            val range = weeklySetRange(TrainingGoal.HYPERTROPHY, level, false)
            val weekly = p.weeklyGroupSets
            val all = p.days.flatMap { it.exercises }
            for (g in listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS)) {
                val v = weekly[g] ?: 0.0
                val direct = all.filter { patternGroup(it.pattern) == g }
                // Above the range only when every exercise for it is already at the minimum of 2 sets.
                assertTrue("$level/$n $g: $v above $range", v <= range.endInclusive + 0.01 || direct.all { it.sets == 2 })
                // The session budget can run out first: up to 2 sets short on 3 days, 1 on more.
                assertTrue("$level/$n $g: $v below $range", v >= range.start - (if (n <= 3) 2.01 else 1.01) || n == 2)
            }
            assertTrue(p.days.flatMap { it.exercises }.all { it.sets in 2..5 && it.sets == it.baseSets })
        }
    }

    @Test
    fun splitChoiceShapesTheRotation() {
        val ul3 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, split = Split.UPPER_LOWER)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), ul3.days.map { it.name })
        assertEquals(3, ul3.sessionsPerWeek)
        val ppl3 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, split = Split.PPL)
        assertEquals(listOf("Push", "Pull", "Legs"), ppl3.days.map { it.name })
        val fb5 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 5, Equipment.GYM, "kg", lookup, split = Split.FULL_BODY)
        assertEquals(5, fb5.days.size)
        assertEquals("Full body · 5 days", fb5.name)
    }

    @Test
    fun skippedGroupsAreLeftOut() {
        val skip = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, skip = skip)
        val groups = p.days.flatMap { it.exercises }.mapNotNull { patternGroup(it.pattern) }.toSet()
        assertTrue("trained $groups", groups.none { it in skip })
        // Lower days keep only core work, filled up to a useful session.
        assertTrue(p.days.all { it.exercises.size >= 3 })
        assertEquals(4, p.sessionsPerWeek)
    }

    @Test
    fun focusAddsVolumeAndComesFirst() {
        val plain = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup)
        val focused = generateProgram(
            TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, focus = listOf(MuscleGroup.SHOULDERS),
        )
        assertTrue(focused.weeklyGroupSets[MuscleGroup.SHOULDERS]!! > plain.weeklyGroupSets[MuscleGroup.SHOULDERS]!! + 2)
        val upper = focused.days.first()
        val firstAccessory = upper.exercises.first { it.role == Role.ACCESSORY }
        assertEquals(MuscleGroup.SHOULDERS, patternGroup(firstAccessory.pattern))
    }

    // ---------- adaptation ----------

    private fun bench(sessions: Int = 2) = ProgramExercise(
        exerciseId = "Barbell_Bench_Press_-_Medium_Grip", pattern = Pattern.HORIZONTAL_PUSH, role = Role.PRIMARY,
        sets = 3, baseSets = 3, repMin = 8, repMax = 12, targetReps = 9, weight = 60.0, sessions = sessions,
    )

    private fun did(pe: ProgramExercise, reps: Int, rir: Int?, weight: Double = 60.0) = WorkoutExercise(
        exerciseId = pe.exerciseId, programExerciseId = pe.id,
        target = SetTarget(pe.sets, pe.targetReps, pe.repMin, pe.repMax, pe.weight, 2),
        sets = List(3) { SetEntry(weight = weight, reps = reps, completed = true, rir = rir) },
    )

    @Test
    fun loggedRirSteersProgression() {
        val pe = bench()
        assertEquals(10, progressExercise(pe, did(pe, 9, null), "kg", "barbell").targetReps)
        assertEquals(11, progressExercise(pe, did(pe, 9, 4), "kg", "barbell").targetReps)
        assertEquals(9, progressExercise(pe, did(pe, 9, 0), "kg", "barbell").targetReps)
        // At the top of the range with reps to spare, the weight jumps twice.
        assertEquals(65.0, progressExercise(pe, did(pe, 12, 4), "kg", "barbell").weight!!, 1e-9)
        assertEquals(62.5, progressExercise(pe, did(pe, 12, 2), "kg", "barbell").weight!!, 1e-9)
    }

    @Test
    fun stallsBringTheDeloadForward() {
        var p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup)
        // Two weeks in, most exercises have stalled twice.
        p = p.copy(
            sessionsCompleted = 6,
            days = p.days.map { d -> d.copy(exercises = d.exercises.map { it.copy(sessions = 2, stalls = 2, weight = it.weight ?: 40.0) }) },
        )
        val w = p.buildWorkout(p.nextDayIndex, "kg", lookup)!!.let { w ->
            w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(weight = it.weight ?: 40.0, reps = 1, completed = true) }) })
        }
        val (next, changes) = p.afterSession(w, "kg", lookup)
        assertEquals(3, next.trainingWeeks)
        assertTrue(changes.any { it.startsWith("Fatigue is building") })
        assertFalse(next.isDeload)
        // After this week's other two sessions the deload starts, and once it's done the next block is shorter.
        var q = next.copy(sessionsCompleted = 9)
        assertTrue(q.isDeload)
        q = q.copy(sessionsCompleted = 11)
        val (block, notes) = q.afterSession(q.buildWorkout(q.nextDayIndex, "kg", lookup)!!, "kg", lookup)
        assertEquals(1, block.mesoIndex)
        assertEquals(3, block.mesoWeeks)
        assertEquals(0, block.weekIndex)
        assertTrue(notes.any { it.contains("a week shorter") })
    }

    @Test
    fun blockReviewKeepsEarnedSets() {
        val p0 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 2, Equipment.GYM, "kg", lookup)
        val first = p0.days[0].exercises[0]
        val last = p0.days[0].exercises.last()
        // End of the deload week of block 1: one lift earned a set, another lost one.
        val p = p0.copy(
            sessionsCompleted = p0.sessionsPerWeek * (p0.mesoWeeks + 1) - 1,
            days = p0.days.map { d ->
                d.copy(exercises = d.exercises.map {
                    when (it.id) {
                        first.id -> it.copy(sessions = 8, sets = it.baseSets + 1, stalls = 0)
                        last.id -> it.copy(sessions = 8, sets = it.baseSets - 1, stalls = 1)
                        else -> it.copy(sessions = 8, stalls = 1)
                    }
                })
            },
        )
        val (next, notes) = p.afterSession(p.buildWorkout(p.nextDayIndex, "kg", lookup)!!, "kg", lookup)
        val all = next.days.flatMap { it.exercises }
        assertEquals(first.baseSets + 1, all.first { it.id == first.id }.baseSets)
        assertEquals(maxOf(2, last.baseSets - 1), all.first { it.id == last.id }.baseSets)
        assertTrue(all.all { it.sets == it.baseSets })
        assertEquals(4, next.mesoWeeks)
        assertTrue(notes.any { it.startsWith("Deload done") })
    }

    @Test
    fun oldProgramsKeepTheirPlaceInTheBlock() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 3, Equipment.GYM, "kg", lookup)
            .copy(perWeek = 0, sessionsCompleted = 17)
        val m = p.migrated()
        assertEquals(1, m.mesoIndex)
        assertEquals(15, m.mesoStart)
        assertEquals(0, m.weekIndex)
    }
}
