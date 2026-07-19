package com.destinationcompass.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.baidu.location.LocationClient
import com.destinationcompass.app.presentation.DestinationCompassApp
import com.destinationcompass.app.presentation.MainViewModel
import com.destinationcompass.app.presentation.theme.DestinationCompassTheme
import com.destinationcompass.app.model.ThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocationClient.setAgreePrivacy(true)
        // Register before the first composition so the initial sensor sample can
        // arrive while the cold-start UI is being built.
        viewModel.startCompass()
        setContent {
            val theme by viewModel.themeMode.collectAsState()
            val darkSystemBars = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                        detectDarkMode = { darkSystemBars }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                        detectDarkMode = { darkSystemBars }
                    )
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // ColorOS otherwise adds an opaque contrast panel behind its
                    // gesture handle even when the navigation bar is transparent.
                    window.isNavigationBarContrastEnforced = false
                }
            }
            DestinationCompassTheme(theme) {
                DestinationCompassApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startCompass()
    }

    override fun onPause() {
        viewModel.stopCompass()
        super.onPause()
    }
}
