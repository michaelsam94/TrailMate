package com.michael.walkplanner.domain.usecase

import app.cash.turbine.test
import com.michael.walkplanner.core.Result
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.Waypoint
import com.michael.walkplanner.domain.repository.LocationRepository
import com.michael.walkplanner.domain.repository.RouteRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateRouteUseCaseTest {

    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val routeRepository = mockk<RouteRepository>(relaxed = true)
    private val useCase = GenerateRouteUseCase(locationRepository, routeRepository)

    private val fakeRoute = Route(
        id = "r1",
        waypoints = listOf(Waypoint(51.5, -0.12)),
        distanceKm = 5.0,
        estimatedDurationMinutes = 30,
        safetyScore = 0.9f,
        surfaceType = SurfaceType.MIXED,
        elevationGainM = 10.0
    )

    @Test
    fun `returns routes when GPS fix available and distance is valid`() = runTest {
        coEvery { locationRepository.lastKnownLocation() } returns Result.Success(Waypoint(51.5, -0.12))
        coEvery {
            routeRepository.generateRoute(any(), any(), any(), any(), any(), any())
        } returns flowOf(Result.Success(listOf(fakeRoute)))

        useCase(5.0, SurfaceType.MIXED, avoidHighways = true, maxElevationM = 200.0).test {
            assertTrue(awaitItem() is Result.Loading)
            val success = awaitItem()
            assertTrue(success is Result.Success)
            assertEquals(1, (success as Result.Success).data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns LocationUnavailable when GPS returns failure`() = runTest {
        coEvery { locationRepository.lastKnownLocation() } returns Result.Failure(DomainError.LocationUnavailable)

        useCase(5.0, SurfaceType.MIXED, true, 200.0).test {
            awaitItem() // Loading
            val result = awaitItem()
            assertTrue(result is Result.Failure)
            assertEquals(DomainError.LocationUnavailable, (result as Result.Failure).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns DistanceOutOfRange when distance below minimum`() = runTest {
        useCase(0.4, SurfaceType.MIXED, true, 200.0).test {
            awaitItem()
            val result = awaitItem()
            assertTrue(result is Result.Failure)
            assertTrue((result as Result.Failure).error is DomainError.DistanceOutOfRange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns DistanceOutOfRange when distance above maximum`() = runTest {
        useCase(50.1, SurfaceType.MIXED, true, 200.0).test {
            awaitItem()
            val result = awaitItem()
            assertTrue(result is Result.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits loading state before result state`() = runTest {
        coEvery { locationRepository.lastKnownLocation() } returns Result.Success(Waypoint(51.5, -0.12))
        coEvery {
            routeRepository.generateRoute(any(), any(), any(), any(), any(), any())
        } returns flowOf(Result.Success(listOf(fakeRoute)))

        useCase(5.0, SurfaceType.MIXED, true, 200.0).test {
            assertTrue(awaitItem() is Result.Loading)
            assertTrue(awaitItem() is Result.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `passes avoidHighways to route repository`() = runTest {
        coEvery { locationRepository.lastKnownLocation() } returns Result.Success(Waypoint(51.5, -0.12))
        coEvery {
            routeRepository.generateRoute(any(), any(), any(), any(), avoidHighways = true, any())
        } returns flowOf(Result.Success(listOf(fakeRoute)))

        useCase(5.0, SurfaceType.TRAIL, avoidHighways = true, maxElevationM = 200.0).test {
            skipItems(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns top scored routes from repository`() = runTest {
        val routes = listOf(
            fakeRoute.copy(id = "a", safetyScore = 0.95f),
            fakeRoute.copy(id = "b", safetyScore = 0.80f),
            fakeRoute.copy(id = "c", safetyScore = 0.70f)
        )
        coEvery { locationRepository.lastKnownLocation() } returns Result.Success(Waypoint(51.5, -0.12))
        coEvery {
            routeRepository.generateRoute(any(), any(), any(), any(), any(), any())
        } returns flowOf(Result.Success(routes))

        useCase(5.0, SurfaceType.MIXED, true, 200.0).test {
            skipItems(1)
            val result = awaitItem() as Result.Success
            assertEquals(3, result.data.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
