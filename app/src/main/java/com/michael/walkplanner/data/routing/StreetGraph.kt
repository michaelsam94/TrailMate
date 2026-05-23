package com.michael.walkplanner.data.routing

import com.michael.walkplanner.domain.model.Waypoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A single directed edge in the street graph adjacency list. */
data class GraphEdge(
    val toNodeId: Long,
    val distanceM: Double,
    val safetyWeight: Float
)

/**
 * In-memory OSM street graph using an adjacency list — O(V + E) memory, not O(V²).
 * Built during route generation and discarded afterward; never held in a ViewModel.
 */
data class StreetGraph(
    val nodes: Map<Long, Waypoint>,
    val adjacencyList: Map<Long, List<GraphEdge>>
)

internal fun normalizeEdge(u: Long, v: Long): Pair<Long, Long> =
    if (u < v) u to v else v to u

/** Haversine distance in metres — admissible A* heuristic on lat/lng coordinates. */
fun haversineDistanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * asin(sqrt(a))
}

internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    haversineDistanceM(lat1, lon1, lat2, lon2) / 1000.0

/** Lower weight = safer / more preferred for walking. Used as a path-cost multiplier. */
fun highwaySafetyWeight(highway: String): Float = when (highway) {
    "footway", "path", "steps" -> 0.8f
    "cycleway", "pedestrian" -> 0.9f
    "residential", "living_street" -> 1.0f
    "unclassified", "service" -> 1.1f
    "tertiary" -> 1.4f
    "secondary" -> 2.0f
    "primary" -> 3.0f
    else -> 1.2f
}

internal fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) -
        sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

internal fun angularSeparation(a: Double, b: Double): Double {
    val diff = kotlin.math.abs(a - b) % 360.0
    return kotlin.math.min(diff, 360.0 - diff)
}

internal fun StreetGraph.neighborIds(nodeId: Long): List<Long> =
    adjacencyList[nodeId].orEmpty().map { it.toNodeId }

internal fun StreetGraph.degreeOf(nodeId: Long): Int =
    adjacencyList[nodeId]?.size ?: 0
