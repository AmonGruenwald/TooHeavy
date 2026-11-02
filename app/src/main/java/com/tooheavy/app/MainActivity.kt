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
        setContent {
            // While a run is recorded, waking the phone shows the run instead of the lock screen (unlocking is
            // still needed to leave the app). Off again once the run is finished or discarded.
            val overLockScreen = RunTracker.track != null && Repo.settings.runOverLockScreen
            LaunchedEffect(overLockScreen) { showOverLockScreen(overLockScreen) }
            TooHeavyTheme { AppRoot() }
        }
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
