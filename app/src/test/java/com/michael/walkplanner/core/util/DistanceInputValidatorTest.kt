package com.michael.walkplanner.core.util

import com.michael.walkplanner.core.Constants
import com.michael.walkplanner.domain.error.DomainError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistanceInputValidatorTest {

    @Test
    fun `accepts 0_5 km minimum valid`() {
        val result = DistanceInputValidator.validate(0.5)
        assertTrue(result is DistanceValidation.Valid)
        assertEquals(0.5, (result as DistanceValidation.Valid).roundedKm)
    }

    @Test
    fun `accepts 50_0 km maximum valid`() {
        val result = DistanceInputValidator.validate(50.0)
        assertTrue(result is DistanceValidation.Valid)
        assertEquals(50.0, (result as DistanceValidation.Valid).roundedKm)
    }

    @Test
    fun `rejects 0_4 km below minimum`() {
        val result = DistanceInputValidator.validate(0.4)
        assertTrue(result is DistanceValidation.Invalid)
        assertEquals(0.4, (result as DistanceValidation.Invalid).error.requested)
    }

    @Test
    fun `rejects 50_1 km above maximum`() {
        val result = DistanceInputValidator.validate(50.1)
        assertTrue(result is DistanceValidation.Invalid)
    }

    @Test
    fun `rounds 3_8 km input to 4_0 km`() {
        val result = DistanceInputValidator.validate(3.8)
        assertTrue(result is DistanceValidation.Valid)
        assertEquals(4.0, (result as DistanceValidation.Valid).roundedKm)
    }

    @Test
    fun `rounds 2_3 km input to 2_5 km`() {
        val result = DistanceInputValidator.validate(2.3)
        assertTrue(result is DistanceValidation.Valid)
        assertEquals(2.5, (result as DistanceValidation.Valid).roundedKm)
    }

    @Test
    fun `rejects negative distance values`() {
        val result = DistanceInputValidator.validate(-1.0)
        assertTrue(result is DistanceValidation.Invalid)
    }

    @Test
    fun `rejects zero as distance`() {
        val result = DistanceInputValidator.validate(0.0)
        assertTrue(result is DistanceValidation.Invalid)
    }
}
