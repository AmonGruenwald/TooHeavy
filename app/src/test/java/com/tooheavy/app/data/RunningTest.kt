package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Running workouts and running plans (1.12.0). */
class RunningTest {
    private val library: List<Exercise> by lazy {
        val dir = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.exists() }
        listOf("exercises.json", "exercises_extra.json").flatMap {
            Gson().fromJson<List<Exercise>>(File(dir, it).readText(), object : TypeToken<List<Exercise>>() {}.type)
        }.map { it.withDefaultTracking() }
    }

    @Test
    fun runsLogDistanceAndTime() {
        val byId = library.associateBy { it.id }
        val running = byId[RUNNING_EXERCISE_ID]!!
        assertTrue(running.tracksDistance && running.isTimed)
        assertTrue(running.primaryMuscles.isEmpty())
        assertTrue(byId["Running_Treadmill"]!!.tracksDistance)
        assertTrue(byId["Rowing_Stationary"]!!.tracksDistance)
        assertFalse(byId["Barbell_Squat"]!!.isTimed)
        // No ramp-up sets and no weight for a run.
        assertFalse(WarmUps.suits(running))
    }

    @Test
    fun distancesAndPaces() {
        assertEquals("5 km", formatDistance(5.0, "km"))
        assertEquals("10 km", formatDistance(10.0, "km"))
        assertEquals("21.1 km", formatDistance(21.0975, "km"))
        assertEquals("3.11 mi", formatDistance(5.0, "mi"))
        assertEquals(5.0, kmFrom(kmIn(5.0, "mi"), "mi"), 1e-9)
        assertEquals("5:30 /km", formatPace(1650, 5.0, "km"))
        assertNull(formatPace(1650, null, "km"))
        assertNull(formatPace(null, 5.0, "km"))
        assertEquals("5 km · 27:30 · 5:30 /km", SetEntry(distanceKm = 5.0, seconds = 1650).cardioText("km"))
        assertEquals("5:00", SetEntry(seconds = 300).cardioText("km"))
        assertNull(SetEntry(weight = 60.0, reps = 8).cardioText("km"))
        assertEquals("km", distanceUnit("kg"))
        assertEquals("mi", distanceUnit("lb"))
    }

    @Test
    fun buildingAPlan() {
        val plan = RunPlan(startDate = "2026-11-02", weeks = List(3) { RunWeek() })
        val long = PlannedRun(id = "long", name = RunType.label(RunType.LONG), type = RunType.LONG, day = 7, distanceKm = 12.0)
        val easy = PlannedRun(id = "easy", name = RunType.label(RunType.EASY), type = RunType.EASY, day = 2, notes = "Conversational")
        val anyDay = PlannedRun(id = "any", name = RunType.label(RunType.TEMPO), type = RunType.TEMPO)
        // Added runs go into their week in weekday order; runs on any day come last.
        var p = plan.saveRun(0, long).saveRun(0, anyDay).saveRun(0, easy)
        assertEquals(listOf("easy", "long", "any"), p.weeks[0].runs.map { it.id })
        assertTrue(p.weeks[1].runs.isEmpty())
        // Saving an existing run replaces it where it is, whatever week is passed.
        p = p.saveRun(2, easy.copy(day = 1, notes = "Hills"))
        assertEquals(listOf("easy", "long", "any"), p.weeks[0].runs.map { it.id })
        assertEquals("Hills", p.findRun("easy")?.notes)
        assertTrue(p.weeks[2].runs.isEmpty())
        // Copying a week's runs gives the next week fresh, open copies.
        p = p.updateRun("easy") { it.copy(doneWorkoutId = "w1") }.copyRunsToNextWeek(0)
        assertEquals(listOf(RunType.EASY, RunType.LONG, RunType.TEMPO), p.weeks[1].runs.map { it.type })
        assertTrue(p.weeks[1].runs.all { it.open && it.id !in setOf("easy", "long", "any") })
        assertEquals(p, p.copyRunsToNextWeek(2))
        assertEquals("Mon · Easy run", p.findRun("easy")?.title())
        assertEquals("Tempo run", anyDay.title())
        assertEquals("Run", RunType.label(null))
    }

    @Test
    fun followingThePlan() {
        val plan = RunPlan(
            startDate = "2026-11-02",
            weeks = listOf(
                RunWeek(runs = listOf(PlannedRun(id = "a"), PlannedRun(id = "b"))),
                RunWeek(runs = listOf(PlannedRun(id = "c"))),
            ),
        )
        assertEquals("2026-11-02", mondayOf("2026-11-04"))
        assertEquals("2026-11-02", mondayOf("2026-11-02"))
        assertEquals("2026-11-02", mondayOf("2026-11-08"))
        assertEquals(-1, plan.weekIndexOn("2026-11-01"))
        assertEquals(0, plan.weekIndexOn("2026-11-08"))
        assertEquals(1, plan.weekIndexOn("2026-11-09"))
        // Before the start the first run is next; done and skipped runs are passed over.
        assertEquals("a", plan.nextRun("2026-10-30")?.second?.id)
        val doneA = plan.updateRun("a") { it.copy(doneWorkoutId = "w1") }
        assertEquals("b", doneA.nextRun("2026-11-03")?.second?.id)
        assertEquals("c", doneA.updateRun("b") { it.copy(skipped = true) }.nextRun("2026-11-03")?.second?.id)
        // Missed runs of past weeks aren't next; the current week's are.
        assertEquals("c", doneA.nextRun("2026-11-10")?.second?.id)
        assertNull(plan.nextRun("2026-11-16"))
        assertEquals(1 to 3, doneA.progress())
        assertEquals("c", plan.findRun("c")?.id)
    }

    @Test
    fun plansAndRunsRoundTrip() {
        val gson = Gson()
        val data = AppData(
            runPlan = RunPlan(
                name = "10K",
                startDate = "2026-11-02",
                weeks = listOf(
                    RunWeek(
                        note = "Easy week",
                        runs = listOf(PlannedRun(name = "Easy run", type = RunType.EASY, day = 2, distanceKm = 5.0, notes = "Flat route")),
                    ),
                ),
            ),
            workouts = listOf(
                Workout(
                    plannedRunId = "r1",
                    exercises = listOf(WorkoutExercise(exerciseId = RUNNING_EXERCISE_ID, sets = listOf(SetEntry(distanceKm = 5.0, seconds = 1650)))),
                ),
            ),
        )
        assertEquals(data, gson.fromJson(gson.toJson(data), AppData::class.java))
        assertNull(gson.fromJson("{}", AppData::class.java).runPlan)
    }
}
