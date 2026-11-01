package com.tooheavy.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

// General habit tracking: habits with a daily goal and a frequency, a log of how often each was done per day,
// and the statistics (streaks, completion rates, weekly completion). The maths are pure functions of a habit
// and its log, so they can be unit tested; HabitRepo stores everything in filesDir/habits.json.

object HabitFrequency {
    /** Every day. */
    const val DAILY = "daily"
    /** On chosen weekdays (Habit.days). */
    const val DAYS = "days"
    /** A number of days a week, any days (Habit.timesPerWeek). */
    const val WEEKLY = "weekly"
}

/** A reminder at [hour]:[minute] on the days the habit is due. */
data class HabitReminder(val hour: Int = 9, val minute: Int = 0) {
    val label: String get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
}

data class Habit(
    val id: String = newId(),
    val name: String = "",
    /** How many times a day make the day done; 1 for a simple yes/no habit. */
    val target: Int = 1,
    /** What one time is called for a counted habit, e.g. "glasses"; may be empty. */
    val unit: String = "",
    val frequency: String = HabitFrequency.DAILY,
    /** Weekdays 1 (Monday) to 7 (Sunday) for HabitFrequency.DAYS. */
    val days: List<Int> = (1..7).toList(),
    /** Days a week to reach for HabitFrequency.WEEKLY. */
    val timesPerWeek: Int = 3,
    val reminders: List<HabitReminder> = emptyList(),
    /** Index into the habit colours. */
    val color: Int = 0,
    /** "yyyy-MM-dd" the habit was added; days before it don't count against it. */
    val createdDate: String = "",
    /** Archived habits are kept with their history but hidden from today and get no reminders. */
    val archived: Boolean = false,
) {
    val counted: Boolean get() = target > 1
    /** Streaks of a weekly habit count weeks, the others days. */
    val streakUnit: String get() = if (frequency == HabitFrequency.WEEKLY) "week" else "day"
}

/** Everything persisted to filesDir/habits.json: the habits and, per habit id, the count logged per date. */
data class HabitData(
    val habits: List<Habit> = emptyList(),
    val logs: Map<String, Map<String, Int>> = emptyMap(),
)

// ---------- schedule ----------

/** Weekday of a "yyyy-MM-dd" date, 1 (Monday) to 7 (Sunday). */
fun isoWeekday(date: String): Int = (dayOfWeek(date) + 5) % 7 + 1

/** Whether [date] is a day the habit is meant for. A weekly habit can be done any day. */
fun Habit.isDueOn(date: String): Boolean = when (frequency) {
    HabitFrequency.DAYS -> isoWeekday(date) in days
    else -> true
}

/** "Every day", "Mon, Wed, Fri", "3 times a week". */
fun Habit.frequencyText(): String = when (frequency) {
    HabitFrequency.DAYS -> if (days.size == 7) "Every day" else days.sorted().joinToString(", ") { weekdayShort(it) }
    HabitFrequency.WEEKLY -> if (timesPerWeek == 1) "Once a week" else "$timesPerWeek times a week"
    else -> "Every day"
}

// ---------- progress ----------

fun Habit.countOn(log: Map<String, Int>, date: String): Int = log[date] ?: 0

fun Habit.doneOn(log: Map<String, Int>, date: String): Boolean = countOn(log, date) >= target

/** Days done in the Monday-to-Sunday week starting [monday], up to [until]. */
private fun Habit.daysDoneInWeek(log: Map<String, Int>, monday: String, until: String): Int =
    (0..6).map { shiftDate(monday, it) }.count { it <= until && doneOn(log, it) }

/** Whether a weekly habit reached its days in the week starting [monday]. */
private fun Habit.weekMet(log: Map<String, Int>, monday: String, until: String): Boolean =
    daysDoneInWeek(log, monday, until) >= timesPerWeek

/** The first day that counts: the day it was added (or the first log, if that's earlier, e.g. imported). */
private fun Habit.firstDay(log: Map<String, Int>): String =
    listOfNotNull(createdDate.takeIf { it.isNotEmpty() }, log.keys.minOrNull()).minOrNull() ?: today()

/** Done this week so far and the week's goal, for a weekly habit's "2 of 3 this week". */
fun Habit.weekProgress(log: Map<String, Int>, today: String): Pair<Int, Int> =
    daysDoneInWeek(log, mondayOf(today), today) to timesPerWeek

/**
 * The current streak in days done in a row (weeks for a weekly habit), skipping days it isn't due. Today (or this
 * week) only counts once done, so an unfinished today doesn't break the streak.
 */
