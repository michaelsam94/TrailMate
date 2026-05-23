package com.example.data.routing

import com.example.domain.model.Waypoint
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
        val adjacency = mutableMapOf<Long, MutableSet<Long>>()
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
                if (col + 1 < cols) connect(adjacency, at(row, col), at(row, col + 1))
                if (row + 1 < rows) connect(adjacency, at(row, col), at(row + 1, col))
            }
        }

        return StreetGraph(nodes, adjacency)
    }

    private fun connect(adjacency: MutableMap<Long, MutableSet<Long>>, a: Long, b: Long) {
        adjacency.getOrPut(a) { mutableSetOf() }.add(b)
        adjacency.getOrPut(b) { mutableSetOf() }.add(a)
    }
}
