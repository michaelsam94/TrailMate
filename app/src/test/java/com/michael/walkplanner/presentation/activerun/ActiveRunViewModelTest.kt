package com.michael.walkplanner.presentation.activerun

import androidx.lifecycle.ViewModel
import com.michael.walkplanner.core.Result as AppResult
import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.model.UserPrefs
import com.michael.walkplanner.domain.model.Waypoint
import com.michael.walkplanner.domain.repository.LocationRepository
import com.michael.walkplanner.domain.repository.RouteRepository
import com.michael.walkplanner.domain.repository.SessionRepository
import com.michael.walkplanner.domain.repository.UserPrefsRepository
import com.michael.walkplanner.domain.usecase.StopSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveRunViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val locationFlow = MutableSharedFlow<AppResult<Waypoint, com.michael.walkplanner.domain.error.DomainError>>(extraBufferCapacity = 10)

    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val routeRepository = mockk<RouteRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val userPrefsRepository = mockk<UserPrefsRepository>(relaxed = true)
    private val stopSessionUseCase = mockk<StopSessionUseCase>(relaxed = true)
    private val appContext = mockk<android.content.Context>(relaxed = true)

    private var viewModelUnderTest: ActiveRunViewModel? = null

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepository.userPrefsFlow } returns flowOf(
            UserPrefs(SurfaceType.MIXED, true, 200.0, false)
        )
        every { locationRepository.locationUpdates(any()) } returns locationFlow
        coEvery { routeRepository.getRouteById(any()) } returns Route(
            id = "route-1",
            waypoints = listOf(Waypoint(51.5, -0.12)),
            distanceKm = 5.0,
            estimatedDurationMinutes = 30,
            safetyScore = 0.9f,
            surfaceType = SurfaceType.MIXED,
            elevationGainM = 10.0
        )
        coEvery { stopSessionUseCase(any()) } returns AppResult.Success(Unit)
    }

    @AfterEach
    fun tearDown() {
        clearViewModel()
        Dispatchers.resetMain()
    }

    private fun clearViewModel() {
        viewModelUnderTest?.let { vm ->
            val method = ViewModel::class.java.getDeclaredMethod("onCleared")
            method.isAccessible = true
            method.invoke(vm)
        }
        viewModelUnderTest = null
    }

    private fun createViewModel(): ActiveRunViewModel {
        return ActiveRunViewModel(
            appContext = appContext,
            routeId = "route-1",
            locationRepository = locationRepository,
            routeRepository = routeRepository,
            sessionRepository = sessionRepository,
            userPrefsRepository = userPrefsRepository,
            stopSessionUseCase = stopSessionUseCase,
            ioDispatcher = testDispatcher
        ).also { viewModelUnderTest = it }
    }

    @Test
    fun `updates liveWaypoints on GPS location emission`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        locationFlow.emit(AppResult.Success(Waypoint(51.501, -0.121)))
        advanceTimeBy(100)
        assertEquals(1, viewModel.trackingSession.value?.liveWaypoints?.size)
        clearViewModel()
    }

    @Test
    fun `calculates pace after receiving movement waypoints`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        locationFlow.emit(AppResult.Success(Waypoint(51.500, -0.120)))
        advanceTimeBy(16_000)
        locationFlow.emit(AppResult.Success(Waypoint(51.520, -0.120)))
        advanceTimeBy(100)
        assertTrue((viewModel.trackingSession.value?.currentPaceMinPerKm ?: 0.0) > 0.0)
        clearViewModel()
    }

    @Test
    fun `transitions to paused state when pause toggled`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        viewModel.togglePause()
        assertTrue(viewModel.trackingSession.value?.isPaused == true)
        clearViewModel()
    }

    @Test
    fun `transitions back to active state when resume toggled`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        viewModel.togglePause()
        viewModel.togglePause()
        assertFalse(viewModel.trackingSession.value?.isPaused == true)
        clearViewModel()
    }

    @Test
    fun `persists completed session on finish`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        viewModel.stopSession {}
        advanceTimeBy(100)
        coVerify { stopSessionUseCase(any()) }
        clearViewModel()
    }

    @Test
    fun `emits elapsed time updates every second via ticker`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceTimeBy(100)
        advanceTimeBy(3_100)
        assertTrue(viewModel.elapsedSeconds.value >= 2)
        clearViewModel()
    }
}
