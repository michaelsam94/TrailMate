package com.example.domain.usecase

import com.example.domain.model.ActiveSession
import com.example.domain.error.DomainError
import com.example.domain.repository.SessionSaveScheduler
import com.example.core.Result

class StopSessionUseCase(private val sessionSaveScheduler: SessionSaveScheduler) {
    suspend operator fun invoke(session: ActiveSession): Result<Unit, DomainError> {
        return try {
            val finishedSession = session.copy(
                endTime = System.currentTimeMillis(),
                isPaused = false
            )
            sessionSaveScheduler.scheduleSave(finishedSession)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(DomainError.Unknown(e))
        }
    }
}
