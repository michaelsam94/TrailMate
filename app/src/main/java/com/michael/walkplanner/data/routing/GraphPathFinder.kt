package com.michael.walkplanner.data.routing

import com.michael.walkplanner.domain.model.Waypoint
import java.util.PriorityQueue

object GraphPathFinder {

    data class PathResult(
        val nodeIds: List<Long>,
        val costKm: Double
    )

    data class SearchOptions(
        val blockedEdges: Set<Pair<Long, Long>> = emptySet(),
        val edgePenalties: Map<Pair<Long, Long>, Double> = emptyMap(),
        val bannedNodes: Set<Long> = emptySet(),
        val preferredBearingDeg: Double? = null,
        val maxDistanceKm: Double = Double.MAX_VALUE
    )

    fun shortestPath(
        graph: StreetGraph,
        source: Long,
        target: Long,
        options: SearchOptions = SearchOptions()
    ): PathResult? = aStar(graph, source, target, options)

    fun aStar(
        graph: StreetGraph,
        source: Long,
        target: Long,
        options: SearchOptions = SearchOptions()
    ): PathResult? {
        if (source == target) return PathResult(listOf(source), 0.0)
        val targetWp = graph.nodes[target] ?: return null
        val gScore = mutableMapOf(source to 0.0)
        val cameFrom = mutableMapOf<Long, Long>()
        val open = PriorityQueue<Pair<Long, Double>>(compareBy { it.second })
        open.add(source to heuristic(graph, source, targetWp))

        while (open.isNotEmpty()) {
            val (current, _) = open.poll()
            if (current == target) {
                return reconstructPath(source, target, cameFrom, gScore[current] ?: 0.0)
            }
            val currentG = gScore[current] ?: continue

            for (edge in graph.adjacencyList[current].orEmpty()) {
                val neighbor = edge.toNodeId
                if (neighbor in options.bannedNodes && neighbor != target) continue
                val step = edgeCost(graph, current, edge, options) ?: continue
                val tentativeG = currentG + step
                if (options.maxDistanceKm < Double.MAX_VALUE / 2 && tentativeG > options.maxDistanceKm) continue
                if (tentativeG >= (gScore[neighbor] ?: Double.MAX_VALUE)) continue
                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG
                open.add(neighbor to (tentativeG + heuristic(graph, neighbor, targetWp)))
            }
        }
        return null
    }

    fun dijkstraAll(
        graph: StreetGraph,
        source: Long,
        options: SearchOptions = SearchOptions()
    ): Map<Long, PathResult> {
        val dist = mutableMapOf(source to 0.0)
        val parent = mutableMapOf<Long, Long>()
        val queue = PriorityQueue<Pair<Long, Double>>(compareBy { it.second })
        queue.add(source to 0.0)

        while (queue.isNotEmpty()) {
            val (u, costU) = queue.poll()
            if (costU > (dist[u] ?: Double.MAX_VALUE)) continue

            for (edge in graph.adjacencyList[u].orEmpty()) {
                val v = edge.toNodeId
                if (v in options.bannedNodes && v != source) continue
                val step = edgeCost(graph, u, edge, options) ?: continue
                val newCost = costU + step
                if (options.maxDistanceKm < Double.MAX_VALUE / 2 && newCost > options.maxDistanceKm) continue
                if (newCost < (dist[v] ?: Double.MAX_VALUE)) {
                    dist[v] = newCost
                    parent[v] = u
                    queue.add(v to newCost)
                }
            }
        }

        return dist.mapValues { (nodeId, costKm) ->
            PathResult(reconstructNodeList(nodeId, source, parent), costKm)
        }
    }

