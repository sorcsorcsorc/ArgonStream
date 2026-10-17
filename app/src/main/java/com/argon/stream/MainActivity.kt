package com.argon.stream

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.data.local.UserPreferencesRepository
import com.argon.stream.domain.model.ArgonPreferences
import com.argon.stream.ui.navigation.ArgonNavHost
import com.argon.stream.ui.theme.ArgonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = ArgonPreferences()
            )
            val darkTheme = prefs.darkTheme

            // Keep status/navigation bar icons readable for the app theme, not the system theme.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            val windowSizeClass = calculateWindowSizeClass(this)
            ArgonTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs.dynamicColors
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ArgonNavHost(windowSizeClass = windowSizeClass)
                }
            }
        }
    }
}
