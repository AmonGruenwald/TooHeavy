package com.tooheavy.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The new program and coach fields must round-trip through Gson and leave older save files loadable. */
class PersistenceTest {
    private val gson = Gson()
    private val anyExercise: (String) -> Exercise? = { Exercise(id = it, equipment = "barbell") }

    @Test
    fun programAndActiveWorkoutRoundTrip() {
        val program = generateProgram(TrainingGoal.STRENGTH, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", anyExercise) { 100.0 }
        val data = AppData(program = program, activeWorkout = program.buildWorkout(0, "kg", anyExercise))
        assertEquals(data, gson.fromJson(gson.toJson(data), AppData::class.java))
    }

    @Test
    fun coachDataRoundTrips() {
        val data = NutritionData(
            coach = CoachSettings(enabled = true, weeklyRatePct = 0.75, dietStyle = DietStyle.KETO),
            checkIns = listOf(CheckIn("2026-10-26", 80.4, -0.3, 2650, NutritionGoals(2200, 160, 30, 160))),
        )
        assertEquals(data, gson.fromJson(gson.toJson(data), NutritionData::class.java))
    }

    @Test
    fun olderSaveFilesLoadWithDefaults() {
        val app = gson.fromJson(
            """{"workouts":[{"id":"w","name":"Push","startTime":1,"endTime":2,"exercises":[{"id":"e","exerciseId":"x","sets":[]}]}],
               "settings":{"unit":"kg","defaultRestSeconds":90}}""",
            AppData::class.java,
        )
        assertNull(app.program)
        assertNull(app.workouts[0].programDayId)
        assertNull(app.workouts[0].exercises[0].target)

        val nutrition = gson.fromJson("""{"goals":{"kcal":2000,"protein":150,"carbs":200,"fat":60}}""", NutritionData::class.java)
        assertEquals(CoachSettings(), nutrition.coach)
        assertTrue(nutrition.checkIns.isEmpty())
    }
}
