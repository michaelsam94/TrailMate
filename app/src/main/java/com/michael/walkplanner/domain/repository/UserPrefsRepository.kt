package com.michael.walkplanner.domain.repository

import com.michael.walkplanner.domain.model.UserPrefs
import kotlinx.coroutines.flow.Flow

interface UserPrefsRepository {
    val userPrefsFlow: Flow<UserPrefs>
    suspend fun updateUserPrefs(userPrefs: UserPrefs)
}
