package com.example.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.SurfaceType
import com.example.domain.model.UserPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPrefsManager(private val context: Context) {
    companion object {
        val PREFERRED_SURFACE = stringPreferencesKey("preferred_surface")
        val AVOID_HIGHWAYS = booleanPreferencesKey("avoid_highways")
        val MAX_ELEVATION_GAIN = doublePreferencesKey("max_elevation_gain")
        val USE_IMPERIAL_UNITS = booleanPreferencesKey("use_imperial_units")
    }

    val userPrefsFlow: Flow<UserPrefs> = context.dataStore.data.map { preferences ->
        val surfaceStr = preferences[PREFERRED_SURFACE] ?: SurfaceType.MIXED.name
        val preferredSurface = try {
            SurfaceType.valueOf(surfaceStr)
        } catch (e: Exception) {
            SurfaceType.MIXED
        }
        UserPrefs(
            preferredSurface = preferredSurface,
            avoidHighways = preferences[AVOID_HIGHWAYS] ?: true,
            maxElevationGainM = preferences[MAX_ELEVATION_GAIN] ?: 200.0,
            useImperialUnits = preferences[USE_IMPERIAL_UNITS] ?: false
        )
    }

    suspend fun updateUserPrefs(userPrefs: UserPrefs) {
        context.dataStore.edit { preferences ->
            preferences[PREFERRED_SURFACE] = userPrefs.preferredSurface.name
            preferences[AVOID_HIGHWAYS] = userPrefs.avoidHighways
            preferences[MAX_ELEVATION_GAIN] = userPrefs.maxElevationGainM
            preferences[USE_IMPERIAL_UNITS] = userPrefs.useImperialUnits
        }
    }
}
