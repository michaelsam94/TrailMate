package com.example.domain.usecase

import com.example.domain.model.ActiveSession
import com.example.domain.error.DomainError
import com.example.domain.repository.SessionRepository
import com.example.core.Result
import kotlinx.coroutines.flow.Flow

class GetSessionHistoryUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<Result<List<ActiveSession>, DomainError>> {
        return sessionRepository.getSessionHistory()
    }
}
