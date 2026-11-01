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
    fun aLineBecomesARunWithTargets() {
        assertEquals(5.0, parsePlannedRun("Easy run 5 km")!!.distanceKm!!, 1e-9)
        assertEquals(10.0, parsePlannedRun("- Long run 10k")!!.distanceKm!!, 1e-9)
        assertEquals(4.83, parsePlannedRun("Easy 3 miles")!!.distanceKm!!, 1e-9)
        assertEquals("Long run 10k", parsePlannedRun("- Long run 10k")!!.name)
        val tempo = parsePlannedRun("Tempo 20 min")!!
        assertEquals(1200, tempo.seconds)
        assertNull(tempo.distanceKm)
        assertEquals(3600, parsePlannedRun("Easy 1 h")!!.seconds)
        // Intervals and paces aren't the run's length.
        val intervals = parsePlannedRun("Run 1 min, walk 2 min × 8")!!
        assertNull(intervals.seconds)
        assertNull(intervals.distanceKm)
        assertNull(parsePlannedRun("Run 1 min, walk 90 s × 8")!!.seconds)
        assertNull(parsePlannedRun("8x 3 min hills")!!.seconds)
        assertEquals(1800, parsePlannedRun("Max 30 min easy")!!.seconds)
        val paced = parsePlannedRun("8 km at 5:30 min/km")!!
        assertEquals(8.0, paced.distanceKm!!, 1e-9)
        assertNull(paced.seconds)
        // Rest days aren't runs.
        assertNull(parsePlannedRun("Rest"))
        assertNull(parsePlannedRun("Rest day"))
        assertNull(parsePlannedRun("   "))
    }

    @Test
    fun planTextBecomesWeeks() {
        val weeks = parseRunPlan(
            """
            Week 1
            Easy run 3 km
            Rest
            Run 1 min, walk 90 s × 8

            Long run 5 km
            Week 2: Easy run 4 km; Tempo 20 min; Long run 6 km
            """.trimIndent(),
        )
        assertEquals(2, weeks.size)
        assertEquals(listOf("Easy run 3 km", "Run 1 min, walk 90 s × 8", "Long run 5 km"), weeks[0].runs.map { it.name })
        assertEquals(listOf("Easy run 4 km", "Tempo 20 min", "Long run 6 km"), weeks[1].runs.map { it.name })

        // Without "Week" lines, blank lines separate the weeks.
        val blocks = parseRunPlan("3 km easy\n4 km easy\n\n\n4 km easy\n5 km long")
        assertEquals(listOf(2, 2), blocks.map { it.runs.size })
        assertTrue(parseRunPlan("").isEmpty())
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
            runPlan = RunPlan(name = "10K", startDate = "2026-11-02", weeks = listOf(RunWeek(runs = listOf(PlannedRun(name = "Easy 5 km", distanceKm = 5.0))))),
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
