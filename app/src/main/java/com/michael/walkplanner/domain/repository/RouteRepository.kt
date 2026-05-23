package com.michael.walkplanner.domain.repository

import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.core.Result
import kotlinx.coroutines.flow.Flow

interface RouteRepository {
    fun generateRoute(
        lat: Double,
        lng: Double,
        distanceKm: Double,
        surfaceType: SurfaceType,
        avoidHighways: Boolean,
        maxElevationM: Double
    ): Flow<Result<List<Route>, DomainError>>

    suspend fun getCachedRoutes(): Result<List<Route>, DomainError>
    suspend fun clearExpiredCache(): Result<Unit, DomainError>
    suspend fun saveRoute(route: Route)
    suspend fun getRouteById(id: String): Route?
    suspend fun getCacheAgeDays(): Int
}
