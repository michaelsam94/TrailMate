package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.Route
import com.michael.walkplanner.domain.model.SurfaceType
import com.michael.walkplanner.domain.error.DomainError
import com.michael.walkplanner.domain.repository.LocationRepository
import com.michael.walkplanner.domain.repository.RouteRepository
import com.michael.walkplanner.core.Result
import com.michael.walkplanner.core.util.DistanceInputValidator
import com.michael.walkplanner.core.util.DistanceValidation
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
