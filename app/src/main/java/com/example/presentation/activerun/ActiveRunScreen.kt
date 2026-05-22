package com.example.presentation.activerun

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.TrailMateApplication
import com.example.domain.model.ActiveSession
import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.model.UserPrefs
import com.example.domain.model.Waypoint
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.RouteRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.repository.UserPrefsRepository
import com.example.domain.usecase.StopSessionUseCase
import com.example.presentation.OsmMapView
import com.example.core.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.UUID
import java.util.Locale
import kotlin.math.*

class ActiveRunViewModel(
    private val routeId: String,
    private val locationRepository: LocationRepository,
    private val routeRepository: RouteRepository,
    private val sessionRepository: SessionRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val stopSessionUseCase: StopSessionUseCase
) : ViewModel() {

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _userPrefs = MutableStateFlow(UserPrefs(SurfaceType.MIXED, true, 200.0, false))
    val userPrefs: StateFlow<UserPrefs> = _userPrefs.asStateFlow()

    private val _trackingSession = MutableStateFlow<ActiveSession?>(null)
    val trackingSession: StateFlow<ActiveSession?> = _trackingSession.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null

    init {
        // Fetch User Prefs
        viewModelScope.launch {
            userPrefsRepository.userPrefsFlow.collectLatest { prefs ->
                _userPrefs.value = prefs
            }
        }

        // Fetch Route details
        viewModelScope.launch {
            val dbRoute = routeRepository.getRouteById(routeId)
            _route.value = dbRoute

            // Initialize active session
            val initialWaypoint = dbRoute?.waypoints?.firstOrNull() ?: Waypoint(51.5074, -0.1278)
            _trackingSession.value = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                routeId = routeId,
                startTime = System.currentTimeMillis(),
                liveWaypoints = listOf(initialWaypoint),
                currentPaceMinPerKm = 0.0,
                distanceCoveredKm = 0.0,
                isPaused = false
            )

            startTracking()
        }
    }

    private fun startTracking() {
        startTimer()
        collectLocations()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val session = _trackingSession.value
                if (session != null && !session.isPaused) {
                    _elapsedSeconds.value += 1
                }
            }
        }
    }

    private fun collectLocations() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            // Poll location every 2 seconds during active tracking
            locationRepository.locationUpdates(2000L).collectLatest { result ->
                val session = _trackingSession.value ?: return@collectLatest
                if (session.isPaused) return@collectLatest

                if (result is Result.Success) {
                    val newPoint = result.data
                    val currentList = session.liveWaypoints.toMutableList()
                    
                    var distDelta = 0.0
                    if (currentList.isNotEmpty()) {
                        val lastPoint = currentList.last()
                        distDelta = calculateDistance(lastPoint.lat, lastPoint.lng, newPoint.lat, newPoint.lng)
                    }
                    currentList.add(newPoint)

                    val newDistance = session.distanceCoveredKm + distDelta

                    // Pace calc: elapsed minutes / distance covered
                    val elapsedMinutes = _elapsedSeconds.value / 60.0
                    val currentPace = if (newDistance > 0.0) {
                        elapsedMinutes / newDistance
                    } else {
                        0.0
                    }

                    // Calorie estimate: 60 kcal per km
                    val estCalories = (newDistance * 60.0).toInt()

                    _trackingSession.value = session.copy(
                        liveWaypoints = currentList,
                        distanceCoveredKm = round(newDistance * 100) / 100.0,
                        currentPaceMinPerKm = round(currentPace * 100) / 100.0,
                        caloriesBurned = estCalories,
                        elevationGainM = round((session.elevationGainM + max(0.0, newPoint.elevationM - (currentList.getOrNull(currentList.size - 2)?.elevationM ?: 0.0))) * 10) / 10.0
                    )
                }
            }
        }
    }

    fun togglePause() {
        val session = _trackingSession.value ?: return
        val currentPauseState = session.isPaused
        _trackingSession.value = session.copy(isPaused = !currentPauseState)
    }

    fun stopSession(onComplete: () -> Unit) {
        val session = _trackingSession.value ?: return
        viewModelScope.launch {
            stopSessionUseCase(session)
            // Cleanup tracking updates
            timerJob?.cancel()
            locationJob?.cancel()
            onComplete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        locationJob?.cancel()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

@Composable
fun rememberActiveRunViewModel(app: TrailMateApplication, routeId: String): ActiveRunViewModel {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActiveRunViewModel(
                    routeId = routeId,
                    locationRepository = app.locationRepository,
                    routeRepository = app.routeRepository,
                    sessionRepository = app.sessionRepository,
                    userPrefsRepository = app.userPrefsRepository,
                    stopSessionUseCase = app.stopSessionUseCase
                ) as T
            }
        }
    )
}

@Composable
fun ActiveRunScreen(
    viewModel: ActiveRunViewModel,
    onCompleteSession: () -> Unit
) {
    val route by viewModel.route.collectAsState()
    val session by viewModel.trackingSession.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val prefs by viewModel.userPrefs.collectAsState()

    var showStopDialog by remember { mutableStateOf(false) }

    // Dialog when stopping run
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Complete Session?", fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to stop tracking and save this running/walking session detail to your track history?") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        viewModel.stopSession(onCompleteSession)
                    }
                ) {
                    Text("Save & Complete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopDialog = false }) {
                    Text("Resume Run")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val liveSession = session
            if (liveSession == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val mapPoints = liveSession.liveWaypoints.map { GeoPoint(it.lat, it.lng) }
                val currentLatLng = mapPoints.lastOrNull() ?: GeoPoint(51.5074, -0.1278)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Map filling the upper portion
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                    ) {
                        OsmMapView(
                            center = currentLatLng,
                            routePoints = mapPoints,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Ticker widget overlay
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                            ),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = formatDuration(elapsedSeconds),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Panel for statistics gauges and buttons
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.9f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // High contrast metric grid
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val currentDistanceText = if (prefs.useImperialUnits) {
                                        "${String.format(Locale.getDefault(), "%.2f", liveSession.distanceCoveredKm * 0.621371)} mi"
                                    } else {
                                        "${liveSession.distanceCoveredKm} km"
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(currentDistanceText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        val currentPaceText = if (prefs.useImperialUnits) {
                                            "${String.format(Locale.getDefault(), "%.2f", liveSession.currentPaceMinPerKm * 1.60934)} min/mi"
                                        } else {
                                            "${liveSession.currentPaceMinPerKm} min/km"
                                        }
                                        Text("Pace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(currentPaceText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("Calorie Estimate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text("${liveSession.caloriesBurned} kcal", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("Elevation Gain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text("${liveSession.elevationGainM} m", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            // Navigation operations
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.togglePause() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (liveSession.isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (liveSession.isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                ) {
                                    Icon(
                                        imageVector = if (liveSession.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (liveSession.isPaused) "Resume" else "Pause",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Button(
                                    onClick = { showStopDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
