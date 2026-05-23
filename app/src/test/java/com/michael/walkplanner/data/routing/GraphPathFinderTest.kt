package com.michael.walkplanner.data.routing

import com.michael.walkplanner.domain.model.Waypoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphPathFinderTest {

    private val graph = gridGraph(rows = 4, cols = 4, blockKm = 0.3)

    @Test
    fun `aStar finds shortest path across grid`() {
        val path = GraphPathFinder.shortestPath(graph, source = 1L, target = 4L)
        assertTrue(path != null)
        assertEquals(1L, path!!.nodeIds.first())
        assertEquals(4L, path.nodeIds.last())
        assertTrue(path.nodeIds.size >= 4)
    }

    @Test
    fun `aStar uses haversine heuristic and precomputed edge distances`() {
        val path = GraphPathFinder.aStar(graph, source = 1L, target = 16L)
        assertTrue(path != null)
        assertTrue(path!!.costKm > 0.8)
    }

    @Test
    fun `kShortestPaths returns distinct alternatives`() {
        val paths = GraphPathFinder.kShortestPaths(
            graph = graph,
            source = 1L,
            target = 13L,
            k = 3
        )
        assertTrue(paths.size >= 2, "Expected at least 2 distinct paths, got ${paths.size}")
        assertTrue(paths.map { it.nodeIds }.distinct().size == paths.size)
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
