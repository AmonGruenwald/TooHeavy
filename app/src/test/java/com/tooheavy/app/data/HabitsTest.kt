package com.tooheavy.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Habit tracking (1.15.0). 2026-11-02 is a Monday. */
class HabitsTest {
    private val monday = "2026-11-02"

    private fun log(vararg dates: String, count: Int = 1): Map<String, Int> = dates.associateWith { count }

    private fun d(mmdd: String) = "2026-$mmdd"

    @Test
    fun weekdaysAndFrequencies() {
        assertEquals(1, isoWeekday(monday))
        assertEquals(7, isoWeekday(d("11-01")))
        val mwf = Habit(frequency = HabitFrequency.DAYS, days = listOf(1, 3, 5))
        assertTrue(mwf.isDueOn(monday))
        assertFalse(mwf.isDueOn(d("11-03")))
        assertEquals("Mon, Wed, Fri", mwf.frequencyText())
        assertTrue(Habit(frequency = HabitFrequency.WEEKLY).isDueOn(d("11-03")))
        assertEquals("3 times a week", Habit(frequency = HabitFrequency.WEEKLY, timesPerWeek = 3).frequencyText())
        assertEquals("Every day", Habit().frequencyText())
    }

    @Test
    fun dailyStreaks() {
        val h = Habit(createdDate = d("10-26"))
        val week = log(d("10-27"), d("10-28"), d("10-29"), d("10-30"), d("10-31"), d("11-01"))
        // Today isn't done yet: the streak still stands.
        assertEquals(6, h.currentStreak(week, monday))
        assertEquals(7, h.currentStreak(week + (monday to 1), monday))
        // A missed day breaks it.
        val gap = week - d("10-30")
        assertEquals(2, h.currentStreak(gap, monday))
        assertEquals(3, h.bestStreak(gap, monday))
        assertEquals(6, h.bestStreak(week, monday))
        assertEquals(0, h.currentStreak(emptyMap(), monday))
    }

    @Test
    fun countedHabitsAreDoneAtTheirGoal() {
        val water = Habit(target = 8, unit = "glasses", createdDate = d("11-01"))
        assertTrue(water.counted)
        assertFalse(water.doneOn(log(monday, count = 5), monday))
        assertTrue(water.doneOn(log(monday, count = 8), monday))
        assertTrue(water.doneOn(log(monday, count = 9), monday))
        assertEquals(1, water.currentStreak(log(d("11-01"), count = 8) + (monday to 3), monday))
    }

    @Test
    fun daysNotDueDontBreakAStreak() {
        val mwf = Habit(frequency = HabitFrequency.DAYS, days = listOf(1, 3, 5), createdDate = d("10-19"))
        val done = log(d("10-19"), d("10-21"), d("10-23"), d("10-26"), d("10-28"), d("10-30"))
        assertEquals(6, mwf.currentStreak(done, monday))
        assertEquals(1.0, mwf.completionRate(done, monday, 30)!!, 1e-9)
        // Missing the Wednesday: 5 of 6 due days.
        val missed = done - d("10-28")
        assertEquals(1, mwf.currentStreak(missed, monday))
        assertEquals(5.0 / 6, mwf.completionRate(missed, monday, 30)!!, 1e-9)
    }

    @Test
    fun weeklyHabitsCountWeeks() {
        val gym = Habit(frequency = HabitFrequency.WEEKLY, timesPerWeek = 3, createdDate = d("10-19"))
        val two = log(d("10-19"), d("10-21"), d("10-23"), d("10-26"), d("10-27"), d("10-29"))
        // Both past weeks met; this week (just started) doesn't count against it.
        assertEquals(2, gym.currentStreak(two, monday))
        assertEquals(2, gym.bestStreak(two, monday))
        assertEquals(0 to 3, gym.weekProgress(two, monday))
        assertEquals(1.0, gym.completionRate(two, monday, 30)!!, 1e-9)
        // Only two days the week before: the streak is just the week of 10-19 before it... broken.
        val short = two - d("10-29")
        assertEquals(0, gym.currentStreak(short, monday))
        assertEquals(1, gym.bestStreak(short, monday))
        assertEquals(0.5, gym.completionRate(short, monday, 30)!!, 1e-9)
        // This week, once met, counts.
        val thisWeek = two + log(monday, d("11-03"), d("11-04"))
        assertEquals(3, gym.currentStreak(thisWeek, d("11-04")))
        assertFalse(gym.openOn(thisWeek, d("11-05")))
        assertTrue(gym.openOn(two, monday))
        // Weekly completion: days done out of the goal, capped at 100 %.
        assertEquals(listOf(1.0, 1.0, 0.0), gym.weeklyCompletion(two, monday, 3))
    }

