package com.example.domain.repository

import com.example.domain.model.UserPrefs
import kotlinx.coroutines.flow.Flow

interface UserPrefsRepository {
    val userPrefsFlow: Flow<UserPrefs>
    suspend fun updateUserPrefs(userPrefs: UserPrefs)
}
