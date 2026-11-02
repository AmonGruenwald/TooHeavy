package com.tooheavy.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tooheavy.app.data.distanceUnit
import com.tooheavy.app.ui.running.LockRunScreen
import com.tooheavy.app.data.HabitReminders
import com.tooheavy.app.data.HabitRepo
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.RunTracker
import com.tooheavy.app.data.Updater
import com.tooheavy.app.data.WhatsNew
import com.tooheavy.app.ui.AppRoot
import com.tooheavy.app.ui.theme.TooHeavyTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Whether the phone is locked (the app can show over the lock screen during a run). */
    private var locked by mutableStateOf(false)

    /** Screen on/off and unlocking change [locked]. */
    private val keyguardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateLocked()
    }

    private fun updateLocked() {
        locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        NutritionRepo.init(applicationContext)
        RunTracker.init(applicationContext)
        HabitRepo.init(applicationContext)
        HabitReminders.schedule(applicationContext)
        Updater.check(applicationContext)
        Updater.scheduleDailyCheck(applicationContext)
        WhatsNew.onAppStart(applicationContext)
        askForNotificationsOnce()
        ContextCompat.registerReceiver(
            this, keyguardReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        updateLocked()
        setContent {
            // While a run is recorded, waking the phone shows the run instead of the lock screen: a focused view
            // with the map and the main stats, laid over the app (which stays as it was) until the phone is
            // unlocked. Off again once the run is finished or discarded.
            val track = RunTracker.track
            val overLockScreen = track != null && Repo.settings.runOverLockScreen
            LaunchedEffect(overLockScreen) { showOverLockScreen(overLockScreen) }
            TooHeavyTheme {
                Box(Modifier.fillMaxSize()) {
                    AppRoot()
                    if (overLockScreen && locked && track != null) {
                        LockRunScreen(track, distanceUnit(Repo.settings.unit)) { unlock() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocked()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateLocked()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(keyguardReceiver) }
        super.onDestroy()
    }

    /** Asks for the lock screen (PIN, fingerprint...) to be dismissed; the full app shows once unlocked. */
    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java) ?: return
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = updateLocked()
        })
    }

    /** Android 8.0 only has the window flag; 8.1 and later have setShowWhenLocked. */
    @Suppress("DEPRECATION")
    private fun showOverLockScreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(on)
        } else if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onStart() {
        super.onStart()
        Updater.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        Updater.appVisible = false
    }

    /** Update notifications need permission on Android 13+. Asked once; it can be changed in system settings. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
