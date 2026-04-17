package com.routetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.routetracker.app.ui.BluetoothSettingsScreen
import com.routetracker.app.ui.MapScreen
import com.routetracker.app.ui.RouteListScreen
import com.routetracker.app.ui.theme.Rejestrator_TrasTheme

/**
 * Main activity for the Route Tracker app.
 * Single-Activity architecture with Jetpack Compose navigation.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Rejestrator_TrasTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RouteTrackerApp()
                }
            }
        }
    }
}

/**
 * Main composable that sets up navigation and the ViewModel.
 */
@Composable
fun RouteTrackerApp() {
    val navController = rememberNavController()
    val viewModel: RouteViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "map"
    ) {
        composable("map") {
            MapScreen(
                viewModel = viewModel,
                onNavigateToRouteList = {
                    navController.navigate("routes")
                },
                onNavigateToBluetoothSettings = {
                    navController.navigate("bluetooth_settings")
                }
            )
        }

        composable("routes") {
            val selectedRouteId by viewModel.selectedRouteId.collectAsState()

            RouteListScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onShowRouteOnMap = { routeId ->
                    viewModel.selectRoute(routeId)
                    navController.popBackStack()
                }
            )
        }

        composable("bluetooth_settings") {
            BluetoothSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
