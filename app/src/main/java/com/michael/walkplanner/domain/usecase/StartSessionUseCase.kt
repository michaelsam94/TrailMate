package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.model.Waypoint
import java.util.UUID

class StartSessionUseCase {
    operator fun invoke(routeId: String, startLocation: Waypoint): ActiveSession {
        return ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            routeId = routeId,
            startTime = System.currentTimeMillis(),
            liveWaypoints = listOf(startLocation),
            currentPaceMinPerKm = 0.0,
            distanceCoveredKm = 0.0,
            isPaused = false
        )
    }
}
