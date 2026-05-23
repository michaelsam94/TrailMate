package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.repository.SessionSaveScheduler
import com.michael.walkplanner.core.Result

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