    /** Yen's algorithm — returns up to [k] shortest distinct paths from [source] to [target]. */
    fun kShortestPaths(
        graph: StreetGraph,
        source: Long,
        target: Long,
        k: Int,
        options: SearchOptions = SearchOptions()
    ): List<PathResult> {
        if (k <= 0) return emptyList()
        val first = shortestPath(graph, source, target, options) ?: return emptyList()

        val paths = mutableListOf(first)
        val candidates = PriorityQueue<PathResult>(compareBy { it.costKm })

        for (pathIndex in 1 until k) {
            val previous = paths[pathIndex - 1]
            for (spurIndex in 0 until previous.nodeIds.size - 1) {
                val spurNode = previous.nodeIds[spurIndex]
                val rootNodes = previous.nodeIds.subList(0, spurIndex + 1)
                val rootCost = pathCost(graph, rootNodes, options)

                val spurOptions = options.copy(
                    blockedEdges = options.blockedEdges + rootSpurBlockedEdges(paths, rootNodes, spurIndex),
                    bannedNodes = options.bannedNodes + rootNodes.dropLast(1).toSet()
                )

                val spurPath = shortestPath(graph, spurNode, target, spurOptions) ?: continue
                val combinedNodes = rootNodes.dropLast(1) + spurPath.nodeIds
                val combinedCost = rootCost + spurPath.costKm
                if (combinedNodes.first() != source || combinedNodes.last() != target) continue
                candidates.add(PathResult(combinedNodes, combinedCost))
            }

            var next: PathResult? = null
            while (candidates.isNotEmpty()) {
                val candidate = candidates.poll()
                if (paths.none { it.nodeIds == candidate.nodeIds }) {
                    next = candidate
                    break
                }
            }
            if (next == null) break
            paths.add(next)
        }
        return paths
    }

    fun pathCost(
        graph: StreetGraph,
        nodeIds: List<Long>,
        options: SearchOptions = SearchOptions()
    ): Double {
        var total = 0.0
        for (i in 0 until nodeIds.size - 1) {
            val edge = findEdge(graph, nodeIds[i], nodeIds[i + 1]) ?: return Double.MAX_VALUE
            total += edgeCost(graph, nodeIds[i], edge, options) ?: return Double.MAX_VALUE
        }
        return total
    }

    private fun rootSpurBlockedEdges(
        paths: List<PathResult>,
        rootNodes: List<Long>,
        spurIndex: Int
    ): Set<Pair<Long, Long>> {
        val blocked = mutableSetOf<Pair<Long, Long>>()
        for (path in paths) {
            if (path.nodeIds.size > spurIndex + 1 &&
                path.nodeIds.subList(0, spurIndex + 1) == rootNodes
            ) {
                blocked.add(normalizeEdge(path.nodeIds[spurIndex], path.nodeIds[spurIndex + 1]))
            }
        }
        return blocked
    }

    private fun reconstructPath(
        source: Long,
        target: Long,
        cameFrom: Map<Long, Long>,
        costKm: Double
    ): PathResult = PathResult(reconstructNodeList(target, source, cameFrom), costKm)

    private fun reconstructNodeList(
        target: Long,
        source: Long,
        parent: Map<Long, Long>
    ): List<Long> {
        val nodes = mutableListOf<Long>()
        var current: Long? = target
        while (current != null) {
            nodes.add(current)
            if (current == source) break
            current = parent[current]
        }
        if (nodes.lastOrNull() != source) return emptyList()
        nodes.reverse()
        return nodes
    }

    private fun heuristic(graph: StreetGraph, from: Long, target: Waypoint): Double {
        val fromWp = graph.nodes[from] ?: return 0.0
        return haversineKm(fromWp.lat, fromWp.lng, target.lat, target.lng)
    }

    private fun findEdge(graph: StreetGraph, from: Long, to: Long): GraphEdge? =
        graph.adjacencyList[from]?.firstOrNull { it.toNodeId == to }

    private fun edgeCost(
        graph: StreetGraph,
        u: Long,
        edge: GraphEdge,
        options: SearchOptions
    ): Double? {
        val v = edge.toNodeId
        val normalized = normalizeEdge(u, v)
        if (normalized in options.blockedEdges) return null
        var cost = edge.distanceM / 1000.0
        cost *= edge.safetyWeight
        cost *= options.edgePenalties[normalized] ?: 1.0
        options.preferredBearingDeg?.let { bearing ->
            val nodeU = graph.nodes[u] ?: return null
            val nodeV = graph.nodes[v] ?: return null
            val edgeBearing = bearingDegrees(nodeU.lat, nodeU.lng, nodeV.lat, nodeV.lng)
            val sep = angularSeparation(edgeBearing, bearing)
            cost *= when {
                sep <= 25.0 -> 0.7
                sep <= 45.0 -> 0.9
                sep <= 70.0 -> 1.4
                sep <= 110.0 -> 3.0
                else -> 8.0
            }
        }
        return cost
    }
}
