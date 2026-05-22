package com.example.domain.repository

import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.error.DomainError
import com.example.core.Result
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
}
