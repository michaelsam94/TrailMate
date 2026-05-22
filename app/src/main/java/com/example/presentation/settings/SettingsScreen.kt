package com.example.presentation.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.TrailMateApplication
import com.example.domain.model.SurfaceType
import com.example.domain.model.UserPrefs
import com.example.domain.repository.RouteRepository
import com.example.domain.repository.UserPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(
    private val userPrefsRepository: UserPrefsRepository,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val _userPrefs = MutableStateFlow(UserPrefs(SurfaceType.MIXED, true, 200.0, false))
    val userPrefs: StateFlow<UserPrefs> = _userPrefs.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefsRepository.userPrefsFlow.collectLatest { prefs ->
                _userPrefs.value = prefs
            }
        }
    }

    fun updatePrefs(updated: UserPrefs) {
        viewModelScope.launch {
            userPrefsRepository.updateUserPrefs(updated)
        }
    }

    fun clearCache(onDeleted: () -> Unit) {
        viewModelScope.launch {
            routeRepository.clearExpiredCache()
            onDeleted()
        }
    }
}

@Composable
fun rememberSettingsViewModel(app: TrailMateApplication): SettingsViewModel {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    userPrefsRepository = app.userPrefsRepository,
                    routeRepository = app.routeRepository
                ) as T
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val prefs by viewModel.userPrefs.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Unit preference
            Text("General Preferences", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updatePrefs(prefs.copy(useImperialUnits = !prefs.useImperialUnits))
                    }
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Imperial Units", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Measure distance in miles instead of kilometers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = prefs.useImperialUnits,
                    onCheckedChange = { viewModel.updatePrefs(prefs.copy(useImperialUnits = it)) }
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Surface preferences
            Text("Route Generation Constraints", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Default Preferred Surface", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SurfaceType.values().forEach { type ->
                        FilterChip(
                            selected = prefs.preferredSurface == type,
                            onClick = {
                                viewModel.updatePrefs(prefs.copy(preferredSurface = type))
                            },
                            label = { Text(type.name.lowercase().capitalize(Locale.ROOT)) }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updatePrefs(prefs.copy(avoidHighways = !prefs.avoidHighways))
                    }
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avoid Major Highways", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Restrict route matching to paths, trails and residential streets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = prefs.avoidHighways,
                    onCheckedChange = { viewModel.updatePrefs(prefs.copy(avoidHighways = it)) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Max Elevation Gain Limit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("${prefs.maxElevationGainM.toInt()} m", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("Exclude steep hills or mountain climbing structures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Slider(
                    value = prefs.maxElevationGainM.toFloat(),
                    onValueChange = {
                        viewModel.updatePrefs(prefs.copy(maxElevationGainM = Math.round(it / 50.0) * 50.0))
                    },
                    valueRange = 0f..500f,
                    steps = 9
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Cache & Offline Management
            Text("Offline & Cache Maintenance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            Button(
                onClick = {
                    viewModel.clearCache {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Local route suggestions and map cached elements cleared successfully!"
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Cached, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Route Cache", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
