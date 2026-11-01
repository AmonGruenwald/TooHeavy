package com.tooheavy.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.tooheavy.app.R

/**
 * Habit reminders. One alarm is set for the next reminder of any habit (nextReminderAt); when it goes off, every
 * habit still open with a reminder in the last two hours gets a notification with a "Done" button
 * (remindersDue), and the next alarm is set. Inexact alarms, so no exact-alarm permission is needed; they can
 * come a few minutes late. Alarms are set again after a reboot, an app update or a clock change.
 */
object HabitReminders {
    const val ACTION_FIRE = "com.tooheavy.app.action.HABIT_REMINDER"
    const val ACTION_DONE = "com.tooheavy.app.action.HABIT_DONE"
    const val EXTRA_HABIT = "habit"
    private const val CHANNEL_ID = "habits"
    private const val PREFS = "habit_reminders"
    private const val KEY_SHOWN = "shown"

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets the alarm for the next reminder, or cancels it when no habit has one. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        val next = nextReminderAt(HabitRepo.habits.toList(), System.currentTimeMillis())
        if (next == null) am.cancel(pi) else runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
    }

    /** The alarm went off: show the reminders due now (each once a day), then set the next alarm. */
    fun fire(context: Context) {
        val now = System.currentTimeMillis()
        val day = today()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Keys "habitId|date|HH:MM" of reminders already shown; only today's are kept.
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty().filter { it.contains("|$day|") }.toMutableSet()
        for ((habit, reminder) in remindersDue(HabitRepo.habits.toList(), HabitRepo::log, now)) {
            val key = "${habit.id}|$day|${reminder.label}"
            if (key in shown) continue
            shown.add(key)
            notify(context, habit)
        }
        prefs.edit().putStringSet(KEY_SHOWN, shown).apply()
        schedule(context)
    }

    /** The notification for [habit]: its name, today's progress and "Done". */
    fun notify(context: Context, habit: Habit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val log = HabitRepo.log(habit.id)
        val day = today()
        val text = when {
            habit.counted -> "${habit.countOn(log, day)} of ${habit.target}${if (habit.unit.isBlank()) "" else " ${habit.unit}"} today"
            habit.frequency == HabitFrequency.WEEKLY -> habit.weekProgress(log, day).let { (d, g) -> "$d of $g this week" }
            else -> "Not done yet today"
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val done = PendingIntent.getBroadcast(
            context, notificationId(habit.id),
            Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val doneLabel = if (habit.counted) "+1" else "Done"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_habit)
            .setContentTitle(habit.name)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_habit), doneLabel, done).build())
            .apply { if (open != null) setContentIntent(open) }
            .build()
        runCatching { manager.notify(notificationId(habit.id), notification) }
    }

    fun cancelNotification(context: Context, habitId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(habitId))
    }

    /** Stable per habit, clear of the app's other notification ids (1001 updates, 4201 run recording). */
    private fun notificationId(habitId: String): Int = 10_000 + (habitId.hashCode() and 0xFFFFF)
}

/** Habit reminder alarms, the "Done" button, and setting the alarm again after reboots, updates and clock changes. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HabitRepo.init(context.applicationContext)
        when (intent.action) {
            HabitReminders.ACTION_FIRE -> HabitReminders.fire(context)
            HabitReminders.ACTION_DONE -> {
                val id = intent.getStringExtra(HabitReminders.EXTRA_HABIT) ?: return
                val habit = HabitRepo.habit(id) ?: return
                HabitRepo.increment(id)
                // A counted habit not there yet keeps its notification, with the new count.
                if (habit.counted && !habit.doneOn(HabitRepo.log(id), today())) HabitReminders.notify(context, habit)
                else HabitReminders.cancelNotification(context, id)
            }
            else -> HabitReminders.schedule(context)
        }
    }
}
