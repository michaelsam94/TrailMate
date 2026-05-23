package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.repository.SessionRepository
import com.michael.walkplanner.core.Result
import kotlinx.coroutines.flow.Flow

class GetSessionHistoryUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<Result<List<ActiveSession>, DomainError>> {
        return sessionRepository.getSessionHistory()
    }
}