fun Habit.currentStreak(log: Map<String, Int>, today: String): Int {
    val first = firstDay(log)
    var streak = 0
    if (frequency == HabitFrequency.WEEKLY) {
        var monday = mondayOf(today)
        if (weekMet(log, monday, today)) streak++
        monday = shiftDate(monday, -7)
        while (monday >= mondayOf(first) && weekMet(log, monday, today)) {
            streak++
            monday = shiftDate(monday, -7)
        }
        return streak
    }
    var d = today
    if (isDueOn(d) && doneOn(log, d)) streak++
    d = shiftDate(d, -1)
    while (d >= first) {
        if (isDueOn(d)) {
            if (doneOn(log, d)) streak++ else break
        }
        d = shiftDate(d, -1)
    }
    return streak
}

/** The longest streak ever, in the same units as currentStreak. */
fun Habit.bestStreak(log: Map<String, Int>, today: String): Int {
    val first = firstDay(log)
    var best = 0
    var run = 0
    if (frequency == HabitFrequency.WEEKLY) {
        var monday = mondayOf(first)
        val thisWeek = mondayOf(today)
        while (monday <= thisWeek) {
            if (weekMet(log, monday, today)) {
                run++
                best = maxOf(best, run)
            } else if (monday < thisWeek) {
                run = 0
            }
            monday = shiftDate(monday, 7)
        }
        return best
    }
    var d = first
    while (d <= today) {
        if (isDueOn(d)) {
            if (doneOn(log, d)) {
                run++
                best = maxOf(best, run)
            } else if (d < today) {
                run = 0
            }
        }
        d = shiftDate(d, 1)
    }
    return best
}

/**
 * Share (0..1) of the due days done over the [days] days up to [today] (weeks met, for a weekly habit), counting
 * only from when the habit was added. Today or this week only counts once done. Null when nothing is due yet.
 */
fun Habit.completionRate(log: Map<String, Int>, today: String, days: Int = 30): Double? {
    val from = maxOf(shiftDate(today, -(days - 1)), firstDay(log))
    if (frequency == HabitFrequency.WEEKLY) {
        var monday = mondayOf(from)
        val thisWeek = mondayOf(today)
        var met = 0
        var weeks = 0
        while (monday <= thisWeek) {
            val ok = weekMet(log, monday, today)
            if (monday < thisWeek || ok) {
                weeks++
                if (ok) met++
            }
            monday = shiftDate(monday, 7)
        }
        return if (weeks == 0) null else met.toDouble() / weeks
    }
    var due = 0
    var done = 0
    var d = from
    while (d <= today) {
        if (isDueOn(d)) {
            val ok = doneOn(log, d)
            if (d < today || ok) {
                due++
                if (ok) done++
            }
        }
        d = shiftDate(d, 1)
    }
    return if (due == 0) null else done.toDouble() / due
}

/**
 * Completion (0..1) of each of the last [weeks] Monday-to-Sunday weeks, oldest first: due days done out of the
 * week's due days so far, or for a weekly habit days done out of its goal. Weeks before the habit are 0.
 */
fun Habit.weeklyCompletion(log: Map<String, Int>, today: String, weeks: Int = 12): List<Double> {
    val thisWeek = mondayOf(today)
    val first = firstDay(log)
    return (weeks - 1 downTo 0).map { back ->
        val monday = shiftDate(thisWeek, -7 * back)
        val dates = (0..6).map { shiftDate(monday, it) }.filter { it <= today && it >= first }
        when {
            dates.isEmpty() -> 0.0
            frequency == HabitFrequency.WEEKLY -> min(1.0, dates.count { doneOn(log, it) }.toDouble() / timesPerWeek)
            else -> {
                val due = dates.filter { isDueOn(it) }
                if (due.isEmpty()) 0.0 else due.count { doneOn(log, it) }.toDouble() / due.size
            }
        }
    }
}

/** Days the habit was done, all-time. */
fun Habit.daysDone(log: Map<String, Int>): Int = log.keys.count { doneOn(log, it) }

/** Whether the habit still needs doing on [today]: due, not done, and for a weekly habit, the week not met yet. */
fun Habit.openOn(log: Map<String, Int>, today: String): Boolean =
    !archived && isDueOn(today) && !doneOn(log, today) &&
        (frequency != HabitFrequency.WEEKLY || !weekMet(log, mondayOf(today), today))

// ---------- reminders ----------

