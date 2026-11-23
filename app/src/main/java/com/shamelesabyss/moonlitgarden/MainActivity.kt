package com.shamelesabyss.moonlitgarden

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.shamelesabyss.moonlitgarden.domain.GardenViewModel
import com.shamelesabyss.moonlitgarden.ui.MoonlitApp
import com.shamelesabyss.moonlitgarden.ui.MoonlitTheme

class MainActivity : ComponentActivity() {
    private val gardenViewModel: GardenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(19, 13, 33)
        window.navigationBarColor = android.graphics.Color.rgb(19, 13, 33)
        setContent {
            MoonlitTheme {
                MoonlitApp(gardenViewModel)
            }
        }
    }
}
