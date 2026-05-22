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
import com.example.TrailMateApplication
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
import org.osmdroid.util.GeoPoint
import java.util.Locale

sealed interface PlanRouteUiState {
    object Idle : PlanRouteUiState
    object Loading : PlanRouteUiState
    data class Success(val routes: List<Route>) : PlanRouteUiState
    data class Error(val error: DomainError) : PlanRouteUiState
}

class PlanRouteViewModel(
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
                        _uiState.value = PlanRouteUiState.Success(result.data)
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
                            text = "Failed to plan routes.",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OpenStreetMap Overpass servers are currently unreachable. Please verify your connection and try again.",
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
                                    modifier = Modifier.fillMaxSize()
                                )

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
                                                text = "Option ${activeIndex + 1}: ${activeRoute.surfaceType.name.lowercase().capitalize(Locale.ROOT)} Trace",
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

                                            Column {
                                                Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text(statsDistanceValue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Column {
                                                Text("Est. Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text("${activeRoute.estimatedDurationMinutes} mins", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Column {
                                                Text("Elevation Gain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Text("${activeRoute.elevationGainM} m", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
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
