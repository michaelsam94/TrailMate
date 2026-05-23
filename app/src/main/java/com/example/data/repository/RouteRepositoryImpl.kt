package com.example.data.repository

import android.content.Context
import com.example.data.local.db.*
import com.example.data.remote.api.OverpassService
import com.example.data.remote.dto.OverpassResponse
import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.model.Waypoint
import com.example.domain.error.DomainError
import com.example.domain.repository.RouteRepository
import com.example.data.mapper.RouteMapper
import com.example.core.util.NetworkUtils
import com.example.core.Result
import com.example.core.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.*

class RouteRepositoryImpl(
    private val context: Context,
    private val routeDao: RouteDao,
    private val osmCacheDao: OsmCacheDao,
    private val overpassService: OverpassService
) : RouteRepository {

    override fun generateRoute(
        lat: Double,
        lng: Double,
        distanceKm: Double,
        surfaceType: SurfaceType,
        avoidHighways: Boolean,
        maxElevationM: Double
    ): Flow<Result<List<Route>, DomainError>> = flow {
        emit(Result.Loading)

        clearExpiredCache()

        val isOnline = NetworkUtils.isOnline(context)
        val radiusM = (distanceKm * 600.0).coerceIn(1500.0, 6000.0)
        val highwayFilter = if (avoidHighways) {
            "^(footway|path|cycleway|pedestrian|steps|living_street|residential|unclassified|service)$"
        } else {
            "^(footway|path|cycleway|pedestrian|steps|living_street|residential|unclassified|service|tertiary)$"
        }
        val query = """
            [out:json][timeout:30];
            (
              way[highway~"$highwayFilter"]
                 (around:$radiusM,$lat,$lng);
              way[leisure=track]
                 (around:$radiusM,$lat,$lng);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val endpoints = if (isOnline) {
            listOf(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
                "https://lz4.overpass-api.de/api/interpreter"
            )
        } else {
            emptyList()
        }

        var success = false
        var exception: Exception? = null
        var response: OverpassResponse? = null

        for (endpoint in endpoints) {
            android.util.Log.d("TrailMate", "Querying Overpass API at endpoint: $endpoint")
            try {
                response = overpassService.queryOverpass(endpoint, query)
                success = true
                android.util.Log.d("TrailMate", "Overpass response success: $success, elements count: ${response.elements?.size}")
                break
            } catch (e: Exception) {
                exception = e
                android.util.Log.e("TrailMate", "Overpass query failed at endpoint $endpoint", e)
            }
        }

        if (success && response?.elements != null) {
            val elements = response.elements ?: emptyList()
            val nodesMap = elements.filter { it.type == "node" }.associateBy { it.id }
            val ways = elements.filter { it.type == "way" }

            try {
                val nodeEntities = nodesMap.values.map {
                    NodeEntity(it.id, it.lat ?: 0.0, it.lon ?: 0.0)
                }
                val wayEntities = ways.map {
                    WayEntity(
                        id = it.id,
                        surfaceType = it.tags?.get("highway") ?: "path",
                        nodeIds = it.nodes?.joinToString(",") ?: ""
                    )
                }
                osmCacheDao.insertAllNodes(nodeEntities)
                osmCacheDao.insertAllWays(wayEntities)
            } catch (e: Exception) {
                // Secondary operation, ignore caching fails
            }

            val graph = buildStreetGraphFromOverpass(nodesMap, ways, surfaceType)
            if (graph != null) {
                val parsedRoutes = withContext(Dispatchers.Default) {
                    findStreetLoopRoutes(graph, lat, lng, distanceKm, surfaceType)
                }
                if (parsedRoutes.isNotEmpty()) {
                    routeDao.insertAllRoutes(parsedRoutes.map { it.toEntity() })
                    emit(Result.Success(parsedRoutes))
                    return@flow
                }
            }
        }

        try {
            val cachedGraph = buildStreetGraphFromCache(surfaceType)
            if (cachedGraph != null) {
                val cachedRoutes = withContext(Dispatchers.Default) {
                    findStreetLoopRoutes(cachedGraph, lat, lng, distanceKm, surfaceType)
                }
                if (cachedRoutes.isNotEmpty()) {
                    routeDao.insertAllRoutes(cachedRoutes.map { it.toEntity() })
                    emit(Result.Success(cachedRoutes))
                    return@flow
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TrailMate", "Failed to build routes from cached OSM data", e)
        }

        emit(
            Result.Failure(
                when {
                    !isOnline && !success -> DomainError.NetworkUnavailable
                    success -> DomainError.RouteGenerationFailed
                    else -> DomainError.NetworkUnavailable
                }
            )
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun getCacheAgeDays(): Int {
        return try {
            val nodes = osmCacheDao.getAllNodes()
            if (nodes.isEmpty()) return 0
            val oldest = nodes.minOf { it.insertedAt }
            val ageMs = System.currentTimeMillis() - oldest
            (ageMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun getCachedRoutes(): Result<List<Route>, DomainError> {
        return try {
            val entities = routeDao.getAllRoutes()
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(DomainError.Unknown(e))
        }
    }

    override suspend fun clearExpiredCache(): Result<Unit, DomainError> {
        return try {
            val threshold = System.currentTimeMillis() - (Constants.CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            routeDao.deleteOldRoutes(threshold)
            osmCacheDao.deleteOldNodes(threshold)
            osmCacheDao.deleteOldWays(threshold)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(DomainError.Unknown(e))
        }
    }

    override suspend fun saveRoute(route: Route) {
        routeDao.insertRoute(route.toEntity())
    }

    override suspend fun getRouteById(id: String): Route? {
        return routeDao.getRouteById(id)?.toDomain()
    }

    private data class StreetGraph(
        val nodes: Map<Long, Waypoint>,
        val adjacencyList: Map<Long, Set<Long>>
    )

    private data class LoopCandidate(
        val route: Route,
        val pathNodeIds: List<Long>,
        val middleNodes: Set<Long>,
        val distanceError: Double,
        val initialBearingDeg: Double,
        val turnSignature: String
    )

    private fun isWayAllowed(highway: String, leisure: String?, surfaceType: SurfaceType): Boolean {
        return when (surfaceType) {
            SurfaceType.TRAIL -> highway == "path" || highway == "footway" || highway == "steps"
            SurfaceType.PARK -> highway in setOf("footway", "path", "steps") || leisure == "track"
            SurfaceType.ROAD -> highway in setOf(
                "residential", "living_street", "unclassified", "service", "tertiary", "pedestrian"
            )
            SurfaceType.MIXED -> highway.isNotBlank() || leisure == "track"
        }
    }

    private fun buildStreetGraphFromOverpass(
        nodesMap: Map<Long, com.example.data.remote.dto.OverpassElement>,
        ways: List<com.example.data.remote.dto.OverpassElement>,
        surfaceType: SurfaceType
    ): StreetGraph? {
        var filteredWays = ways.filter { way ->
            isWayAllowed(
                highway = way.tags?.get("highway") ?: "",
                leisure = way.tags?.get("leisure"),
                surfaceType = surfaceType
            )
        }
        if (filteredWays.isEmpty() && surfaceType != SurfaceType.MIXED) {
            filteredWays = ways
        }

        val nodes = mutableMapOf<Long, Waypoint>()
        for ((id, element) in nodesMap) {
            val lat = element.lat
            val lon = element.lon
            if (lat != null && lon != null) {
                nodes[id] = Waypoint(lat, lon)
            }
        }

        val wayNodeLists = filteredWays.mapNotNull { way ->
            way.nodes?.filter { nodes.containsKey(it) }?.takeIf { it.size >= 2 }
        }

        return buildStreetGraph(nodes, wayNodeLists)
    }

    private suspend fun buildStreetGraphFromCache(surfaceType: SurfaceType): StreetGraph? {
        val wayEntities = osmCacheDao.getAllWays()
        if (wayEntities.isEmpty()) return null

        val nodes = osmCacheDao.getAllNodes().associate { entity ->
            entity.id to Waypoint(entity.lat, entity.lng, entity.elevationM)
        }
        if (nodes.isEmpty()) return null

        val wayNodeLists = wayEntities.mapNotNull { way ->
            val nodeIds = way.nodeIds.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { nodes.containsKey(it) }
            if (isWayAllowed(way.surfaceType, null, surfaceType) && nodeIds.size >= 2) {
                nodeIds
            } else {
                null
            }
        }.ifEmpty {
            wayEntities.mapNotNull { way ->
                way.nodeIds.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .filter { nodes.containsKey(it) }
                    .takeIf { it.size >= 2 }
            }
        }

        return buildStreetGraph(nodes, wayNodeLists)
    }

    private fun buildStreetGraph(
        nodes: Map<Long, Waypoint>,
        wayNodeLists: List<List<Long>>
    ): StreetGraph? {
        if (nodes.isEmpty() || wayNodeLists.isEmpty()) return null

        val adjacencyList = mutableMapOf<Long, MutableSet<Long>>()
        for (nodeIds in wayNodeLists) {
            for (i in 0 until nodeIds.size - 1) {
                val u = nodeIds[i]
                val v = nodeIds[i + 1]
                if (nodes.containsKey(u) && nodes.containsKey(v)) {
                    adjacencyList.getOrPut(u) { mutableSetOf() }.add(v)
                    adjacencyList.getOrPut(v) { mutableSetOf() }.add(u)
                }
            }
        }

        if (adjacencyList.isEmpty()) return null
        return StreetGraph(nodes, adjacencyList)
    }

    private fun nearestStreetNodes(
        graph: StreetGraph,
        centerLat: Double,
        centerLng: Double,
        maxDistanceKm: Double = 0.45,
        limit: Int = 6
    ): List<Long> {
        return graph.nodes.entries
            .asSequence()
            .map { (id, waypoint) ->
                id to calculateDistance(centerLat, centerLng, waypoint.lat, waypoint.lng)
            }
            .filter { (id, distanceKm) ->
                distanceKm <= maxDistanceKm && graph.adjacencyList[id]?.isNotEmpty() == true
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun middleSegmentNodes(pathNodes: List<Long>, startNodeId: Long): Set<Long> {
        val body = pathNodes.filter { it != startNodeId }
        if (body.size <= 4) return body.toSet()
        val trim = (body.size * 0.15).toInt().coerceAtLeast(1)
        return body.drop(trim).dropLast(trim).toSet()
    }

    private fun overlapRatio(a: Set<Long>, b: Set<Long>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b)
        return intersection.size.toDouble() / min(a.size, b.size)
    }

    private fun normalizeEdge(u: Long, v: Long): Pair<Long, Long> =
        if (u < v) u to v else v to u

    private fun pathDistanceKm(nodes: Map<Long, Waypoint>, path: List<Long>): Double {
        var total = 0.0
        for (i in 0 until path.size - 1) {
            val a = nodes[path[i]] ?: continue
            val b = nodes[path[i + 1]] ?: continue
            total += calculateDistance(a.lat, a.lng, b.lat, b.lng)
        }
        return total
    }

    private fun buildTurnSignature(
        path: List<Long>,
        nodes: Map<Long, Waypoint>,
        adjacencyList: Map<Long, Set<Long>>
    ): String {
        if (path.size < 3) return ""
        val turns = StringBuilder()
        for (i in 1 until path.size - 1) {
            if ((adjacencyList[path[i]]?.size ?: 0) < 3) continue
            val prev = nodes[path[i - 1]] ?: continue
            val curr = nodes[path[i]] ?: continue
            val next = nodes[path[i + 1]] ?: continue
            val inBearing = bearingDegrees(prev.lat, prev.lng, curr.lat, curr.lng)
            val outBearing = bearingDegrees(curr.lat, curr.lng, next.lat, next.lng)
            val delta = ((outBearing - inBearing + 540.0) % 360.0) - 180.0
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

    /** Enumerate paths covering distinct left/right choices at the first few junctions. */
    private fun enumerateJunctionPrefixes(
        graph: StreetGraph,
        startNodeId: Long,
        maxDecisionPoints: Int = 3,
        maxResults: Int = 32
    ): List<List<Long>> {
        val adjacencyList = graph.adjacencyList
        val results = mutableListOf<List<Long>>()
        val seen = mutableSetOf<String>()

        data class State(val path: List<Long>, val decisionsMade: Int)

        val stack = ArrayDeque<State>()
        for (neighbor in adjacencyList[startNodeId].orEmpty().sorted()) {
            stack.add(State(listOf(startNodeId, neighbor), 0))
        }

        while (stack.isNotEmpty() && results.size < maxResults) {
            val state = stack.removeLast()
            val path = state.path
            if (path.size < 2) continue

            val current = path.last()
            val prev = path[path.size - 2]
            val forwardNeighbors = adjacencyList[current].orEmpty().filter { it != prev }.sorted()
            val isDecisionPoint = (adjacencyList[current]?.size ?: 0) >= 3

            val decisionsMade = if (isDecisionPoint) state.decisionsMade + 1 else state.decisionsMade

            if (decisionsMade >= 1) {
                val key = path.joinToString("-")
                if (seen.add(key)) results.add(path)
            }

            if (decisionsMade >= maxDecisionPoints || path.size >= 24) continue

            for (next in forwardNeighbors) {
                stack.add(State(path + next, decisionsMade))
            }
        }

        return results
    }

    private fun buildEdgePenalties(
        candidates: List<LoopCandidate>,
        multiplier: Double
    ): Map<Pair<Long, Long>, Double> {
        val penalties = mutableMapOf<Pair<Long, Long>, Double>()
        for (candidate in candidates) {
            for ((u, v) in candidate.pathNodeIds.zipWithNext()) {
                val edge = normalizeEdge(u, v)
                penalties[edge] = max(penalties[edge] ?: 1.0, multiplier)
            }
        }
        return penalties
    }

    private fun selectDiverseRoutes(
        candidates: List<LoopCandidate>,
        targetCount: Int
    ): List<Route> {
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedBy { it.distanceError }
        val selected = mutableListOf<LoopCandidate>()

        for ((maxOverlap, minBearingSep, allowSameTurns) in listOf(
            Triple(0.15, 70.0, false),
            Triple(0.25, 55.0, false),
            Triple(0.35, 40.0, true),
            Triple(0.50, 25.0, true)
        )) {
            for (candidate in sorted) {
                if (selected.size >= targetCount) break
                if (selected.any { it.route.id == candidate.route.id }) continue

                val worstMiddleOverlap = selected.maxOfOrNull {
                    overlapRatio(candidate.middleNodes, it.middleNodes)
                } ?: 0.0
                val worstPathOverlap = selected.maxOfOrNull {
                    overlapRatio(candidate.pathNodeIds.toSet(), it.pathNodeIds.toSet())
                } ?: 0.0
                val worstOverlap = max(worstMiddleOverlap, worstPathOverlap)

                val minSep = selected.minOfOrNull {
                    angularSeparation(candidate.initialBearingDeg, it.initialBearingDeg)
                } ?: 360.0

                val turnClash = !allowSameTurns &&
                    candidate.turnSignature.isNotEmpty() &&
                    selected.any {
                        it.turnSignature.isNotEmpty() &&
                            it.turnSignature == candidate.turnSignature
                    }

                if (worstOverlap <= maxOverlap && minSep >= minBearingSep && !turnClash) {
                    selected.add(candidate)
                }
            }
            if (selected.size >= targetCount) break
        }

        return selected.map { it.route }
    }

    private fun findStreetLoopRoutes(
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

        for (startNodeId in startNodeIds) {
            allCandidates.addAll(
                collectLoopCandidates(
                    graph = graph,
                    startNodeId = startNodeId,
                    targetDistanceKm = targetDistanceKm,
                    surfaceType = surfaceType,
                    seenSignatures = seenSignatures
                )
            )
            if (allCandidates.size >= Constants.ROUTE_OPTION_COUNT * 12) break
        }

        if (allCandidates.isEmpty()) return emptyList()

        var routes = selectDiverseRoutes(allCandidates, Constants.ROUTE_OPTION_COUNT)
        if (routes.size < Constants.ROUTE_OPTION_COUNT) {
            val selectedIds = routes.map { it.id }.toSet()
            val extras = allCandidates
                .filter { it.route.id !in selectedIds }
                .sortedBy { it.distanceError }
            for (candidate in extras) {
                if (routes.size >= Constants.ROUTE_OPTION_COUNT) break
                routes = routes + candidate.route
            }
        }

        return routes.sortedByDescending { it.safetyScore }.take(Constants.ROUTE_OPTION_COUNT)
    }

    private fun collectLoopCandidates(
        graph: StreetGraph,
        startNodeId: Long,
        targetDistanceKm: Double,
        surfaceType: SurfaceType,
        seenSignatures: MutableSet<String>
    ): List<LoopCandidate> {
        val nodes = graph.nodes
        val adjacencyList = graph.adjacencyList
        val targetHalfDist = targetDistanceKm / 2.2
        val defaultHighways = defaultHighwaysForSurface(surfaceType)
        val candidates = mutableListOf<LoopCandidate>()

        data class PathInfo(val distanceKm: Double, val parentId: Long?)

        fun runDijkstra(
            start: Long,
            blockedEdges: Set<Pair<Long, Long>> = emptySet(),
            edgePenalties: Map<Pair<Long, Long>, Double> = emptyMap()
        ): Map<Long, PathInfo> {
            val distances = mutableMapOf<Long, PathInfo>()
            distances[start] = PathInfo(0.0, null)

            val queue = java.util.PriorityQueue<Pair<Long, Double>>(compareBy { it.second })
            queue.add(start to 0.0)

            while (queue.isNotEmpty()) {
                val pollResult = queue.poll() ?: continue
                val (u, distU) = pollResult
                val currentBest = distances[u]?.distanceKm ?: Double.MAX_VALUE
                if (distU > currentBest) continue

                val neighbors = adjacencyList[u] ?: continue
                for (v in neighbors) {
                    val edge = normalizeEdge(u, v)
                    if (blockedEdges.contains(edge)) continue

                    val nodeU = nodes[u] ?: continue
                    val nodeV = nodes[v] ?: continue
                    val edgeDist = calculateDistance(nodeU.lat, nodeU.lng, nodeV.lat, nodeV.lng)
                    val penalty = edgePenalties[edge] ?: 1.0
                    val newDist = distU + edgeDist * penalty

                    val bestV = distances[v]?.distanceKm ?: Double.MAX_VALUE
                    if (newDist < bestV) {
                        distances[v] = PathInfo(newDist, u)
                        queue.add(v to newDist)
                    }
                }
            }
            return distances
        }

        fun buildLoopFromTurnaround(
            targetId: Long,
            forward: Map<Long, PathInfo>,
            requiredPrefix: List<Long>? = null,
            requiredFirstHop: Long? = null
        ): LoopCandidate? {
            val path1 = mutableListOf<Long>()
            var curr: Long? = targetId
            while (curr != null) {
                path1.add(curr)
                curr = forward[curr]?.parentId
            }
            path1.reverse()
            if (path1.size < 2) return null

            if (requiredFirstHop != null && path1.getOrNull(1) != requiredFirstHop) return null
            if (requiredPrefix != null) {
                if (path1.size < requiredPrefix.size) return null
                for (i in requiredPrefix.indices) {
                    if (path1[i] != requiredPrefix[i]) return null
                }
            }

            val outboundEdges = path1.zipWithNext().map { (u, v) -> normalizeEdge(u, v) }.toSet()
            val returnPaths = runDijkstra(targetId, outboundEdges)
            if (!returnPaths.containsKey(startNodeId)) return null

            val path2 = mutableListOf<Long>()
            var rc: Long? = startNodeId
            while (rc != null) {
                path2.add(rc)
                rc = returnPaths[rc]?.parentId
            }
            path2.reverse()

            val fullPathNodes = mutableListOf<Long>()
            fullPathNodes.addAll(path1)
            fullPathNodes.addAll(path2.drop(1))
            if (fullPathNodes.size < 4) return null

            val signature = fullPathNodes.joinToString("-")
            if (!seenSignatures.add(signature)) return null

            val routeWaypoints = fullPathNodes.mapNotNull { id -> nodes[id] }
            if (routeWaypoints.size < 4) return null

            val startWp = nodes[startNodeId] ?: return null
            val firstStepId = path1.getOrNull(1) ?: return null
            val firstStepWp = nodes[firstStepId] ?: return null
            val initialBearing = bearingDegrees(
                startWp.lat, startWp.lng,
                firstStepWp.lat, firstStepWp.lng
            )

            var totalDist = 0.0
            for (i in 0 until routeWaypoints.size - 1) {
                totalDist += calculateDistance(
                    routeWaypoints[i].lat, routeWaypoints[i].lng,
                    routeWaypoints[i + 1].lat, routeWaypoints[i + 1].lng
                )
            }

            val middleNodes = middleSegmentNodes(fullPathNodes, startNodeId)
            return LoopCandidate(
                route = RouteMapper.buildRoute(
                    id = UUID.randomUUID().toString(),
                    waypoints = routeWaypoints,
                    surfaceType = surfaceType,
                    highways = defaultHighways
                ),
                pathNodeIds = fullPathNodes,
                middleNodes = middleNodes,
                distanceError = abs(totalDist - targetDistanceKm),
                initialBearingDeg = initialBearing,
                turnSignature = buildTurnSignature(path1, nodes, adjacencyList)
            )
        }

        fun getTurnaroundCandidates(
            forward: Map<Long, PathInfo>,
            minFactor: Double,
            maxFactor: Double
        ): List<Pair<Long, Double>> {
            val turnaroundCandidates = mutableListOf<Pair<Long, Double>>()
            for ((id, pathInfo) in forward) {
                if (id == startNodeId) continue
                val dist = pathInfo.distanceKm
                if (dist in (targetHalfDist * minFactor)..(targetHalfDist * maxFactor)) {
                    turnaroundCandidates.add(id to dist)
                }
            }
            return turnaroundCandidates.sortedBy { abs(it.second - targetHalfDist) }
        }

        fun collectBestLoop(
            forward: Map<Long, PathInfo>,
            requiredPrefix: List<Long>? = null,
            requiredFirstHop: Long? = null
        ): LoopCandidate? {
            var best: LoopCandidate? = null
            for ((minFactor, maxFactor, limit) in listOf(
                Triple(0.4, 1.5, 100),
                Triple(0.1, 3.0, 160)
            )) {
                for ((targetId, _) in getTurnaroundCandidates(forward, minFactor, maxFactor).take(limit)) {
                    buildLoopFromTurnaround(
                        targetId, forward, requiredPrefix, requiredFirstHop
                    )?.let { candidate ->
                        if (best == null || candidate.distanceError < best!!.distanceError) {
                            best = candidate
                        }
                    }
                }
                if (best != null) break
            }
            return best
        }

        fun searchTurnarounds(
            forward: Map<Long, PathInfo>,
            limitPerPass: Int = Constants.ROUTE_OPTION_COUNT * 4
        ) {
            val seenTurnarounds = mutableSetOf<Long>()
            for ((minFactor, maxFactor, limit) in listOf(
                Triple(0.4, 1.5, 100),
                Triple(0.1, 3.0, 160)
            )) {
                for ((targetId, _) in getTurnaroundCandidates(forward, minFactor, maxFactor).take(limit)) {
                    if (!seenTurnarounds.add(targetId)) continue
                    buildLoopFromTurnaround(targetId, forward)?.let { candidates.add(it) }
                }
                if (candidates.size >= limitPerPass) break
            }
        }

        val forwardPaths = runDijkstra(startNodeId)

        // Phase 1: one best loop per initial direction (left / right on current street).
        val firstHopNeighbors = adjacencyList[startNodeId]?.toList().orEmpty()
        for (firstHop in firstHopNeighbors) {
            collectBestLoop(forwardPaths, requiredFirstHop = firstHop)?.let { candidates.add(it) }
        }

        // Phase 2: one best loop per distinct junction decision sequence (L/R at each crossing).
        for (prefix in enumerateJunctionPrefixes(graph, startNodeId)) {
            collectBestLoop(forwardPaths, requiredPrefix = prefix)?.let { candidates.add(it) }
        }

        // Phase 3: general search for distance-fit fallbacks.
        searchTurnarounds(forwardPaths)

        // Phase 4: penalize streets already used so later loops take different turns.
        for (penalty in listOf(2.5, 5.0, 9.0)) {
            if (candidates.size >= Constants.ROUTE_OPTION_COUNT * 8) break
            val penalizedForward = runDijkstra(
                startNodeId,
                edgePenalties = buildEdgePenalties(candidates, penalty)
            )
            for (firstHop in firstHopNeighbors) {
                collectBestLoop(penalizedForward, requiredFirstHop = firstHop)?.let { candidates.add(it) }
            }
            searchTurnarounds(penalizedForward, limitPerPass = Constants.ROUTE_OPTION_COUNT * 2)
        }

        return candidates
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angularSeparation(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return min(diff, 360.0 - diff)
    }

    private fun defaultHighwaysForSurface(surfaceType: SurfaceType): List<String> {
        return when (surfaceType) {
            SurfaceType.TRAIL -> listOf("footway", "path")
            SurfaceType.PARK -> listOf("footway", "path")
            SurfaceType.ROAD -> listOf("residential")
            SurfaceType.MIXED -> listOf("footway", "residential")
        }
    }



    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Extensions for db mapping
    private fun Route.toEntity() = RouteEntity(
        id = id,
        distanceKm = distanceKm,
        estimatedDurationMinutes = estimatedDurationMinutes,
        safetyScore = safetyScore,
        surfaceTypeName = surfaceType.name,
        elevationGainM = elevationGainM,
        createdAt = createdAt,
        waypoints = waypoints
    )

    private fun RouteEntity.toDomain() = Route(
        id = id,
        distanceKm = distanceKm,
        estimatedDurationMinutes = estimatedDurationMinutes,
        safetyScore = safetyScore,
        surfaceType = try { SurfaceType.valueOf(surfaceTypeName) } catch (e: Exception) { SurfaceType.MIXED },
        elevationGainM = elevationGainM,
        createdAt = createdAt,
        waypoints = waypoints
    )
}

typealias ContextLatLon = Double
