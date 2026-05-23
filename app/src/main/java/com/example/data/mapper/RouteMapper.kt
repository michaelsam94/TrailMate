package com.example.data.mapper

import com.example.data.remote.dto.OverpassElement
import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.model.Waypoint
import kotlin.math.*

object RouteMapper {

    fun mapHighwayToSurfaceType(highway: String): SurfaceType {
        return when (highway) {
            "footway", "path", "steps", "pedestrian" -> SurfaceType.TRAIL
            "cycleway" -> SurfaceType.MIXED
            "residential", "living_street", "unclassified", "service" -> SurfaceType.ROAD
            else -> SurfaceType.MIXED
        }
    }

    fun computeSafetyScore(highways: List<String>, hasParkSegment: Boolean = false): Float {
        if (highways.isEmpty()) return 0.5f
        var total = 0.0
        for (highway in highways) {
            total += when (highway) {
                "footway", "path" -> 1.0
                "cycleway" -> 0.7
                "residential", "living_street", "pedestrian" -> 0.5
                "primary", "secondary" -> -0.3
                else -> 0.4
            }
        }
        if (hasParkSegment) total += 0.2 * highways.size
        val normalized = total / highways.size
        return normalized.coerceIn(0.0, 1.0).toFloat()
    }

    fun mapNodesToWaypoints(
        nodeIds: List<Long>,
        nodesMap: Map<Long, OverpassElement>
    ): List<Waypoint> {
        return nodeIds.mapNotNull { id ->
            val node = nodesMap[id] ?: return@mapNotNull null
            val lat = node.lat ?: return@mapNotNull null
            val lon = node.lon ?: return@mapNotNull null
            Waypoint(lat = lat, lng = lon)
        }
    }

    fun calculateDistanceKm(waypoints: List<Waypoint>): Double {
        if (waypoints.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until waypoints.size - 1) {
            total += haversineKm(
                waypoints[i].lat, waypoints[i].lng,
                waypoints[i + 1].lat, waypoints[i + 1].lng
            )
        }
        return (round(total * 10.0) / 10.0)
    }

    fun buildRoute(
        id: String,
        waypoints: List<Waypoint>,
        surfaceType: SurfaceType,
        highways: List<String>,
        hasParkSegment: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ): Route {
        val distanceKm = calculateDistanceKm(waypoints)
        return Route(
            id = id,
            waypoints = waypoints,
            distanceKm = distanceKm,
            estimatedDurationMinutes = (distanceKm * 6.5).toInt().coerceAtLeast(3),
            safetyScore = computeSafetyScore(highways, hasParkSegment),
            surfaceType = surfaceType,
            elevationGainM = round((5.0 + distanceKm * 2.0) * 10.0) / 10.0,
            createdAt = createdAt
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
