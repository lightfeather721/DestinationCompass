package com.destinationcompass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.location.LocationClient
import com.destinationcompass.app.presentation.DestinationCompassApp
import com.destinationcompass.app.presentation.MainViewModel
import com.destinationcompass.app.presentation.theme.DestinationCompassTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocationClient.setAgreePrivacy(true)
        SDKInitializer.setAgreePrivacy(applicationContext, true)
        SDKInitializer.initialize(applicationContext)
        SDKInitializer.setCoordType(CoordType.GCJ02)
        setContent {
            val theme by viewModel.themeMode.collectAsState()
            DestinationCompassTheme(theme) {
                DestinationCompassApp(viewModel)
            }
        }
    }
}
