package com.senzaproject.createapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.senzaproject.createapp.di.AppViewModelFactory
import com.senzaproject.createapp.ui.CreateAppRoot
import com.senzaproject.createapp.ui.theme.CreateAppTheme
import com.senzaproject.createapp.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tema gelap menjadi default sampai pengaturan tema tersedia (Tahap 13).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val container = (application as CreateAppApplication).container
        val viewModelFactory = AppViewModelFactory(container)

        setContent {
            CreateAppTheme(themeMode = ThemeMode.DARK) {
                CreateAppRoot(viewModelFactory = viewModelFactory)
            }
        }
    }
}
