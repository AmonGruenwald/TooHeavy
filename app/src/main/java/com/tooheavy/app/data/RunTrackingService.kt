package com.tooheavy.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
 * Foreground service that keeps GPS running while a run is recorded, with the screen off or the app in the
 * background, and hands each fix to RunTracker. Its notification shows the moving time and distance. Uses the
 * platform LocationManager (no Google Play services needed). Runs only while RunTracker isn't paused.
 */
class RunTrackingService : Service(), LocationListener {
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Run recording", NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // No location permission, or not allowed to start from the background: nothing to record with.
            stopSelf()
            return START_NOT_STICKY
        }
        val lm = getSystemService(LocationManager::class.java)
        try {
            lm.removeUpdates(this)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        } catch (e: IllegalArgumentException) {
            // The device has no GPS.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        RunTracker.onLocation(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null)
        val now = System.currentTimeMillis()
        if (now - lastNotify >= 5_000) {
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

    private fun notification(): Notification {
        val t = RunTracker.track
        val now = System.currentTimeMillis()
        val unit = distanceUnit(Repo.settings.unit)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val open = launch?.let { PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("Recording run")
            .setContentText(
                when {
                    t == null -> "Starting GPS"
                    t.lastAccuracy == null -> "Waiting for GPS"
                    else -> formatDistance(t.distanceKm, unit)
                },
            )
            // The system counts the moving time up by itself.
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(now - (t?.movingMs(now) ?: 0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (open != null) setContentIntent(open) }
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "run_recording"
        private const val NOTIFICATION_ID = 4201
    }
}
