package com.example.domain.usecase

import com.example.domain.model.Route
import com.example.domain.model.SurfaceType
import com.example.domain.error.DomainError
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.RouteRepository
import com.example.core.Result
import com.example.core.util.DistanceInputValidator
import com.example.core.util.DistanceValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class GenerateRouteUseCase(
    private val locationRepository: LocationRepository,
    private val routeRepository: RouteRepository
) {
    operator fun invoke(
        distanceKm: Double,
        surfaceType: SurfaceType,
        avoidHighways: Boolean,
        maxElevationM: Double
    ): Flow<Result<List<Route>, DomainError>> = flow {
        emit(Result.Loading)

        when (val validation = DistanceInputValidator.validate(distanceKm)) {
            is DistanceValidation.Invalid -> {
                emit(Result.Failure(validation.error))
                return@flow
            }
            is DistanceValidation.Valid -> {
                val validatedDistance = validation.roundedKm
                when (val locationResult = locationRepository.lastKnownLocation()) {
                    is Result.Success -> {
                        val waypoint = locationResult.data
                        emitAll(
                            routeRepository.generateRoute(
                                lat = waypoint.lat,
                                lng = waypoint.lng,
                                distanceKm = validatedDistance,
                                surfaceType = surfaceType,
                                avoidHighways = avoidHighways,
                                maxElevationM = maxElevationM
                            )
                        )
                    }
                    is Result.Failure -> emit(Result.Failure(locationResult.error))
                    is Result.Loading -> emit(Result.Loading)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
