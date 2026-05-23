package com.michael.walkplanner.presentation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object PlanRoute : Screen("plan_route/{distanceKm}/{surfaceType}") {
        fun createRoute(distance: Double, surface: String) = "plan_route/$distance/$surface"
    }
    object ActiveRun : Screen("active_run/{routeId}") {
        fun createRoute(routeId: String) = "active_run/$routeId"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
}
