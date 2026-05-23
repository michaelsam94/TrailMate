package com.michael.walkplanner.data.repository

import android.content.Context
import com.michael.walkplanner.data.local.db.*
import com.michael.walkplanner.data.remote.api.OverpassService
import com.michael.walkplanner.data.remote.dto.OverpassResponse
import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.Waypoint
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.repository.RouteRepository
import com.michael.walkplanner.data.routing.LoopRouteGenerator
import com.michael.walkplanner.data.routing.StreetGraph
import com.michael.walkplanner.core.util.NetworkUtils
import com.michael.walkplanner.core.Result
import com.michael.walkplanner.core.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.michael.walkplanner.data.routing.haversineDistanceM
import com.michael.walkplanner.data.routing.haversineKm
import com.michael.walkplanner.data.routing.highwaySafetyWeight
import com.michael.walkplanner.data.routing.GraphEdge

class RouteRepositoryImpl(
    private val context: Context,
    private val routeDao: RouteDao,
    private val osmCacheDao: OsmCacheDao,
    private val overpassService: OverpassService,
    private val loopRouteGenerator: LoopRouteGenerator = LoopRouteGenerator()
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

        // Fast path: reuse nearby cached OSM data when still fresh (avoids slow Overpass round-trip).
        val cacheAgeDays = getCacheAgeDays()
        val maxRouteDistanceError = distanceKm * Constants.ROUTE_DISTANCE_TOLERANCE_PERCENT
        if (cacheAgeDays < Constants.CACHE_EXPIRY_DAYS) {
            val cachedGraph = buildStreetGraphFromCache(surfaceType)
            if (cachedGraph != null && hasCachedDataNear(lat, lng, cachedGraph)) {
                val cachedRoutes = withContext(Dispatchers.Default) {
                    findStreetLoopRoutes(cachedGraph, lat, lng, distanceKm, surfaceType)
                }
                if (cachedRoutes.isNotEmpty() &&
                    cachedRoutes.all { abs(it.distanceKm - distanceKm) <= maxRouteDistanceError }
                ) {
                    routeDao.insertAllRoutes(cachedRoutes.map { it.toEntity() })
                    emit(Result.Success(cachedRoutes))
                    return@flow
                }
            }
        }

        val isOnline = NetworkUtils.isOnline(context)
        val radiusM = (distanceKm * 900.0).coerceIn(2500.0, 8000.0)
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
            android.util.Log.d("WalkPlanner", "Querying Overpass API at endpoint: $endpoint")
            try {
                response = overpassService.queryOverpass(endpoint, query)
                success = true
                android.util.Log.d("WalkPlanner", "Overpass response success: $success, elements count: ${response.elements?.size}")
                break
            } catch (e: Exception) {
                exception = e
                android.util.Log.e("WalkPlanner", "Overpass query failed at endpoint $endpoint", e)
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
            android.util.Log.e("WalkPlanner", "Failed to build routes from cached OSM data", e)
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
        nodesMap: Map<Long, com.michael.walkplanner.data.remote.dto.OverpassElement>,
        ways: List<com.michael.walkplanner.data.remote.dto.OverpassElement>,
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
            val nodeIds = way.nodes?.filter { nodes.containsKey(it) }?.takeIf { it.size >= 2 } ?: return@mapNotNull null
            val highway = way.tags?.get("highway") ?: way.tags?.get("leisure") ?: "path"
            nodeIds to highway
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
                nodeIds to way.surfaceType
            } else {
                null
            }
        }.ifEmpty {
            wayEntities.mapNotNull { way ->
                val nodeIds = way.nodeIds.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .filter { nodes.containsKey(it) }
                nodeIds.takeIf { it.size >= 2 }?.let { it to way.surfaceType }
            }
        }

        return buildStreetGraph(nodes, wayNodeLists)
    }

    private fun buildStreetGraph(
        nodes: Map<Long, Waypoint>,
        ways: List<Pair<List<Long>, String>>
    ): StreetGraph? {
        if (nodes.isEmpty() || ways.isEmpty()) return null

        val adjacencyList = mutableMapOf<Long, MutableList<GraphEdge>>()
        for ((nodeIds, highway) in ways) {
            val safetyWeight = highwaySafetyWeight(highway)
            for (i in 0 until nodeIds.size - 1) {
                val u = nodeIds[i]
                val v = nodeIds[i + 1]
                val nodeU = nodes[u] ?: continue
                val nodeV = nodes[v] ?: continue
                val distanceM = haversineDistanceM(nodeU.lat, nodeU.lng, nodeV.lat, nodeV.lng)
                addUndirectedEdge(adjacencyList, u, v, distanceM, safetyWeight)
            }
        }

        if (adjacencyList.isEmpty()) return null
        return StreetGraph(nodes, adjacencyList)
    }

    private fun addUndirectedEdge(
        adjacencyList: MutableMap<Long, MutableList<GraphEdge>>,
        u: Long,
        v: Long,
        distanceM: Double,
        safetyWeight: Float
    ) {
        adjacencyList.getOrPut(u) { mutableListOf() }.add(GraphEdge(v, distanceM, safetyWeight))
        adjacencyList.getOrPut(v) { mutableListOf() }.add(GraphEdge(u, distanceM, safetyWeight))
    }

    private fun hasCachedDataNear(lat: Double, lng: Double, graph: StreetGraph, radiusKm: Double = 1.5): Boolean {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return graph.nodes.values.any { wp ->
            abs(wp.lat - lat) <= latDelta &&
                abs(wp.lng - lng) <= lngDelta &&
                haversineKm(lat, lng, wp.lat, wp.lng) <= radiusKm
        }
    }

    private fun findStreetLoopRoutes(
        graph: StreetGraph,
        centerLat: Double,
        centerLng: Double,
        targetDistanceKm: Double,
        surfaceType: SurfaceType
    ): List<Route> = loopRouteGenerator.generateLoops(
        graph = graph,
        centerLat = centerLat,
        centerLng = centerLng,
        targetDistanceKm = targetDistanceKm,
        surfaceType = surfaceType
    )

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
