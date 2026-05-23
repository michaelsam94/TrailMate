package com.example.data.routing

import com.example.domain.model.Waypoint

data class StreetGraph(
    val nodes: Map<Long, Waypoint>,
    val adjacencyList: Map<Long, Set<Long>>
)

internal fun normalizeEdge(u: Long, v: Long): Pair<Long, Long> =
    if (u < v) u to v else v to u

internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

internal fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2Rad)
    val x = kotlin.math.cos(lat1Rad) * kotlin.math.sin(lat2Rad) -
        kotlin.math.sin(lat1Rad) * kotlin.math.cos(lat2Rad) * kotlin.math.cos(dLon)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

internal fun angularSeparation(a: Double, b: Double): Double {
    val diff = kotlin.math.abs(a - b) % 360.0
    return kotlin.math.min(diff, 360.0 - diff)
}
