package com.michael.walkplanner.domain.repository

import com.michael.walkplanner.domain.model.ActiveSession
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.core.Result
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun saveSession(session: ActiveSession): Result<Unit, DomainError>
    fun getSessionHistory(): Flow<Result<List<ActiveSession>, DomainError>>
    suspend fun deleteSession(sessionId: String): Result<Unit, DomainError>
}
