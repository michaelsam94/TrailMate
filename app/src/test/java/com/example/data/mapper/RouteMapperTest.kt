package com.example.data.mapper

import com.example.domain.model.SurfaceType
import com.example.domain.model.Waypoint
import com.example.data.remote.dto.OverpassElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteMapperTest {

    @Test
    fun `maps Overpass nodes to waypoints with correct lat lng`() {
        val nodes = mapOf(
            1L to OverpassElement(type = "node", id = 1L, lat = 51.5, lon = -0.12, nodes = null, tags = null),
            2L to OverpassElement(type = "node", id = 2L, lat = 51.51, lon = -0.13, nodes = null, tags = null)
        )
        val waypoints = RouteMapper.mapNodesToWaypoints(listOf(1L, 2L), nodes)
        assertEquals(2, waypoints.size)
        assertEquals(51.5, waypoints[0].lat, 0.0001)
        assertEquals(-0.12, waypoints[0].lng, 0.0001)
    }

    @Test
    fun `maps highway tag value to correct SurfaceType enum`() {
        assertEquals(SurfaceType.TRAIL, RouteMapper.mapHighwayToSurfaceType("footway"))
        assertEquals(SurfaceType.ROAD, RouteMapper.mapHighwayToSurfaceType("residential"))
        assertEquals(SurfaceType.MIXED, RouteMapper.mapHighwayToSurfaceType("cycleway"))
    }

    @Test
    fun `preserves route id from builder to domain model`() {
        val route = RouteMapper.buildRoute(
            id = "route-123",
            waypoints = listOf(
                Waypoint(51.5, -0.12),
                Waypoint(51.51, -0.13)
            ),
            surfaceType = SurfaceType.MIXED,
            highways = listOf("footway")
        )
        assertEquals("route-123", route.id)
    }

    @Test
    fun `calculates distanceKm correctly from waypoint sequence`() {
        val waypoints = listOf(
            Waypoint(51.5074, -0.1278),
            Waypoint(51.5174, -0.1278)
        )
        val distance = RouteMapper.calculateDistanceKm(waypoints)
        assertTrue(distance > 1.0)
        assertTrue(distance < 1.5)
    }

    @Test
    fun `assigns safetyScore 1_0 for footway and 0_5 for residential`() {
        val footwayScore = RouteMapper.computeSafetyScore(listOf("footway"))
        val residentialScore = RouteMapper.computeSafetyScore(listOf("residential"))
        assertEquals(1.0f, footwayScore, 0.01f)
        assertEquals(0.5f, residentialScore, 0.01f)
    }
}
