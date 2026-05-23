package com.michael.walkplanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.presentation.Screen
import com.michael.walkplanner.presentation.activerun.ActiveRunScreen
import com.michael.walkplanner.presentation.activerun.rememberActiveRunViewModel
import com.michael.walkplanner.presentation.history.HistoryScreen
import com.michael.walkplanner.presentation.history.rememberHistoryViewModel
import com.michael.walkplanner.presentation.home.HomeScreen
import com.michael.walkplanner.presentation.home.rememberHomeViewModel
import com.michael.walkplanner.presentation.planroute.PlanRouteScreen
import com.michael.walkplanner.presentation.planroute.rememberPlanRouteViewModel
import com.michael.walkplanner.presentation.settings.SettingsScreen
import com.michael.walkplanner.presentation.settings.rememberSettingsViewModel
import com.michael.walkplanner.ui.theme.WalkPlannerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WalkPlannerApplication

        requestRuntimePermissions()

        setContent {
            WalkPlannerTheme {
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

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}

