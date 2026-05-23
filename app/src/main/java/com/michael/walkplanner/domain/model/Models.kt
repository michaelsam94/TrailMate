package com.michael.walkplanner.domain.model

enum class SurfaceType { ROAD, TRAIL, PARK, MIXED }

data class Waypoint(
    val lat: Double,
    val lng: Double,
    val elevationM: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class Route(
    val id: String,
    val waypoints: List<Waypoint>,
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    val safetyScore: Float,           // 0.0 - 1.0
    val surfaceType: SurfaceType,
    val elevationGainM: Double,
    val createdAt: Long = System.currentTimeMillis()
)

data class ActiveSession(
    val sessionId: String,
    val routeId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val liveWaypoints: List<Waypoint>,
    val currentPaceMinPerKm: Double,
    val distanceCoveredKm: Double,
    val elevationGainM: Double = 0.0,
    val caloriesBurned: Int = 0,
    val isPaused: Boolean = false
)

data class UserPrefs(
    val preferredSurface: SurfaceType,
    val avoidHighways: Boolean,
    val maxElevationGainM: Double,
    val useImperialUnits: Boolean
)
