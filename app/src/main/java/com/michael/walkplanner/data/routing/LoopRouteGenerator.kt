package com.michael.walkplanner.data.routing

import com.michael.walkplanner.core.Constants
import com.michael.walkplanner.data.mapper.RouteMapper
import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.Waypoint
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

class LoopRouteGenerator {

    data class LoopCandidate(
        val route: Route,
        val pathNodeIds: List<Long>,
        val distanceError: Double,
        val initialBearingDeg: Double,
        val turnSignature: String
    )

    fun generateLoops(
        graph: StreetGraph,
        centerLat: Double,
        centerLng: Double,
        targetDistanceKm: Double,
        surfaceType: SurfaceType
    ): List<Route> {
        val startNodeIds = nearestStreetNodes(graph, centerLat, centerLng)
        if (startNodeIds.isEmpty()) return emptyList()

        val allCandidates = mutableListOf<LoopCandidate>()
        val seenSignatures = mutableSetOf<String>()
        val maxDistanceError = targetDistanceKm * Constants.ROUTE_DISTANCE_TOLERANCE_PERCENT
        val searchCap = targetDistanceKm * 0.65

        for (startNodeId in startNodeIds) {
            allCandidates += collectLoopCandidates(
                graph = graph,
                startNodeId = startNodeId,
                targetDistanceKm = targetDistanceKm,
                searchCapKm = searchCap,
                surfaceType = surfaceType,
                seenSignatures = seenSignatures
            )
            if (allCandidates.size >= Constants.ROUTE_OPTION_COUNT * 6) break
        }

        if (allCandidates.isEmpty()) return emptyList()

        var selected = selectDiverseCandidates(allCandidates, maxDistanceError)
        if (selected.size < Constants.ROUTE_OPTION_COUNT) {
            val selectedIds = selected.map { it.route.id }.toSet()
            selected = selected + allCandidates
                .filter { it.route.id !in selectedIds }
                .sortedBy { it.distanceError }
                .take(Constants.ROUTE_OPTION_COUNT - selected.size)
        }

        return selected
            .map { it.route }
            .sortedByDescending { it.safetyScore }
            .take(Constants.ROUTE_OPTION_COUNT)
    }

    private fun collectLoopCandidates(
        graph: StreetGraph,
        startNodeId: Long,
        targetDistanceKm: Double,
        searchCapKm: Double,
        surfaceType: SurfaceType,
        seenSignatures: MutableSet<String>
    ): List<LoopCandidate> {
        val targetHalfDist = targetDistanceKm / 2.0
        val defaultHighways = defaultHighwaysForSurface(surfaceType)
        val startWp = graph.nodes[startNodeId] ?: return emptyList()
        val candidates = mutableListOf<LoopCandidate>()
        val enough = Constants.ROUTE_OPTION_COUNT * 4
        val baseOptions = GraphPathFinder.SearchOptions(maxDistanceKm = searchCapKm)

        // Bounded reachability from start to pick turnaround nodes near ~50% of target distance.
        val reachability = GraphPathFinder.dijkstraAll(graph, startNodeId, baseOptions)
        val turnaroundNodes = reachability.entries
            .asSequence()
            .filter { (nodeId, path) ->
                nodeId != startNodeId &&
                    path.costKm in (targetHalfDist * 0.7)..(targetHalfDist * 1.3)
            }
            .sortedBy { abs(it.value.costKm - targetHalfDist) }
            .take(Constants.TURNAROUND_CANDIDATE_COUNT)
            .map { it.key }
            .toList()

        fun addLoop(outbound: List<Long>, inbound: List<Long>) {
            buildCandidate(
                graph, startNodeId, startWp, outbound, inbound,
                targetDistanceKm, surfaceType, defaultHighways, seenSignatures
            )?.let { candidates += it }
        }

        // Primary: bidirectional A* — outbound to turnaround, return avoiding outbound edges.
        for (turnaround in turnaroundNodes) {
            if (candidates.size >= enough) break
            val outbound = GraphPathFinder.aStar(graph, startNodeId, turnaround, baseOptions) ?: continue
            val outboundEdges = edgesOf(outbound.nodeIds)
            val inbound = returnPathFor(
                graph, turnaround, startNodeId, outboundEdges, searchCapKm
            ) ?: continue
            addLoop(outbound.nodeIds, inbound.nodeIds)
        }

        // Cardinal bias when we still need more distinct loops.
        if (candidates.size < Constants.ROUTE_OPTION_COUNT * 2) {
            for (sector in 0 until Constants.ROUTE_OPTION_COUNT) {
                if (candidates.size >= enough) break
                val bearingOptions = baseOptions.copy(preferredBearingDeg = sector * 90.0)
                for (turnaround in turnaroundNodes) {
                    val outbound = GraphPathFinder.aStar(
                        graph, startNodeId, turnaround, bearingOptions
                    ) ?: continue
                    val inbound = returnPathFor(
                        graph, turnaround, startNodeId,
                        edgesOf(outbound.nodeIds), searchCapKm, bearingOptions
                    ) ?: continue
                    addLoop(outbound.nodeIds, inbound.nodeIds)
                }
            }
        }

        // Penalize edges already used to push onto different streets.
        if (candidates.size < Constants.ROUTE_OPTION_COUNT * 2) {
            val penalties = buildEdgePenalties(candidates, multiplier = 10.0)
            for (turnaround in turnaroundNodes) {
                val outbound = GraphPathFinder.aStar(
                    graph, startNodeId, turnaround,
                    baseOptions.copy(edgePenalties = penalties)
                ) ?: continue
                val inbound = returnPathFor(
                    graph, turnaround, startNodeId,
                    edgesOf(outbound.nodeIds), searchCapKm,
                    baseOptions.copy(edgePenalties = penalties)
                ) ?: continue
                addLoop(outbound.nodeIds, inbound.nodeIds)
            }
        }

        return candidates
    }

