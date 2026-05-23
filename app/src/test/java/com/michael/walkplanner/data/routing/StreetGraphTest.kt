package com.michael.walkplanner.data.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreetGraphTest {

    @Test
    fun `haversineDistanceM matches known one degree latitude span`() {
        val distanceM = haversineDistanceM(51.0, 0.0, 52.0, 0.0)
        assertTrue(distanceM in 110_000.0..112_000.0)
    }

    @Test
    fun `highwaySafetyWeight prefers footways over primary roads`() {
        assertTrue(highwaySafetyWeight("footway") < highwaySafetyWeight("primary"))
        assertEquals(0.8f, highwaySafetyWeight("path"))
    }
}
