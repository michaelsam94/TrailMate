package com.example.domain.repository

import com.example.domain.model.Waypoint
import com.example.domain.error.DomainError
import com.example.core.Result
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun locationUpdates(intervalMs: Long): Flow<Result<Waypoint, DomainError>>
    suspend fun lastKnownLocation(): Result<Waypoint, DomainError>
}