    private fun returnPathFor(
        graph: StreetGraph,
        from: Long,
        to: Long,
        outboundEdges: Set<Pair<Long, Long>>,
        searchCapKm: Double,
        options: GraphPathFinder.SearchOptions = GraphPathFinder.SearchOptions(
            maxDistanceKm = searchCapKm
        )
    ): GraphPathFinder.PathResult? {
        val blocked = options.copy(blockedEdges = outboundEdges)
        GraphPathFinder.aStar(graph, from, to, blocked)?.let { return it }

        val soft = options.copy(edgePenalties = outboundEdges.associateWith { 20.0 })
        return GraphPathFinder.aStar(graph, from, to, soft)
    }

    private fun edgesOf(nodeIds: List<Long>): Set<Pair<Long, Long>> =
        nodeIds.zipWithNext().map { (u, v) -> normalizeEdge(u, v) }.toSet()

    private fun buildCandidate(
        graph: StreetGraph,
        startNodeId: Long,
        startWp: Waypoint,
        outbound: List<Long>,
        inbound: List<Long>,
        targetDistanceKm: Double,
        surfaceType: SurfaceType,
        defaultHighways: List<String>,
        seenSignatures: MutableSet<String>
    ): LoopCandidate? {
        if (outbound.size < 2 || inbound.size < 2) return null
        val fullPath = outbound + inbound.drop(1)
        if (fullPath.size < 4 || fullPath.first() != startNodeId || fullPath.last() != startNodeId) return null

        val signature = fullPath.joinToString("-")
        if (!seenSignatures.add(signature)) return null

        val waypoints = fullPath.mapNotNull { graph.nodes[it] }
        if (waypoints.size < 4) return null

        val firstStep = graph.nodes[outbound[1]] ?: return null
        val initialBearing = bearingDegrees(startWp.lat, startWp.lng, firstStep.lat, firstStep.lng)
        val turnSignature = buildTurnSignature(outbound, graph)
        val distanceKm = GraphPathFinder.pathCost(graph, fullPath)

        return LoopCandidate(
            route = RouteMapper.buildRoute(
                id = UUID.randomUUID().toString(),
                waypoints = waypoints,
                surfaceType = surfaceType,
                highways = defaultHighways
            ),
            pathNodeIds = fullPath,
            distanceError = abs(distanceKm - targetDistanceKm),
            initialBearingDeg = initialBearing,
            turnSignature = turnSignature
        )
    }

