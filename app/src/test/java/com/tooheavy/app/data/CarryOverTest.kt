package com.tooheavy.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calorie carry-over (1.9.0) and day marks (1.11.0). */
class CarryOverTest {
    private val goal = 2000

    /**
     * Carry-over into [date] with a flat goal; [eaten] maps "MM-dd" in October 2026 to kcal, [marks] "MM-dd" to a DayMark.
     */
    private fun carried(
        date: String,
        vararg eaten: Pair<String, Int>,
        marks: Map<String, String> = emptyMap(),
        goalOn: (String) -> Int = { goal },
    ): Int = carryOverKcal(
        "2026-$date",
        countedKcal(eaten.associate { (d, k) -> "2026-$d" to k.toDouble() }, marks.mapKeys { "2026-${it.key}" }),
        goalOn,
    )

    @Test
    fun overAndUnderMoveTheNextDay() {
        assertEquals(-500, carried("10-02", "10-01" to 2500))
        assertEquals(400, carried("10-02", "10-01" to 1600))
        assertEquals(0, carried("10-02", "10-01" to 2000))
        // Nothing logged, nothing carried.
        assertEquals(0, carried("10-02"))
    }

    @Test
    fun eatingTheAdjustedGoalSettlesIt() {
        assertEquals(0, carried("10-03", "10-01" to 2500, "10-02" to 1500))
        // Partly made up: the rest stays.
        assertEquals(-300, carried("10-03", "10-01" to 2500, "10-02" to 1800))
        // Over again: it adds up.
        assertEquals(-800, carried("10-03", "10-01" to 2500, "10-02" to 2300))
    }

    @Test
    fun aDifferenceIsDroppedAfterAWeek() {
        val onGoal = (2..9).map { "10-0$it" to goal }.toTypedArray()
        assertEquals(-500, carried("10-08", "10-01" to 2500, *onGoal))
        assertEquals(0, carried("10-09", "10-01" to 2500, *onGoal))
    }

    @Test
    fun daysWithoutFoodDontCount() {
        assertEquals(-500, carried("10-04", "10-01" to 2500))
        assertEquals(-500, carried("10-04", "10-01" to 2500, "10-02" to 0))
    }

    @Test
    fun theOldestDifferenceIsMadeUpFirst() {
        val days = arrayOf("10-01" to 2300, "10-02" to 2200, "10-03" to 1600)
        // 400 under pays the 300 over of 10-01 and 100 of the 200 of 10-02.
        assertEquals(-100, carried("10-04", *days))
        // The rest is from 10-02, so it lasts until 10-09.
        assertEquals(-100, carried("10-09", *days))
        assertEquals(0, carried("10-10", *days))
    }

    @Test
    fun aSettledDifferenceDoesntComeBack() {
        val days = arrayOf("10-01" to 2500, "10-02" to 1500)
        for (d in listOf("10-03", "10-08", "10-09", "10-10")) assertEquals(d, 0, carried(d, *days))
    }

    @Test
    fun pastDaysKeepTheGoalTheyHad() {
        val history = listOf(KcalGoalChange("", 2000), KcalGoalChange("2026-10-02", 1800))
        assertEquals(2000, kcalGoalAt("2026-10-01", history, 1800))
        assertEquals(1800, kcalGoalAt("2026-10-02", history, 1800))
        assertEquals(2200, kcalGoalAt("2026-10-02", emptyList(), 2200))
        val goalOn = { d: String -> kcalGoalAt(d, history, 1800) }
        assertEquals(0, carried("10-03", "10-01" to 2000, "10-02" to 1800, goalOn = goalOn))
    }

    @Test
    fun theMinimumGoalHoldsBackTheRest() {
        assertEquals(1700, adjustedKcalGoal(2000, -300, 1500))
        assertEquals(1500, adjustedKcalGoal(2000, -800, 1500))
        assertEquals(2400, adjustedKcalGoal(2000, 400, 1500))
        // A goal already below the minimum isn't raised to it.
        assertEquals(1400, adjustedKcalGoal(1400, -300, 1500))
        // 800 over, the next goal stops at 1,500: eating that makes up 500 and 300 carry on.
        assertEquals(-300, carried("10-03", "10-01" to 2800, "10-02" to 1500))
    }

