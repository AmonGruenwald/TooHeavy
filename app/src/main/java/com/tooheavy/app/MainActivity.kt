package com.tooheavy.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Repo
import com.tooheavy.app.ui.AppRoot
import com.tooheavy.app.ui.theme.TooHeavyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        NutritionRepo.init(applicationContext)
        setContent { TooHeavyTheme { AppRoot() } }
    }
}
