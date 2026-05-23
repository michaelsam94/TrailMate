package com.michael.walkplanner.domain.usecase

import com.michael.walkplanner.domain.model.UserPrefs
import com.michael.walkplanner.domain.repository.UserPrefsRepository

class UpdateUserPrefsUseCase(
    private val userPrefsRepository: UserPrefsRepository
) {
    suspend operator fun invoke(userPrefs: UserPrefs) {
        userPrefsRepository.updateUserPrefs(userPrefs)
    }
}
