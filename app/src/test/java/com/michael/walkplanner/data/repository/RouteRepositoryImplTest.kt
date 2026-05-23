package com.michael.walkplanner.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.michael.walkplanner.core.Result
import com.michael.walkplanner.data.local.db.NodeEntity
import com.michael.walkplanner.data.local.db.OsmCacheDao
import com.michael.walkplanner.data.local.db.RouteDao
import com.michael.walkplanner.data.local.db.WayEntity
import com.michael.walkplanner.data.remote.api.OverpassService
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.data.routing.LoopRouteGenerator
import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.Waypoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RouteRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val routeDao = mockk<RouteDao>(relaxed = true)
    private val osmCacheDao = mockk<OsmCacheDao>(relaxed = true)
    private val overpassService = mockk<OverpassService>(relaxed = true)
    private val loopRouteGenerator = mockk<LoopRouteGenerator>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private lateinit var repository: RouteRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        setOffline()
        repository = RouteRepositoryImpl(context, routeDao, osmCacheDao, overpassService, loopRouteGenerator)
    }

    private fun setOffline() {
        every { connectivityManager.activeNetwork } returns null
    }

    private fun setOnline() {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
    }

    @Test
    fun `clears cache entries older than 7 days on next query`() = runTest {
        coEvery { osmCacheDao.getAllWays() } returns emptyList()

        repository.generateRoute(
            lat = 51.5,
            lng = -0.12,
            distanceKm = 5.0,
            surfaceType = SurfaceType.MIXED,
            avoidHighways = true,
            maxElevationM = 200.0
        ).toList()

        coVerify { routeDao.deleteOldRoutes(any()) }
        coVerify { osmCacheDao.deleteOldNodes(any()) }
        coVerify { osmCacheDao.deleteOldWays(any()) }
    }

    @Test
    fun `returns NetworkUnavailable when offline and cache empty`() = runTest {
        coEvery { osmCacheDao.getAllWays() } returns emptyList()

        val results = repository.generateRoute(
            lat = 51.5,
            lng = -0.12,
            distanceKm = 5.0,
            surfaceType = SurfaceType.MIXED,
            avoidHighways = false,
            maxElevationM = 200.0
        ).toList()

        val finalResult = results.last()
        assertTrue(finalResult is Result.Failure)
        assertTrue((finalResult as Result.Failure).error is DomainError.NetworkUnavailable)
    }

    @Test
    fun `returns RouteGenerationFailed when OSM returns empty ways`() = runTest {
        setOnline()
        coEvery { overpassService.queryOverpass(any(), any()) } returns
            com.michael.walkplanner.data.remote.dto.OverpassResponse(elements = emptyList())
        coEvery { osmCacheDao.getAllWays() } returns emptyList()

        val results = repository.generateRoute(
            lat = 51.5,
            lng = -0.12,
            distanceKm = 5.0,
            surfaceType = SurfaceType.MIXED,
            avoidHighways = false,
            maxElevationM = 200.0
        ).toList()

        val finalResult = results.last()
        assertTrue(finalResult is Result.Failure)
        assertTrue((finalResult as Result.Failure).error is DomainError.RouteGenerationFailed)
    }

    @Test
    fun `clearExpiredCache deletes old records`() = runTest {
        val result = repository.clearExpiredCache()
        assertTrue(result is Result.Success)
        coVerify { routeDao.deleteOldRoutes(any()) }
    }

    @Test
    fun `returns cached routes from loop generator when offline cache is populated`() = runTest {
        val cachedRoutes = listOf(
            Route(
                id = "a",
                waypoints = listOf(Waypoint(24.7, 46.6)),
                distanceKm = 3.2,
                estimatedDurationMinutes = 20,
                safetyScore = 0.75f,
                surfaceType = SurfaceType.MIXED,
                elevationGainM = 10.0
            ),
            Route(
                id = "b",
                waypoints = listOf(Waypoint(24.71, 46.61)),
                distanceKm = 3.2,
                estimatedDurationMinutes = 21,
                safetyScore = 0.70f,
                surfaceType = SurfaceType.MIXED,
                elevationGainM = 12.0
            )
        )
        coEvery {
            loopRouteGenerator.generateLoops(any(), any(), any(), any(), any())
        } returns cachedRoutes

        val blockKm = 0.35
        val originLat = 24.7
        val originLng = 46.6
        val nodes = mutableListOf<NodeEntity>()
        val ways = mutableListOf<WayEntity>()
        var nodeId = 1L
        var wayId = 1L

        for (row in 0..4) {
            for (col in 0..4) {
                nodes += NodeEntity(
                    id = nodeId,
                    lat = originLat + row * blockKm / 111.0,
                    lng = originLng + col * blockKm / (111.0 * kotlin.math.cos(Math.toRadians(originLat)))
                )
                nodeId++
            }
        }

        fun nodeAt(row: Int, col: Int) = (row * 5 + col + 1).toLong()

        for (row in 0..4) {
            for (col in 0..4) {
                if (col < 4) {
                    ways += WayEntity(
                        id = wayId++,
                        surfaceType = "residential",
                        nodeIds = "${nodeAt(row, col)},${nodeAt(row, col + 1)}"
                    )
                }
                if (row < 4) {
                    ways += WayEntity(
                        id = wayId++,
                        surfaceType = "residential",
                        nodeIds = "${nodeAt(row, col)},${nodeAt(row + 1, col)}"
                    )
                }
            }
        }

        coEvery { osmCacheDao.getAllNodes() } returns nodes
        coEvery { osmCacheDao.getAllWays() } returns ways

        val results = repository.generateRoute(
            lat = nodes[12].lat,
            lng = nodes[12].lng,
            distanceKm = 3.2,
            surfaceType = SurfaceType.MIXED,
            avoidHighways = false,
            maxElevationM = 200.0
        ).toList()

        val success = results.filterIsInstance<Result.Success<List<Route>>>().lastOrNull()
        assertTrue(success != null, "Expected route generation success")
        assertTrue((success as Result.Success).data.size == 2)
    }
}
