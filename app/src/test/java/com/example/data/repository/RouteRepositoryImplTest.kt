package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.core.Result
import com.example.data.local.db.OsmCacheDao
import com.example.data.local.db.RouteDao
import com.example.data.remote.api.OverpassService
import com.example.domain.error.DomainError
import com.example.domain.model.SurfaceType
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
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private lateinit var repository: RouteRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        setOffline()
        repository = RouteRepositoryImpl(context, routeDao, osmCacheDao, overpassService)
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
            com.example.data.remote.dto.OverpassResponse(elements = emptyList())
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
}
