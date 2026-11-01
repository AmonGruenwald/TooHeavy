package com.tooheavy.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Calorie carry-over. Eating over the calorie goal lowers the goals of the next days by the difference,
// eating under it raises them. Differences the other way pay off the oldest open ones first, and whatever
// is left of a difference is dropped a week after its day. Like the coach, it's a pure function of the logs.

/** Days a difference from the calorie goal is carried before what's left of it is dropped. */
const val CARRY_OVER_DAYS = 7

/** Days replayed before the day asked about; older differences could only matter through long chains of paybacks. */
private const val REPLAY_DAYS = 8 * CARRY_OVER_DAYS

/** The daily calorie goal from [date] on. Date "" stands for every day before the first recorded change. */
data class KcalGoalChange(val date: String = "", val kcal: Int = 0)

/** Calorie goal on [date] from [history] (oldest first), or [current] if no change is that old. */
fun kcalGoalAt(date: String, history: List<KcalGoalChange>, current: Int): Int =
    history.lastOrNull { it.date <= date }?.kcal ?: current

/**
 * Calories carried into [date]: positive when earlier days were eaten under their goal, negative when over.
 * [dailyKcal] is the logged intake per date (days without food logged don't count), [goalOn] the goal a day had.
 */
fun carryOverKcal(date: String, dailyKcal: Map<String, Double>, goalOn: (String) -> Int): Int {
    class Open(val day: Int, var kcal: Int)
    // Open differences, oldest first. They all point the same way: opposite ones pay each other off.
    val open = ArrayList<Open>()
    datesBefore(date, REPLAY_DAYS).forEachIndexed { day, d ->
        open.removeAll { day - it.day > CARRY_OVER_DAYS }
        val eaten = dailyKcal[d]?.takeIf { it > 0 } ?: return@forEachIndexed
        var diff = goalOn(d) - eaten.roundToInt()
        while (diff != 0 && open.isNotEmpty() && (open[0].kcal > 0) != (diff > 0)) {
            if (abs(diff) >= abs(open[0].kcal)) {
                diff += open.removeAt(0).kcal
            } else {
                open[0].kcal += diff
                diff = 0
            }
        }
        if (diff != 0) open.add(Open(day, diff))
    }
    return open.filter { REPLAY_DAYS - it.day <= CARRY_OVER_DAYS }.sumOf { it.kcal }
}

/** [goal] moved by [carried], but not lowered below [floor] (or [goal], if that is lower already). */
fun adjustedKcalGoal(goal: Int, carried: Int, floor: Int): Int = max(goal + carried, min(goal, floor))

/** The [count] dates before [date], oldest first. */
private fun datesBefore(date: String, count: Int): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val c = Calendar.getInstance()
    c.time = format.parse(date) ?: return emptyList()
    c.add(Calendar.DAY_OF_YEAR, -count)
    return List(count) { format.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }
}
