package com.example.domain.usecase

import com.example.domain.model.ActiveSession
import com.example.domain.error.DomainError
import com.example.domain.repository.SessionRepository
import com.example.core.Result

class StopSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(session: ActiveSession): Result<Unit, DomainError> {
        val finishedSession = session.copy(
            endTime = System.currentTimeMillis(),
            isPaused = false
        )
        return sessionRepository.saveSession(finishedSession)
    }
}
