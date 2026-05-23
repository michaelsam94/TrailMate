package com.example.presentation.planroute

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
import android.content.Context
import com.example.TrailMateApplication
import com.example.core.util.NetworkUtils
import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.model.UserPrefs
import com.example.domain.error.DomainError
import com.example.domain.usecase.GenerateRouteUseCase
import com.example.domain.repository.UserPrefsRepository
import com.example.domain.repository.RouteRepository
import com.example.presentation.OsmMapView
import com.example.core.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.*

sealed interface PlanRouteUiState {
    object Idle : PlanRouteUiState
    object Loading : PlanRouteUiState
    data class Success(
        val routes: List<Route>,
        val usingCachedData: Boolean = false,
        val cacheAgeDays: Int = 0
    ) : PlanRouteUiState
    data class Error(val error: DomainError) : PlanRouteUiState
}

class PlanRouteViewModel(
    private val appContext: Context,
    private val generateRouteUseCase: GenerateRouteUseCase,
    private val userPrefsRepository: UserPrefsRepository,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanRouteUiState>(PlanRouteUiState.Idle)
    val uiState: StateFlow<PlanRouteUiState> = _uiState.asStateFlow()

    private val _userPrefs = MutableStateFlow(UserPrefs(SurfaceType.MIXED, true, 200.0, false))
    val userPrefs: StateFlow<UserPrefs> = _userPrefs.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefsRepository.userPrefsFlow.collectLatest { prefs ->
                _userPrefs.value = prefs
            }
        }
    }

    fun generatePlan(distanceKm: Double, surfaceType: SurfaceType) {
        viewModelScope.launch {
            val wasOffline = withContext(Dispatchers.IO) {
                !NetworkUtils.isOnline(appContext)
            }
            generateRouteUseCase(
                distanceKm = distanceKm,
                surfaceType = surfaceType,
                avoidHighways = _userPrefs.value.avoidHighways,
                maxElevationM = _userPrefs.value.maxElevationGainM
            ).collectLatest { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.value = PlanRouteUiState.Loading
                    }
                    is Result.Success -> {
                        val cacheAge = if (wasOffline) {
                            withContext(Dispatchers.IO) { routeRepository.getCacheAgeDays() }
                        } else {
                            0
                        }
                        _uiState.value = PlanRouteUiState.Success(
                            routes = result.data,
                            usingCachedData = wasOffline,
                            cacheAgeDays = cacheAge
                        )
                    }
                    is Result.Failure -> {
                        _uiState.value = PlanRouteUiState.Error(result.error)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPlanRouteViewModel(app: TrailMateApplication): PlanRouteViewModel {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlanRouteViewModel(
                    appContext = app.applicationContext,
                    generateRouteUseCase = app.generateRouteUseCase,
                    userPrefsRepository = app.userPrefsRepository,
                    routeRepository = app.routeRepository
                ) as T
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanRouteScreen(
    viewModel: PlanRouteViewModel,
    requestedDistance: Double,
    requestedSurface: SurfaceType,
    onBack: () -> Unit,
    onStartRoute: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs by viewModel.userPrefs.collectAsState()

    // Trigger API generation once the layout renders
    LaunchedEffect(requestedDistance, requestedSurface) {
        viewModel.generatePlan(requestedDistance, requestedSurface)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Suggestions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PlanRouteUiState.Idle, is PlanRouteUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing local path conditions...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is PlanRouteUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (state.error) {
                                is DomainError.PermissionDenied -> "Location Permission Denied"
                                is DomainError.LocationUnavailable -> "Location Unavailable"
                                is DomainError.NetworkUnavailable -> "Network Unreachable"
                                is DomainError.RouteGenerationFailed -> "No Street Routes Found"
                                else -> "Failed to Plan Routes"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (state.error) {
                                is DomainError.PermissionDenied -> "Location permission is required to plan routes. Please grant location access to TrailMate in your device settings."
                                is DomainError.LocationUnavailable -> "We couldn't retrieve your current location. Please verify that location services are enabled on your device."
                                is DomainError.NetworkUnavailable -> "No internet connection detected. Please check your network connection and try again."
                                is DomainError.RouteGenerationFailed -> "We couldn't build loop routes on nearby streets. Try a different distance or surface type, or move closer to walkable paths."
                                else -> "OpenStreetMap Overpass servers are currently unreachable. Please verify your connection and try again."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.generatePlan(requestedDistance, requestedSurface) }) {
                            Text("Retry Planning")
                        }
                    }
                }
                is PlanRouteUiState.Success -> {
                    val routes = state.routes
                    if (routes.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("No walking paths found in this radius.\nTry expanding search distance.")
                        }
                    } else {
                        var activeIndex by remember { mutableStateOf(0) }
                        val activeRoute = routes[activeIndex.coerceIn(0, routes.size - 1)]

                        // Map Rendering in relative background panel
                        val mapCenter = if (activeRoute.waypoints.isNotEmpty()) {
                            GeoPoint(activeRoute.waypoints.first().lat, activeRoute.waypoints.first().lng)
                        } else {
                            GeoPoint(51.5074, -0.1278) // default center
                        }

                        val mapPoints = activeRoute.waypoints.map { GeoPoint(it.lat, it.lng) }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Map occupies upper 55%
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                            ) {
                                OsmMapView(
                                    center = mapCenter,
                                    routePoints = mapPoints,
                                    enableOffset = true,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (state.usingCachedData) {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(
                                                text = if (state.cacheAgeDays > 0) {
                                                    "Using cached data (${state.cacheAgeDays} days old)"
                                                } else {
                                                    "Offline — using cached map data"
                                                },
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(16.dp)
                                    )
                                }

                                // Floating bubble to switch through choices
                                FloatingActionButton(
                                    onClick = {
                                        activeIndex = (activeIndex + 1) % routes.size
                                    },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.AltRoute, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Next Option (${activeIndex + 1}/${routes.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Stats sheet occupies lower 45%
                            Card(
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.0f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Option ${activeIndex + 1} (${calculateRouteDirection(activeRoute)}): ${activeRoute.surfaceType.name.lowercase().capitalize(Locale.ROOT)} Trace",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            // Safety Rating Chip
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                                )
                                            ) {
                                                Text(
                                                    text = "Safety Score: ${Math.round(activeRoute.safetyScore * 100)}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Stats Row
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val statsDistanceValue = if (prefs.useImperialUnits) {
                                                "${String.format(Locale.getDefault(), "%.2f", activeRoute.distanceKm * 0.621371)} mi"
                                            } else {
                                                "${activeRoute.distanceKm} km"
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text(statsDistanceValue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Column(
                                                modifier = Modifier.weight(1.2f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("Est. Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text("${activeRoute.estimatedDurationMinutes} mins", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Column(
                                                modifier = Modifier.weight(1.1f),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                Text("Elevation Gain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text("${Math.round(activeRoute.elevationGainM)} m", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Brief summary helper
                                        Text(
                                            text = "This circular loop starts and ends at your current location and utilizes mostly highly populated pedestrian conditions for optimal safety support.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { onStartRoute(activeRoute.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Start Tracking Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateRouteDirection(route: Route): String {
    val waypoints = route.waypoints
    if (waypoints.size < 3) return "Unknown"
    val start = waypoints.first()
    
    var maxDist = 0.0
    var furthest = start
    val latRad = start.lat * PI / 180.0
    val cosLat = cos(latRad)
    
    for (wp in waypoints) {
        val dLat = wp.lat - start.lat
        val dLngScaled = (wp.lng - start.lng) * cosLat
        val distSq = dLat * dLat + dLngScaled * dLngScaled
        if (distSq > maxDist) {
            maxDist = distSq
            furthest = wp
        }
    }
    
    val dLat = furthest.lat - start.lat
    val dLngScaled = (furthest.lng - start.lng) * cosLat
    val angleRad = atan2(dLat, dLngScaled)
    var angleDeg = angleRad * 180.0 / PI
    if (angleDeg < 0) {
        angleDeg += 360.0
    }
    val sector = (((angleDeg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
    return when (sector) {
        0 -> "East"
        1 -> "Northeast"
        2 -> "North"
        3 -> "Northwest"
        4 -> "West"
        5 -> "Southwest"
        6 -> "South"
        7 -> "Southeast"
        else -> "East"
    }
}
