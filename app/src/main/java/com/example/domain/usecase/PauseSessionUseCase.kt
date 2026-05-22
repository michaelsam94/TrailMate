package com.example.domain.usecase

import com.example.domain.model.ActiveSession

class PauseSessionUseCase {
    operator fun invoke(session: ActiveSession): ActiveSession {
        return session.copy(isPaused = !session.isPaused) // Toggles pause state
    }
}
