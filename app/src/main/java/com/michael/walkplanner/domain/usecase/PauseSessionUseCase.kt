package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.ActiveSession

class PauseSessionUseCase {
    operator fun invoke(session: ActiveSession): ActiveSession {
        return session.copy(isPaused = !session.isPaused) // Toggles pause state
    }
}
