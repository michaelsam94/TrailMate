package com.michael.walkplanner.data.routing

import com.michael.walkplanner.core.Constants
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.Waypoint
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class LoopRouteGeneratorTest {

    private val generator = LoopRouteGenerator()

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `generates multiple diverse loop options on street grid`() {
        val graph = gridGraph(rows = 5, cols = 5, blockKm = 0.35)
        val centerNode = 13L
        val center = graph.nodes[centerNode]!!

        val routes = generator.generateLoops(
            graph = graph,
            centerLat = center.lat,
            centerLng = center.lng,
            targetDistanceKm = 3.2,
            surfaceType = SurfaceType.MIXED
        )

        assertTrue(routes.isNotEmpty(), "Expected at least one route")
        assertTrue(
            routes.size >= 2,
            "Expected multiple route options, got ${routes.size}"
        )
        assertTrue(routes.size <= Constants.ROUTE_OPTION_COUNT)
    }

    private fun gridGraph(rows: Int, cols: Int, blockKm: Double): StreetGraph {
        val originLat = 24.7
        val originLng = 46.6
        val nodes = mutableMapOf<Long, Waypoint>()
        val adjacency = mutableMapOf<Long, MutableList<GraphEdge>>()
        var id = 1L

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                nodes[id] = Waypoint(
                    lat = originLat + row * blockKm / 111.0,
                    lng = originLng + col * blockKm / (111.0 * kotlin.math.cos(Math.toRadians(originLat)))
                )
                id++
            }
        }

        fun at(row: Int, col: Int) = (row * cols + col + 1).toLong()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (col + 1 < cols) connect(adjacency, nodes, at(row, col), at(row, col + 1))
                if (row + 1 < rows) connect(adjacency, nodes, at(row, col), at(row + 1, col))
            }
        }

        return StreetGraph(nodes, adjacency)
    }

    private fun connect(
        adjacency: MutableMap<Long, MutableList<GraphEdge>>,
        nodes: Map<Long, Waypoint>,
        a: Long,
        b: Long
    ) {
        val nodeA = nodes[a] ?: return
        val nodeB = nodes[b] ?: return
        val distanceM = haversineDistanceM(nodeA.lat, nodeA.lng, nodeB.lat, nodeB.lng)
        adjacency.getOrPut(a) { mutableListOf() }.add(GraphEdge(b, distanceM, 1.0f))
        adjacency.getOrPut(b) { mutableListOf() }.add(GraphEdge(a, distanceM, 1.0f))
    }
}