    @Test
    fun completionStartsWhenTheHabitWasAdded() {
        val h = Habit(createdDate = d("10-31"))
        // 10-31 and 11-01 due, both done; today not done yet doesn't count.
        assertEquals(1.0, h.completionRate(log(d("10-31"), d("11-01")), monday, 30)!!, 1e-9)
        assertEquals(0.5, h.completionRate(log(d("11-01")), monday, 30)!!, 1e-9)
        // Nothing due yet.
        assertNull(Habit(createdDate = monday).completionRate(emptyMap(), monday, 30))
        assertEquals(2, h.daysDone(log(d("10-31"), d("11-01"))))
    }

    @Test
    fun theNextReminder() {
        val daily = Habit(reminders = listOf(HabitReminder(9, 0)))
        val tenOClock = timeOn(monday, 10, 0)
        assertEquals(timeOn(d("11-03"), 9, 0), nextReminderAt(listOf(daily), tenOClock))
        assertEquals(timeOn(monday, 9, 0), nextReminderAt(listOf(daily), timeOn(monday, 8, 0)))
        // On chosen days only: Monday after 10:00 → Wednesday.
        val monWed = Habit(frequency = HabitFrequency.DAYS, days = listOf(1, 3), reminders = listOf(HabitReminder(9, 0)))
        assertEquals(timeOn(d("11-04"), 9, 0), nextReminderAt(listOf(monWed), tenOClock))
        // The earliest of several.
        val evening = Habit(reminders = listOf(HabitReminder(20, 30)))
        assertEquals(timeOn(monday, 20, 30), nextReminderAt(listOf(daily, evening), tenOClock))
        assertNull(nextReminderAt(listOf(daily.copy(archived = true)), tenOClock))
        assertNull(nextReminderAt(listOf(Habit()), tenOClock))
    }

    @Test
    fun remindersOnlyForOpenHabits() {
        val h = Habit(id = "h", reminders = listOf(HabitReminder(9, 0), HabitReminder(6, 0), HabitReminder(12, 0)))
        val now = timeOn(monday, 9, 30)
        // 9:00 is due; 6:00 is too long ago, 12:00 hasn't come.
        assertEquals(listOf(HabitReminder(9, 0)), remindersDue(listOf(h), { emptyMap() }, now).map { it.second })
        assertTrue(remindersDue(listOf(h), { log(monday) }, now).isEmpty())
        val notToday = h.copy(frequency = HabitFrequency.DAYS, days = listOf(2))
        assertTrue(remindersDue(listOf(notToday), { emptyMap() }, now).isEmpty())
    }

    @Test
    fun habitsRoundTrip() {
        val gson = Gson()
        val data = HabitData(
            habits = listOf(
                Habit(id = "a", name = "Read", frequency = HabitFrequency.DAYS, days = listOf(1, 3), reminders = listOf(HabitReminder(21, 15)), color = 2, createdDate = monday),
                Habit(id = "b", name = "Water", target = 8, unit = "glasses", frequency = HabitFrequency.WEEKLY, timesPerWeek = 5, archived = true),
            ),
            logs = mapOf("a" to mapOf(monday to 1), "b" to mapOf(monday to 6)),
        )
        assertEquals(data, gson.fromJson(gson.toJson(data), HabitData::class.java))
        assertEquals("21:15", HabitReminder(21, 15).label)
        assertTrue(gson.fromJson("{}", HabitData::class.java).habits.isEmpty())
    }
}