    private fun selectDiverseCandidates(
        candidates: List<LoopCandidate>,
        maxDistanceError: Double
    ): List<LoopCandidate> {
        val valid = candidates
            .filter { it.distanceError <= maxDistanceError * 2.0 }
            .sortedBy { it.distanceError }
        val pool = valid.ifEmpty { candidates.sortedBy { it.distanceError } }
        val selected = mutableListOf<LoopCandidate>()

        fun tryPick(predicate: (LoopCandidate) -> Boolean) {
            if (selected.size >= Constants.ROUTE_OPTION_COUNT) return
            pool.firstOrNull { candidate ->
                predicate(candidate) &&
                    isDiverse(selected, candidate) &&
                    selected.none { it.route.id == candidate.route.id }
            }?.let { selected += it }
        }

        for (firstHop in pool.mapNotNull { it.pathNodeIds.getOrNull(1) }.distinct()) {
            tryPick { it.pathNodeIds.getOrNull(1) == firstHop }
        }
        for (sector in 0 until Constants.ROUTE_OPTION_COUNT) {
            tryPick { cardinalSector(it.initialBearingDeg) == sector }
        }
        for (candidate in pool) {
            if (selected.size >= Constants.ROUTE_OPTION_COUNT) break
            if (isDiverse(selected, candidate) && selected.none { it.route.id == candidate.route.id }) {
                selected += candidate
            }
        }
        return selected.take(Constants.ROUTE_OPTION_COUNT)
    }

    private fun isDiverse(selected: List<LoopCandidate>, candidate: LoopCandidate): Boolean {
        if (selected.isEmpty()) return true
        val newHop = candidate.pathNodeIds.getOrNull(1)
        return selected.none { existing ->
            val sameHop = existing.pathNodeIds.getOrNull(1) == newHop && newHop != null
            val similarHeading = angularSeparation(existing.initialBearingDeg, candidate.initialBearingDeg) < 30.0
            val overlap = overlapRatio(existing.pathNodeIds.toSet(), candidate.pathNodeIds.toSet()) > 0.80
            (sameHop && similarHeading) || overlap
        }
    }

    private fun overlapRatio(a: Set<Long>, b: Set<Long>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / min(a.size, b.size)
    }

    private fun buildEdgePenalties(candidates: List<LoopCandidate>, multiplier: Double): Map<Pair<Long, Long>, Double> {
        val penalties = mutableMapOf<Pair<Long, Long>, Double>()
        for (candidate in candidates) {
            for ((u, v) in candidate.pathNodeIds.zipWithNext()) {
                val edge = normalizeEdge(u, v)
                penalties[edge] = kotlin.math.max(penalties[edge] ?: 1.0, multiplier)
            }
        }
        return penalties
    }

    private fun buildTurnSignature(path: List<Long>, graph: StreetGraph): String {
        if (path.size < 3) return ""
        val turns = StringBuilder()
        for (i in 1 until path.size - 1) {
            if (graph.degreeOf(path[i]) < 3) continue
            val prev = graph.nodes[path[i - 1]] ?: continue
            val curr = graph.nodes[path[i]] ?: continue
            val next = graph.nodes[path[i + 1]] ?: continue
            val inB = bearingDegrees(prev.lat, prev.lng, curr.lat, curr.lng)
            val outB = bearingDegrees(curr.lat, curr.lng, next.lat, next.lng)
            val delta = ((outB - inB + 540.0) % 360.0) - 180.0
            turns.append(
                when {
                    abs(delta) < 25.0 -> 'S'
                    delta > 0 -> 'R'
                    else -> 'L'
                }
            )
        }
        return turns.toString()
    }

    private fun cardinalSector(bearing: Double): Int =
        (((bearing + 45.0) % 360.0) / 90.0).toInt().coerceIn(0, 3)

    private fun nearestStreetNodes(
        graph: StreetGraph,
        centerLat: Double,
        centerLng: Double,
        maxDistanceKm: Double = 0.6,
        limit: Int = 3
    ): List<Long> {
        return graph.nodes.entries
            .asSequence()
            .map { (id, waypoint) ->
                id to haversineKm(centerLat, centerLng, waypoint.lat, waypoint.lng)
            }
            .filter { (id, distanceKm) ->
                distanceKm <= maxDistanceKm && graph.degreeOf(id) > 0
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun defaultHighwaysForSurface(surfaceType: SurfaceType): List<String> = when (surfaceType) {
        SurfaceType.TRAIL -> listOf("footway", "path")
        SurfaceType.PARK -> listOf("footway", "path")
        SurfaceType.ROAD -> listOf("residential")
        SurfaceType.MIXED -> listOf("footway", "residential")
    }
}
