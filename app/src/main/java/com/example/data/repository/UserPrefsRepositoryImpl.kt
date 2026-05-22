package com.example.data.repository

import com.example.domain.model.UserPrefs
import com.example.domain.repository.UserPrefsRepository
import com.example.data.local.datastore.UserPrefsManager
import kotlinx.coroutines.flow.Flow

class UserPrefsRepositoryImpl(
    private val prefsManager: UserPrefsManager
) : UserPrefsRepository {
    override val userPrefsFlow: Flow<UserPrefs> = prefsManager.userPrefsFlow

    override suspend fun updateUserPrefs(userPrefs: UserPrefs) {
        prefsManager.updateUserPrefs(userPrefs)
    }
}
