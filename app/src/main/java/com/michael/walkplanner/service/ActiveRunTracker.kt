package com.michael.walkplanner.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveRunStats(
    val sessionId: String,
    val paceDisplay: String,
    val distanceDisplay: String,
    val elapsedDisplay: String,
    val isPaused: Boolean
)

sealed interface ActiveRunServiceEvent {
    data object PauseToggle : ActiveRunServiceEvent
    data object Stop : ActiveRunServiceEvent
}

/**
 * Shared bridge between [ActiveRunViewModel] and [ActiveRunForegroundService].
 */
object ActiveRunTracker {
    private val _stats = MutableStateFlow<ActiveRunStats?>(null)
    val stats: StateFlow<ActiveRunStats?> = _stats.asStateFlow()

    private val _serviceEvents = MutableSharedFlow<ActiveRunServiceEvent>(extraBufferCapacity = 1)
    val serviceEvents: SharedFlow<ActiveRunServiceEvent> = _serviceEvents.asSharedFlow()

    fun updateStats(stats: ActiveRunStats) {
        _stats.value = stats
    }

    fun clear() {
        _stats.value = null
    }

    fun onPauseToggleFromNotification() {
        _serviceEvents.tryEmit(ActiveRunServiceEvent.PauseToggle)
    }

    fun onStopFromNotification() {
        _serviceEvents.tryEmit(ActiveRunServiceEvent.Stop)
    }
}
