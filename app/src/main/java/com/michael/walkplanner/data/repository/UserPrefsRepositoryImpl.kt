package com.michael.walkplanner.data.repository

import com.michael.walkplanner.domain.model.UserPrefs
import com.michael.walkplanner.domain.repository.UserPrefsRepository
import com.michael.walkplanner.data.local.datastore.UserPrefsManager
import kotlinx.coroutines.flow.Flow

class UserPrefsRepositoryImpl(
    private val prefsManager: UserPrefsManager
) : UserPrefsRepository {
    override val userPrefsFlow: Flow<UserPrefs> = prefsManager.userPrefsFlow

    override suspend fun updateUserPrefs(userPrefs: UserPrefs) {
        prefsManager.updateUserPrefs(userPrefs)
    }
}
