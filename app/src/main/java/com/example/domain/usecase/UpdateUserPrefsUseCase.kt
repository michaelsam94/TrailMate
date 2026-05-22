package com.example.domain.usecase

import com.example.domain.model.UserPrefs
import com.example.domain.repository.UserPrefsRepository

class UpdateUserPrefsUseCase(
    private val userPrefsRepository: UserPrefsRepository
) {
    suspend operator fun invoke(userPrefs: UserPrefs) {
        userPrefsRepository.updateUserPrefs(userPrefs)
    }
}
