package com.michael.walkplanner.service

import com.michael.walkplanner.domain.model.ActiveSession
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

/**
 * Shared bridge between [ActiveRunViewModel], [ActiveRunForegroundService],
 * and [ActiveRunNotificationReceiver].
 */
object ActiveRunTracker {
    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    private val _stats = MutableStateFlow<ActiveRunStats?>(null)
    val stats: StateFlow<ActiveRunStats?> = _stats.asStateFlow()

    private val _sessionEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionEnded: SharedFlow<Unit> = _sessionEnded.asSharedFlow()

    fun updateSession(session: ActiveSession, stats: ActiveRunStats) {
        _session.value = session
        _stats.value = stats
    }

    fun getCurrentSession(): ActiveSession? = _session.value

    fun togglePauseFromNotification() {
        val session = _session.value ?: return
        val updated = session.copy(isPaused = !session.isPaused)
        _session.value = updated
        _stats.value = _stats.value?.copy(isPaused = updated.isPaused)
    }

    fun clear() {
        _session.value = null
        _stats.value = null
    }

    fun notifySessionEnded() {
        _sessionEnded.tryEmit(Unit)
    }
}
