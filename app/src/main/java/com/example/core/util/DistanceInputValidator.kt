package com.example.core.util

import com.example.core.Constants
import com.example.domain.error.DomainError

sealed interface DistanceValidation {
    data class Valid(val roundedKm: Double) : DistanceValidation
    data class Invalid(val error: DomainError.DistanceOutOfRange) : DistanceValidation
}

object DistanceInputValidator {
    fun validate(distanceKm: Double): DistanceValidation {
        if (distanceKm <= 0.0) {
            return DistanceValidation.Invalid(DomainError.DistanceOutOfRange(distanceKm))
        }
        if (distanceKm < Constants.ROUTE_DISTANCE_MIN_KM || distanceKm > Constants.ROUTE_DISTANCE_MAX_KM) {
            return DistanceValidation.Invalid(DomainError.DistanceOutOfRange(distanceKm))
        }
        val rounded = kotlin.math.round(distanceKm / Constants.ROUTE_DISTANCE_STEP_KM) *
            Constants.ROUTE_DISTANCE_STEP_KM
        return DistanceValidation.Valid(rounded)
    }
}
