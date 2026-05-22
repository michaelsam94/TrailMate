package com.example.domain.repository

import com.example.domain.model.ActiveSession
import com.example.domain.error.DomainError
import com.example.core.Result
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun saveSession(session: ActiveSession): Result<Unit, DomainError>
    fun getSessionHistory(): Flow<Result<List<ActiveSession>, DomainError>>
    suspend fun deleteSession(sessionId: String): Result<Unit, DomainError>
}
