package com.tooheavy.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow

class CoachTest {
    private val today = "2026-10-30" // a Friday
    private val profile = BodyProfile(sex = "male", age = 30, heightCm = 180.0, weightKg = 80.0, activity = 1.55, goal = "lose")

    private fun daysBack(n: Int) = (n downTo 1).map { shiftDate(today, -it) }

    @Test
    fun dateHelpers() {
        assertEquals(7, daysBetween("2026-10-23", "2026-10-30"))
        assertEquals(-1, daysBetween("2026-10-30", "2026-10-29"))
        assertEquals(31, daysBetween("2026-03-01", "2026-04-01")) // across a DST change
        assertEquals(Calendar.FRIDAY, dayOfWeek(today))
    }

    @Test
    fun trendCompoundsSmoothingOverGaps() {
        val t = weightTrend(listOf(WeightEntry("2026-10-01", 80.0), WeightEntry("2026-10-08", 78.0)))
        assertEquals(80.0, t.first().trendKg, 0.0)
        assertEquals(80.0 - 2.0 * (1 - 0.9.pow(7)), t.last().trendKg, 1e-9)
        assertEquals(78.0, t.trendOn("2026-10-20")!!, 1.0)
        assertNull(t.trendOn("2026-09-30"))
    }

    @Test
    fun trendDampsDailyNoise() {
        val weights = (0 until 60).map { WeightEntry(shiftDate(today, -it), if (it % 2 == 0) 80.0 else 81.0) }
        val trend = weightTrend(weights).takeLast(10).map { it.trendKg }
        assertTrue(trend.all { abs(it - 80.5) < 0.1 })
    }

    @Test
    fun expenditureUsesFormulaWithoutData() {
        val e = estimateExpenditure(profile, emptyMap(), emptyList(), today)
        assertEquals(profile.tdee(), e.kcal)
        assertNull(e.dataKcal)
        assertEquals(0.0, e.confidence, 0.0)
    }

    @Test
    fun expenditureRecoversTrueBurnFromSteadyLoss() {
        // Eating 2500 kcal a day while losing 0.5 kg a week means burning 2500 + 0.5 * 7700 / 7 = 3050.
        val perDay = 0.5 / 7
        val weights = (90 downTo 0).map { WeightEntry(shiftDate(today, -it), 85.0 - perDay * (90 - it)) }
        val intake = daysBack(90).associateWith { 2500.0 }
        val e = estimateExpenditure(profile, intake, weights, today)
        assertEquals(1.0, e.confidence, 1e-9)
        assertEquals(3050.0, e.kcal.toDouble(), 10.0)
        assertEquals(EXPENDITURE_WINDOW, e.spanDays)
    }

    @Test
    fun unloggedDaysAreSkippedNotCountedAsZero() {
        val weights = (40 downTo 0).map { WeightEntry(shiftDate(today, -it), 80.0) }
        val intake = daysBack(40).filterIndexed { i, _ -> i % 2 == 0 }.associateWith { 2600.0 }
        val e = estimateExpenditure(profile, intake, weights, today)
        assertEquals(2600, e.dataKcal)
        assertTrue(e.loggedDays in 10..11)
    }

    @Test
    fun sparseDataIsShrunkTowardFormula() {
        val weights = listOf(14, 10, 7, 3, 0).map { WeightEntry(shiftDate(today, -it), 80.0) }
        val intake = daysBack(10).associateWith { 2200.0 }
        val e = estimateExpenditure(profile, intake, weights, today)
        assertEquals(2200, e.dataKcal)
        assertTrue(e.confidence > 0 && e.confidence < 0.5)
        assertTrue(e.kcal in 2201 until e.formulaKcal)
    }

    @Test
    fun goalsForSteadyLoss() {
        val s = CoachSettings(enabled = true, weeklyRatePct = 0.5, proteinPerKg = 2.0)
        val g = coachGoals(2500, profile, s, 80.0)
        // 0.5 % of 80 kg = 0.4 kg a week = 440 kcal a day below expenditure.
        assertEquals(2060, g.kcal)
        assertEquals(160, g.protein)
        assertEquals(69, g.fat)
        assertMacrosAddUp(g)
    }

    @Test
    fun maintainAndGain() {
        val maintain = coachGoals(2500, profile.copy(goal = "maintain"), CoachSettings(), 80.0)
        assertEquals(2500, maintain.kcal)
        val gain = coachGoals(2500, profile.copy(goal = "gain"), CoachSettings(weeklyRatePct = 0.25), 80.0)
        assertEquals(2720, gain.kcal)
    }

    @Test
    fun dietStylesSplitTheRest() {
        for (style in DietStyle.all) {
            val g = coachGoals(2400, profile, CoachSettings(dietStyle = style), 80.0)
            assertMacrosAddUp(g)
            if (style == DietStyle.KETO) assertEquals(30, g.carbs)
        }
        val lowFat = coachGoals(2400, profile, CoachSettings(dietStyle = DietStyle.LOW_FAT), 80.0)
        val balanced = coachGoals(2400, profile, CoachSettings(dietStyle = DietStyle.BALANCED), 80.0)
        assertTrue(lowFat.fat < balanced.fat && lowFat.carbs > balanced.carbs)
    }

    @Test
    fun calorieFloorAndProteinCap() {
        val small = BodyProfile(sex = "female", age = 40, heightCm = 160.0, weightKg = 55.0, goal = "lose")
        assertEquals(1200, coachGoals(1300, small, CoachSettings(weeklyRatePct = 1.0), 55.0).kcal)
        // 130 kg at 180 cm: protein follows the BMI-27 weight (87.5 kg), not 130 kg.
        val heavy = coachGoals(3200, profile.copy(weightKg = 130.0), CoachSettings(proteinPerKg = 2.0), 130.0)
        assertEquals(175, heavy.protein)
    }

    @Test
    fun checkInSchedule() {
        val s = CoachSettings(enabled = true, checkInDay = Calendar.MONDAY)
        assertEquals("2026-10-26", lastWeekdayOnOrBefore(today, Calendar.MONDAY))
        assertEquals(today, lastWeekdayOnOrBefore(today, Calendar.FRIDAY))
        assertTrue(isCheckInDue(s, null, today))
        assertTrue(isCheckInDue(s, "2026-10-19", today)) // missed last Monday
        assertFalse(isCheckInDue(s, "2026-10-26", today))
        assertFalse(isCheckInDue(s, "2026-10-28", today))
        assertFalse(isCheckInDue(s.copy(enabled = false), null, today))
        assertEquals("2026-11-02", nextCheckInDate(s, "2026-10-28", today))
        assertEquals(today, nextCheckInDate(s, "2026-10-19", today))
    }

    @Test
    fun checkInReportsWeeklyTrendChange() {
        val weights = (30 downTo 0).map { WeightEntry(shiftDate(today, -it), 80.0 - 0.05 * (30 - it)) }
        val p = buildCheckIn(profile, CoachSettings(enabled = true), daysBack(30).associateWith { 2300.0 }, weights, today)
        assertTrue(p.weeklyChangeKg!! < 0)
        assertEquals(p.expenditure.kcal, p.toCheckIn().expenditure)
        assertTrue(p.goals.kcal < p.expenditure.kcal)
    }

    private fun assertMacrosAddUp(g: NutritionGoals) {
        val implied = g.protein * 4 + g.carbs * 4 + g.fat * 9
        assertTrue("macros give $implied kcal for ${g.kcal}", abs(implied - g.kcal) <= 12)
    }
}
