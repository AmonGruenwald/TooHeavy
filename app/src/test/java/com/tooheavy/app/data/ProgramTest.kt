package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramTest {
    companion object {
        private val library: List<Exercise> by lazy {
            val file = listOf("src/main/assets/exercises.json", "app/src/main/assets/exercises.json").map(::File).first { it.exists() }
            Gson().fromJson(file.readText(), object : TypeToken<List<Exercise>>() {}.type)
        }
        private val byId: Map<String, Exercise> by lazy { library.associateBy { it.id } }
    }

    private val lookup: (String) -> Exercise? = { byId[it] }

    private fun bench(weight: Double? = 60.0, sets: Int = 3, targetReps: Int = 8, sessions: Int = 1) = ProgramExercise(
        exerciseId = "Barbell_Bench_Press_-_Medium_Grip", pattern = Pattern.HORIZONTAL_PUSH, role = Role.PRIMARY,
        sets = sets, baseSets = 3, repMin = 8, repMax = 12, targetReps = targetReps, weight = weight, sessions = sessions,
    )

    /** A workout entry for [pe] where the sets listed as (weight, reps) were completed. */
    private fun did(pe: ProgramExercise, vararg sets: Pair<Double?, Int>) = WorkoutExercise(
        exerciseId = pe.exerciseId,
        programExerciseId = pe.id,
        target = SetTarget(pe.sets, pe.targetReps, pe.repMin, pe.repMax, pe.weight, 2),
        sets = sets.map { (w, r) -> SetEntry(weight = w, reps = r, completed = true) },
    )

    private fun progress(pe: ProgramExercise, vararg sets: Pair<Double?, Int>) =
        progressExercise(pe, did(pe, *sets), "kg", "barbell")

    // ---------- generator ----------

    @Test
    fun everyCandidateIsInTheLibrary() {
        assertEquals(emptySet<String>(), candidateIds - byId.keys)
    }

    @Test
    fun generatesUsableProgramsForEveryCombination() {
        for (goal in TrainingGoal.all) for (level in Experience.all) for (equipment in Equipment.all) for (n in 2..6) {
            val p = generateProgram(goal, level, n, equipment, "kg", lookup)
            val label = "$goal/$level/$equipment/$n"
            assertEquals(label, n, p.days.size)
            for (d in p.days) {
                assertTrue("$label ${d.name} has ${d.exercises.size} exercises", d.exercises.size in 3..7)
                assertEquals("$label ${d.name} repeats an exercise", d.exercises.size, d.exercises.map { it.exerciseId }.toSet().size)
                for (e in d.exercises) {
                    assertNotNull(byId[e.exerciseId])
                    assertTrue(e.repMin < e.repMax && e.sets == e.baseSets && e.targetReps == e.repMin)
                }
            }
        }
    }

    @Test
    fun beginnersGetShorterSessionsWithoutExpertLifts() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 4, Equipment.GYM, "kg", lookup)
        assertTrue(p.days.all { d -> d.exercises.size <= 5 })
        assertTrue(p.days.flatMap { it.exercises }.none { byId[it.exerciseId]?.level == "expert" })
    }

    @Test
    fun equipmentIsRespected() {
        val p = generateProgram(TrainingGoal.GENERAL, Experience.INTERMEDIATE, 3, Equipment.HOME, "kg", lookup)
        val gear = p.days.flatMap { it.exercises }.mapNotNull { byId[it.exerciseId]?.equipment }.toSet()
        assertTrue("home program uses $gear", gear.all { it in setOf("dumbbell", "kettlebells", "body only", "bands") })
    }

    @Test
    fun startingWeightsComeFromHistory() {
        val p = generateProgram(TrainingGoal.STRENGTH, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup) { id ->
            if (id == "Barbell_Bench_Press_-_Medium_Grip") 100.0 else null
        }
        val bench = p.days.flatMap { it.exercises }.first { it.exerciseId == "Barbell_Bench_Press_-_Medium_Grip" }
        // 4 reps with 3 in reserve: 100 / (1 + 7/30) = 81.1, rounded down to 80.
        assertEquals(80.0, bench.weight!!, 1e-9)
        assertNull(p.days.flatMap { it.exercises }.first { it.exerciseId == "Barbell_Squat" }.weight)
    }

    @Test
    fun swapOptionsStayInPatternAndEquipment() {
        val pe = bench()
        val options = swapOptions(pe, Equipment.HOME, library)
        assertTrue(options.isNotEmpty())
        assertEquals("Dumbbell_Bench_Press", options.first().id)
        assertTrue(options.none { it.equipment == "barbell" || it.id == pe.exerciseId })
        val swapped = pe.swapTo(byId.getValue("Pushups"), TrainingGoal.HYPERTROPHY, "kg", null)
        assertNull(swapped.weight)
        assertEquals(0, swapped.sessions)
        assertEquals(8 to 15, swapped.repMin to swapped.repMax)
    }

    // ---------- progression ----------

    @Test
    fun firstSessionCalibrates() {
        val next = progress(bench(weight = null, sessions = 0), 70.0 to 10, 70.0 to 10, 70.0 to 9)
        assertEquals(70.0, next.weight!!, 0.0)
        assertEquals(10, next.targetReps)
        assertEquals(3, next.sets)
        assertEquals(0, next.stalls)
        assertEquals(1, next.sessions)
    }

    @Test
    fun repsClimbAtTheSameWeight() {
        val next = progress(bench(targetReps = 8), 60.0 to 9, 60.0 to 9, 60.0 to 8)
        assertEquals(60.0, next.weight!!, 0.0)
        assertEquals(9, next.targetReps)
        assertEquals(1, next.streak)
    }

    @Test
    fun weightGoesUpOnceEverySetHitsTheTop() {
        val next = progress(bench(targetReps = 12), 60.0 to 12, 60.0 to 12, 60.0 to 13)
        assertEquals(62.5, next.weight!!, 1e-9)
        assertEquals(8, next.targetReps)
        val squat = bench(targetReps = 12).copy(exerciseId = "Barbell_Squat", pattern = Pattern.SQUAT)
        assertEquals(65.0, progress(squat, 60.0 to 12, 60.0 to 12, 60.0 to 12).weight!!, 1e-9)
        val lb = progressExercise(bench(targetReps = 12), did(bench(targetReps = 12), 135.0 to 12, 135.0 to 12, 135.0 to 12), "lb", "barbell")
        assertEquals(140.0, lb.weight!!, 1e-9)
    }

    @Test
    fun skippedSetsBlockTheWeightJump() {
        val next = progress(bench(targetReps = 12), 60.0 to 12, 60.0 to 12)
        assertEquals(60.0, next.weight!!, 0.0)
        assertEquals(12, next.targetReps)
    }

    @Test
    fun goingHeavierThanAskedCounts() {
        val next = progress(bench(targetReps = 10), 65.0 to 8, 65.0 to 8, 60.0 to 10)
        assertEquals(65.0, next.weight!!, 0.0)
        assertEquals(9, next.targetReps)
        assertEquals(1, next.streak)
    }

    @Test
    fun threeStalledSessionsResetTheWeight() {
        var p = bench(targetReps = 10)
        repeat(2) { p = progress(p, 60.0 to 9, 60.0 to 9, 60.0 to 9) }
        assertEquals(2, p.stalls)
        assertEquals(60.0, p.weight!!, 0.0)
        p = progress(p, 60.0 to 9, 60.0 to 9, 60.0 to 9)
        assertEquals(55.0, p.weight!!, 1e-9)
        assertEquals(8, p.targetReps)
        assertEquals(0, p.stalls)
    }

    @Test
    fun twoHitsInARowAddASetUpToTheCap() {
        var p = bench(targetReps = 8)
        p = progress(p, 60.0 to 8, 60.0 to 8, 60.0 to 8)
        assertEquals(3, p.sets)
        p = progress(p, 60.0 to 9, 60.0 to 9, 60.0 to 9)
        assertEquals(4, p.sets)
        assertEquals(0, p.streak)
        p = p.copy(sets = 5)
        repeat(2) { p = progress(p, *Array(5) { 60.0 to p.targetReps }) }
        assertEquals(5, p.sets) // baseSets + 2
    }

    @Test
    fun fallingBelowTheRangeRemovesASet() {
        val next = progress(bench(sets = 4, targetReps = 10), 60.0 to 7, 60.0 to 6, 60.0 to 6, 60.0 to 6)
        assertEquals(3, next.sets)
        assertEquals(60.0, next.weight!!, 0.0)
        assertEquals(8, next.targetReps)
        val floor = progress(bench(sets = 2, targetReps = 10), 60.0 to 7, 60.0 to 6)
        assertEquals(2, floor.sets) // baseSets - 1
    }

    @Test
    fun wayTooHeavyDropsTheWeight() {
        val next = progress(bench(targetReps = 8), 60.0 to 4, 60.0 to 4, 60.0 to 3)
        assertEquals(55.0, next.weight!!, 1e-9)
        assertEquals(8, next.targetReps)
    }

    @Test
    fun warmupSetsAreIgnored() {
        val pe = bench(targetReps = 12)
        val we = did(pe, 60.0 to 12, 60.0 to 12, 60.0 to 12).let { w ->
            w.copy(sets = listOf(SetEntry(weight = 100.0, reps = 1, completed = true, type = SetType.WARMUP)) + w.sets)
        }
        assertEquals(62.5, progressExercise(pe, we, "kg", "barbell").weight!!, 1e-9)
    }

    @Test
    fun bodyweightProgressesReps() {
        val pullups = ProgramExercise(
            exerciseId = "Pullups", pattern = Pattern.VERTICAL_PULL, role = Role.SECONDARY,
            sets = 3, baseSets = 3, repMin = 8, repMax = 15, targetReps = 5, weight = null, sessions = 1,
        )
        val next = progress(pullups, null to 6, null to 5, null to 5)
        assertNull(next.weight)
        assertEquals(6, next.targetReps)
        assertEquals(1, next.streak)
        val missed = progress(next, null to 4, null to 3, null to 3)
        assertEquals(2, missed.sets)
    }

    // ---------- schedule ----------

    /** Completes every prescribed set of [p]'s next day, filling missing weights with 50. */
    private fun finishNext(p: Program): Pair<Program, List<String>> {
        val w = p.buildWorkout(p.nextDayIndex, "kg", lookup)!!
        val done = w.copy(exercises = w.exercises.map { we ->
            we.copy(sets = we.sets.map { it.copy(weight = it.weight ?: if (we.target?.weight == null && byId[we.exerciseId]?.equipment == "body only") null else 50.0, completed = true) })
        })
        return p.afterSession(done, "kg", lookup)
    }

    @Test
    fun mesocycleRampsEffortThenDeloads() {
        var p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup)
        assertEquals(0, p.nextDayIndex)
        assertEquals(3, p.targetRir)
        val firstBuild = p.buildWorkout(0, "kg", lookup)!!
        assertEquals(p.days[0].id, firstBuild.programDayId)
        assertTrue(firstBuild.exercises.all { it.target?.rir == 3 && it.programExerciseId != null })

        val rirs = mutableListOf<Int>()
        var lastChanges = emptyList<String>()
        repeat(12) {
            if (it % 3 == 0) rirs.add(p.targetRir)
            val r = finishNext(p)
            p = r.first
            lastChanges = r.second
        }
        assertEquals(listOf(3, 2, 2, 1), rirs)
        assertTrue(p.isDeload)
        assertTrue(lastChanges.any { it.startsWith("Next week is a deload") })

        val beforeDeload = p.days.flatMap { it.exercises }
        val deload = p.buildWorkout(p.nextDayIndex, "kg", lookup)!!
        for (we in deload.exercises) {
            val pe = beforeDeload.first { it.id == we.programExerciseId }
            val t = we.target!!
            assertTrue(t.deload)
            assertEquals(4, t.rir)
            assertEquals((pe.baseSets + 1) / 2, t.sets)
            if (pe.weight != null) assertTrue(t.weight!! < pe.weight!!)
        }

        repeat(3) {
            val r = finishNext(p)
            p = r.first
            lastChanges = r.second
        }
        assertEquals(2, p.mesocycle)
        assertEquals(1, p.mesoWeek)
        assertFalse(p.isDeload)
        assertTrue(lastChanges.any { it.startsWith("Deload done") })
        // Deload sessions don't move targets, and the new block starts at base volume.
        val after = p.days.flatMap { it.exercises }
        for (pe in after) {
            val old = beforeDeload.first { it.id == pe.id }
            assertEquals(old.weight, pe.weight)
            assertEquals(pe.baseSets, pe.sets)
        }
    }

    @Test
    fun rotationFollowsTheDayActuallyDone() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 4, Equipment.GYM, "kg", lookup)
        val w = p.buildWorkout(2, "kg", lookup)!!.let { w ->
            w.copy(exercises = w.exercises.map { we -> we.copy(sets = we.sets.map { it.copy(weight = 40.0, completed = true) }) })
        }
        val (next, changes) = p.afterSession(w, "kg", lookup)
        assertEquals(3, next.nextDayIndex)
        assertEquals(1, next.sessionsCompleted)
        assertEquals(w.exercises.size, changes.size)
        // A workout that isn't from this program leaves it alone.
        assertEquals(p, p.afterSession(Workout(name = "Other"), "kg", lookup).first)
    }
}
