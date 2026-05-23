package com.michael.walkplanner.domain.repository

import com.michael.walkplanner.domain.model.Waypoint
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.core.Result
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun locationUpdates(intervalMs: Long): Flow<Result<Waypoint, DomainError>>
    suspend fun lastKnownLocation(): Result<Waypoint, DomainError>
}
