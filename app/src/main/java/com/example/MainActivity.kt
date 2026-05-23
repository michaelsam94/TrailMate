package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.SurfaceType
import com.example.presentation.Screen
import com.example.presentation.activerun.ActiveRunScreen
import com.example.presentation.activerun.rememberActiveRunViewModel
import com.example.presentation.history.HistoryScreen
import com.example.presentation.history.rememberHistoryViewModel
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.rememberHomeViewModel
import com.example.presentation.planroute.PlanRouteScreen
import com.example.presentation.planroute.rememberPlanRouteViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.rememberSettingsViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TrailMateApplication

        // Request location permissions on startup
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Home.route) {
                        val homeViewModel = rememberHomeViewModel(app)
                        HomeScreen(
                            viewModel = homeViewModel,
                            onPlanRoute = { distance, surface ->
                                navController.navigate(
                                    Screen.PlanRoute.createRoute(distance, surface.name)
                                )
                            },
                            onNavigateToHistory = {
                                navController.navigate(Screen.History.route)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }

                    composable(
                        route = Screen.PlanRoute.route,
                        arguments = listOf(
                            navArgument("distanceKm") { type = NavType.FloatType },
                            navArgument("surfaceType") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val distanceKm = backStackEntry.arguments?.getFloat("distanceKm")?.toDouble() ?: 5.0
                        val surfaceStr = backStackEntry.arguments?.getString("surfaceType") ?: "MIXED"
                        val surfaceType = try { SurfaceType.valueOf(surfaceStr) } catch (e: Exception) { SurfaceType.MIXED }

                        val planRouteViewModel = rememberPlanRouteViewModel(app)
                        PlanRouteScreen(
                            viewModel = planRouteViewModel,
                            requestedDistance = distanceKm,
                            requestedSurface = surfaceType,
                            onBack = { navController.popBackStack() },
                            onStartRoute = { routeId ->
                                navController.navigate(Screen.ActiveRun.createRoute(routeId))
                            }
                        )
                    }

                    composable(
                        route = Screen.ActiveRun.route,
                        arguments = listOf(
                            navArgument("routeId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                        val activeViewModel = rememberActiveRunViewModel(app, routeId)

                        ActiveRunScreen(
                            viewModel = activeViewModel,
                            onCompleteSession = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.History.route) {
                        val historyViewModel = rememberHistoryViewModel(app)
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        val settingsViewModel = rememberSettingsViewModel(app)
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

