package com.tooheavy.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calorie carry-over (1.9.0). */
class CarryOverTest {
    private val goal = 2000

    /** Carry-over into [date] with a flat goal; [eaten] maps "MM-dd" in October 2026 to kcal. */
    private fun carried(date: String, vararg eaten: Pair<String, Int>, goalOn: (String) -> Int = { goal }): Int =
        carryOverKcal("2026-$date", eaten.associate { (d, k) -> "2026-$d" to k.toDouble() }, goalOn)

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
    fun carryOverSettingsRoundTripAndOldFilesLoad() {
        val gson = Gson()
        val data = NutritionData(carryOver = false, kcalGoalHistory = listOf(KcalGoalChange("", 2200), KcalGoalChange("2026-10-30", 2000)))
        assertEquals(data, gson.fromJson(gson.toJson(data), NutritionData::class.java))

        val old = gson.fromJson("""{"goals":{"kcal":2000,"protein":150,"carbs":200,"fat":60}}""", NutritionData::class.java)
        assertTrue(old.carryOver)
        assertTrue(old.kcalGoalHistory.isEmpty())
    }
}
