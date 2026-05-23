package com.michael.walkplanner.core

sealed interface Result<out T, out E> {
    data class Success<out T>(val data: T) : Result<T, Nothing>
    data class Failure<out E>(val error: E) : Result<Nothing, E>
    data object Loading : Result<Nothing, Nothing>
}

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}
