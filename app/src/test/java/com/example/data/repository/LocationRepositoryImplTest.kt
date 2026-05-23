package com.example.data.repository

import com.example.core.Constants
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocationRepositoryImplTest {

    @Test
    fun `uses planning interval constant for route planning mode`() {
        assertTrue(Constants.LOCATION_UPDATE_INTERVAL_PLANNING_MS == 5_000L)
    }

    @Test
    fun `uses active interval constant for active session mode`() {
        assertTrue(Constants.LOCATION_UPDATE_INTERVAL_ACTIVE_MS == 2_000L)
    }

    @Test
    fun `accuracy threshold is 50 meters`() {
        assertTrue(Constants.LOCATION_ACCURACY_THRESHOLD_M == 50f)
    }

    @Test
    fun `planning interval is longer than active interval`() {
        assertTrue(Constants.LOCATION_UPDATE_INTERVAL_PLANNING_MS > Constants.LOCATION_UPDATE_INTERVAL_ACTIVE_MS)
    }
}