/** Epoch ms of the next reminder of any active habit after [now], looking a week ahead; null without reminders. */
fun nextReminderAt(habits: List<Habit>, now: Long): Long? {
    val day = dateOf(now)
    var best: Long? = null
    for (h in habits) {
        if (h.archived) continue
        for (r in h.reminders) {
            for (d in 0..7) {
                val date = shiftDate(day, d)
                if (!h.isDueOn(date)) continue
                val t = timeOn(date, r.hour, r.minute)
                if (t > now) {
                    if (best == null || t < best) best = t
                    break
                }
            }
        }
    }
    return best
}

/**
 * Reminders to show at [now]: today's reminders of habits still open (see openOn) whose time was within the last
 * [windowMs], so a reminder delayed by the phone sleeping still comes, but not hours late.
 */
fun remindersDue(habits: List<Habit>, logOf: (String) -> Map<String, Int>, now: Long, windowMs: Long = 2 * 3_600_000L): List<Pair<Habit, HabitReminder>> {
    val day = dateOf(now)
    return habits.flatMap { h ->
        if (!h.openOn(logOf(h.id), day)) return@flatMap emptyList()
        h.reminders.filter { r ->
            val t = timeOn(day, r.hour, r.minute)
            t <= now && now - t <= windowMs
        }.map { h to it }
    }
}

// ---------- store ----------

/** Habit store. Compose-observable; every change persists to filesDir/habits.json and reschedules reminders. Main thread only. */
object HabitRepo {
    private val gson = Gson()
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private var appContext: Context? = null

    /** All habits, archived ones included, in the user's order. */
    val habits = mutableStateListOf<Habit>()
    /** Per habit id: count per date. */
    private val logs = mutableStateMapOf<String, Map<String, Int>>()

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        val f = File(context.filesDir, "habits.json")
        file = f
        if (f.exists()) {
            runCatching { gson.fromJson(f.readText(), HabitData::class.java) }.getOrNull()?.let { d ->
                habits.addAll(d.habits.orEmpty())
                d.logs.orEmpty().forEach { (id, log) -> logs[id] = log.orEmpty() }
            }
        }
    }

    private fun save(reschedule: Boolean) {
        val f = file ?: return
        val json = gson.toJson(HabitData(habits.toList(), logs.toMap()))
        io.execute {
            val tmp = File(f.parentFile, "habits.json.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.delete(); tmp.renameTo(f)
            }
        }
        if (reschedule) appContext?.let { HabitReminders.schedule(it) }
    }

    fun habit(id: String): Habit? = habits.firstOrNull { it.id == id }

    fun active(): List<Habit> = habits.filter { !it.archived }

    fun log(habitId: String): Map<String, Int> = logs[habitId].orEmpty()

    fun count(habitId: String, date: String): Int = log(habitId)[date] ?: 0

    /** Adds or replaces a habit. */
    fun saveHabit(h: Habit) {
        val habit = if (h.createdDate.isEmpty()) h.copy(createdDate = today()) else h
        val i = habits.indexOfFirst { it.id == habit.id }
        if (i >= 0) habits[i] = habit else habits.add(habit)
        save(reschedule = true)
    }

    fun deleteHabit(id: String) {
        habits.removeAll { it.id == id }
        logs.remove(id)
        appContext?.let { HabitReminders.cancelNotification(it, id) }
        save(reschedule = true)
    }

    fun move(id: String, delta: Int) {
        val i = habits.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in habits.indices) return
        val h = habits[i]
        habits[i] = habits[j]
        habits[j] = h
        save(reschedule = false)
    }

    /** Sets how often the habit was done on [date] (0 removes the day). */
    fun setCount(habitId: String, date: String, count: Int) {
        val log = log(habitId).toMutableMap()
        if (count <= 0) log.remove(date) else log[date] = count
        logs[habitId] = log
        // Done for today: its reminder notification can go.
        val h = habit(habitId)
        if (h != null && date == today() && h.doneOn(log, date)) appContext?.let { HabitReminders.cancelNotification(it, habitId) }
        save(reschedule = false)
    }

    /** One more time today (or on [date]). */
    fun increment(habitId: String, date: String = today()) = setCount(habitId, date, count(habitId, date) + 1)

    /** For a yes/no tap: done if it wasn't, not done if it was. A counted habit goes up by one, back to 0 past its goal. */
    fun toggle(habitId: String, date: String) {
        val h = habit(habitId) ?: return
        val c = count(habitId, date)
        setCount(habitId, date, if (c >= h.target) 0 else c + 1)
    }
}