    @Test
    fun aDayNotProperlyTrackedIsSkipped() {
        val untracked = mapOf("10-01" to DayMark.UNTRACKED)
        assertEquals(0, carried("10-02", "10-01" to 900, marks = untracked))
        // Differences from before it pass over it and still age.
        assertEquals(-500, carried("10-03", "10-01" to 2500, "10-02" to 800, marks = mapOf("10-02" to DayMark.UNTRACKED)))
        assertEquals(0, carried("10-09", "10-01" to 2500, "10-02" to 800, marks = mapOf("10-02" to DayMark.UNTRACKED)))
    }

    @Test
    fun aDayLeftEmptyOnPurposeCarriesItsWholeGoal() {
        val empty = mapOf("10-01" to DayMark.EMPTY)
        assertEquals(2000, carried("10-02", marks = empty))
        // It pays off earlier days over the goal like any day under it.
        assertEquals(1500, carried("10-03", "10-01" to 2500, marks = mapOf("10-02" to DayMark.EMPTY)))
        assertEquals(2000, carried("10-08", marks = empty))
        assertEquals(0, carried("10-09", marks = empty))
    }

    @Test
    fun marksDecideWhichDaysCount() {
        val eaten = mapOf("a" to 1800.0, "b" to 900.0, "c" to 0.0)
        val marks = mapOf("b" to DayMark.UNTRACKED, "d" to DayMark.EMPTY)
        assertEquals(mapOf("a" to 1800.0, "d" to 0.0), countedKcal(eaten, marks))
        assertEquals(mapOf("a" to 1800.0, "b" to 900.0), countedKcal(eaten, emptyMap()))
    }

    @Test
    fun theCoachSkipsUntrackedDaysAndCountsEmptyOnes() {
        val profile = BodyProfile(sex = "male", age = 30, heightCm = 180.0, weightKg = 80.0, activity = 1.55)
        val asOf = "2026-10-29"
        val days = (1..28).map { shiftDate(asOf, -it) }
        val weights = days.map { WeightEntry(it, 80.0) } + WeightEntry(asOf, 80.0)
        val intake = days.associateWith { 2500.0 }
        val full = estimateExpenditure(profile, countedKcal(intake, emptyMap()), weights, asOf)
        // A day logged at 500 kcal but marked not properly tracked doesn't move the estimate.
        val partial = intake + (days[3] to 500.0)
        val skipped = estimateExpenditure(profile, countedKcal(partial, mapOf(days[3] to DayMark.UNTRACKED)), weights, asOf)
        assertEquals(full.dataKcal, skipped.dataKcal)
        assertEquals(full.loggedDays - 1, skipped.loggedDays)
        // A fasting day counts at 0 kcal.
        val fasted = estimateExpenditure(profile, countedKcal(intake - days[3], mapOf(days[3] to DayMark.EMPTY)), weights, asOf)
        assertEquals(full.loggedDays, fasted.loggedDays)
        assertTrue(fasted.dataKcal!! < full.dataKcal!!)
    }

    @Test
    fun carryOverSettingsRoundTripAndOldFilesLoad() {
        val gson = Gson()
        val data = NutritionData(
            carryOver = false,
            kcalGoalHistory = listOf(KcalGoalChange("", 2200), KcalGoalChange("2026-10-30", 2000)),
            dayMarks = mapOf("2026-10-28" to DayMark.UNTRACKED, "2026-10-29" to DayMark.EMPTY),
        )
        assertEquals(data, gson.fromJson(gson.toJson(data), NutritionData::class.java))

        val old = gson.fromJson("""{"goals":{"kcal":2000,"protein":150,"carbs":200,"fat":60}}""", NutritionData::class.java)
        assertTrue(old.carryOver)
        assertTrue(old.kcalGoalHistory.isEmpty())
        assertTrue(old.dayMarks.isEmpty())
    }
}
