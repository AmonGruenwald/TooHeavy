package com.tooheavy.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.tooheavy.app.R

/**
 * Foreground service for the whole recording: keeps GPS running with the screen off or the app in the background
 * and hands each fix to RunTracker. Its notification shows distance, moving time and pace, also on the lock screen,
 * with Pause / Resume. During a manual pause it stays (for the notification) but GPS is off. Uses the platform
 * LocationManager (no Google Play services needed). RunTracker starts it again after every change, which makes it
 * follow the run's state; it stops once there is no run.
 */
class RunTrackingService : Service(), LocationListener {
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        // The first version used a low-importance channel, which some phones hide on the lock screen.
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Run recording", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Distance, time and pace while a run is recorded, also on the lock screen"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // No location permission, or not allowed to start from the background: nothing to record with.
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            // From the notification, e.g. on the lock screen; RunTracker starts this again, which syncs GPS.
            ACTION_PAUSE -> RunTracker.pause()
            ACTION_RESUME -> RunTracker.resume()
            else -> syncGps()
        }
        return START_NOT_STICKY
    }

    /** GPS on while recording (or auto-paused, to notice moving again), off during a manual pause. */
    private fun syncGps() {
        val t = RunTracker.track
        if (t == null) {
            stopSelf()
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        runCatching { lm.removeUpdates(this) }
        if (t.paused && !t.autoPaused) return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: IllegalArgumentException) {
            // The device has no GPS.
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        val changed = RunTracker.onLocation(
            location.latitude,
            location.longitude,
            if (location.hasAccuracy()) location.accuracy else null,
            if (location.hasSpeed()) location.speed else null,
        )
        val now = System.currentTimeMillis()
        if (changed || now - lastNotify >= 3_000) {
            lastNotify = now
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
        }
    }

    // Abstract in the LocationListener of Android 10 and older, so they must exist for those phones.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        super.onDestroy()
    }

    /**
     * The recording notification, readable on the lock screen: distance and average pace in the title, the
     * moving time as a running clock, the current pace (or why it's paused) below, and Pause / Resume.
     */
    private fun notification(): Notification {
        val t = RunTracker.track
        val now = System.currentTimeMillis()
        val unit = distanceUnit(Repo.settings.unit)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val open = launch?.let { PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val distance = t?.let { formatDistance(it.distanceKm, unit) }.orEmpty()
        val moving = t?.let { formatClock(it.movingMs(now)) }.orEmpty()
        val title = when {
            t == null -> "Starting run"
            t.autoPaused -> "Auto-paused · $distance"
            t.paused -> "Paused · $distance"
            else -> "$distance · avg ${paceText(t.paceSecPerKm(now), unit)} /$unit"
        }
        val text = when {
            t == null -> "Starting GPS"
            t.autoPaused -> "$moving · carries on when you move"
            t.paused -> "$moving · tap Resume to carry on"
            t.lastAccuracy == null -> "Waiting for GPS"
            else -> "Now ${paceText(t.currentPaceSecPerKm(), unit)} /$unit"
        }
        val manualPause = t != null && t.paused && !t.autoPaused
        val toggle = PendingIntent.getForegroundService(
            this, 3,
            Intent(this, RunTrackingService::class.java).setAction(if (manualPause) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_run),
            if (manualPause) "Resume" else "Pause",
            toggle,
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(title)
            .setContentText(text)
            // The system counts the moving time up by itself while moving.
            .setUsesChronometer(t?.paused != true)
            .setShowWhen(t?.paused != true)
            .setWhen(now - (t?.movingMs(now) ?: 0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Shown in full on the lock screen, even with "hide sensitive content" on.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                if (t != null) addAction(toggleAction)
                if (open != null) setContentIntent(open)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setCategory(Notification.CATEGORY_WORKOUT)
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                } else {
                    setCategory(Notification.CATEGORY_PROGRESS)
                }
            }
            .build()
    }

    companion object {
        private const val OLD_CHANNEL_ID = "run_recording"
        private const val CHANNEL_ID = "run_live"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_PAUSE = "com.tooheavy.app.action.RUN_PAUSE"
        private const val ACTION_RESUME = "com.tooheavy.app.action.RUN_RESUME"
    }
}
