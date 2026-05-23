package com.michael.walkplanner.domain.error

sealed interface DomainError {
    data object LocationUnavailable : DomainError
    data object NetworkUnavailable : DomainError
    data class DistanceOutOfRange(val requested: Double) : DomainError
    data object RouteGenerationFailed : DomainError
    data object PermissionDenied : DomainError
    data class Unknown(val cause: Throwable) : DomainError
}
