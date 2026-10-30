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
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Repo
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
        Updater.check(applicationContext)
        Updater.scheduleDailyCheck(applicationContext)
        WhatsNew.onAppStart(applicationContext)
        askForNotificationsOnce()
        setContent { TooHeavyTheme { AppRoot() } }
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
