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
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.tooheavy.app.R

/**
 * Tells you when a rest is over while you're out of the app (in the app the rest bar vibrates). An exact alarm
 * goes off at the end of the rest, so it comes on time even with the phone locked or the app closed; while the
 * app is running, a timer does the same and whichever comes first shows the notification. Skipping the rest or
 * ending the workout calls it off; changing the rest moves it.
 */
object RestAlarm {
    const val ACTION_REST_OVER = "com.tooheavy.app.action.REST_OVER"
    private const val EXTRA_ENDS_AT = "endsAt"
    private const val CHANNEL_ID = "rest"
    /** Clear of the app's other notification ids (1001 updates, 4201 run recording, 10000+ habits). */
    private const val NOTIFICATION_ID = 4301

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** End of the rest a notification was last shown for, so the alarm and the timer don't both show one. */
    @Volatile
    private var notifiedFor = 0L

    private fun alarmIntent(context: Context, endsAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_REST_OVER).putExtra(EXTRA_ENDS_AT, endsAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets (or moves) the alarm for a rest ending at [endsAt]. */
    fun schedule(context: Context, endsAt: Long) {
        val app = context.applicationContext
        cancelNotification(app)
        app.getSystemService(AlarmManager::class.java)?.let { am ->
            val pi = alarmIntent(app, endsAt)
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
                } else {
                    // Without exact alarms this can come late; the timer below covers the app still running.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
                }
            }
        }
        pending?.let(handler::removeCallbacks)
        pending = Runnable { restOver(app, endsAt) }.also {
            handler.postDelayed(it, (endsAt - System.currentTimeMillis()).coerceAtLeast(0))
        }
    }

    /** Calls the alarm off (rest skipped, workout finished or discarded). */
    fun cancel(context: Context) {
        val app = context.applicationContext
        pending?.let(handler::removeCallbacks)
        pending = null
        app.getSystemService(AlarmManager::class.java)?.let { am -> runCatching { am.cancel(alarmIntent(app, 0)) } }
        cancelNotification(app)
    }

    /** Clears the notification, e.g. once you're back in the app. */
    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** The rest ending at [endsAt] is over: notify, unless the app is on screen or this rest was already notified. */
    fun restOver(context: Context, endsAt: Long) {
        if (Updater.appVisible || endsAt == notifiedFor) return
        notifiedFor = endsAt
        notify(context)
    }

    private fun notify(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // High importance: it pops up, sounds and vibrates like a timer.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a rest between sets is over and you're not in the app"
                enableVibration(true)
            },
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 3, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Rest is over")
            .setContentText(nextSetText() ?: "Time for your next set.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // Stale once the next set is long done.
            .setTimeoutAfter(15 * 60_000L)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** "Next: Bench Press, set 3 of 4", from the workout going on; null without one or with every set done. */
    private fun nextSetText(): String? {
        val workout = Repo.activeWorkout ?: return null
        for (we in workout.exercises) {
            val i = we.sets.indexOfFirst { !it.completed }
            if (i < 0) continue
            val name = Repo.exercise(we.exerciseId)?.name ?: return null
            return "Next: $name, set ${i + 1} of ${we.sets.size}"
        }
        return null
    }
}

/** The rest alarm going off. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestAlarm.ACTION_REST_OVER) return
        Repo.init(context.applicationContext)
        RestAlarm.restOver(context.applicationContext, intent.getLongExtra("endsAt", 0L))
    }
}
