package com.openemf.sensors

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for per-source exposure index calculation.
 * Verifies the ElectroSmart formula: index = floor(2 * (rssi + 140) / 3)
 */
class ExposureIndexTest {

    // Inline the calculation to avoid Compose dependency in unit tests
    private fun calculateExposureIndex(rssiDbm: Int): Int {
        return when {
            rssiDbm < -140 -> 0
            rssiDbm > 10 -> 100
            else -> kotlin.math.floor(2.0 * (rssiDbm + 140) / 3.0).toInt().coerceIn(0, 100)
        }
    }

    // ==================== Exposure Index Calculation Tests ====================

    @Test
    fun `noise floor (-140 dBm) returns index 0`() {
        assertEquals(0, calculateExposureIndex(-140))
    }

    @Test
    fun `below noise floor returns index 0`() {
        assertEquals(0, calculateExposureIndex(-150))
        assertEquals(0, calculateExposureIndex(-200))
    }

    @Test
    fun `max signal (+10 dBm) returns index 100`() {
        assertEquals(100, calculateExposureIndex(10))
    }

    @Test
    fun `above max signal returns index 100`() {
        assertEquals(100, calculateExposureIndex(20))
        assertEquals(100, calculateExposureIndex(50))
    }

    @Test
    fun `LOW threshold boundary (-75 dBm) returns index 43`() {
        // floor(2 * (-75 + 140) / 3) = floor(2 * 65 / 3) = floor(43.33) = 43
        assertEquals(43, calculateExposureIndex(-75))
    }

    @Test
    fun `HIGH threshold boundary (-34 dBm) returns index approximately 70`() {
        // floor(2 * (-34 + 140) / 3) = floor(2 * 106 / 3) = floor(70.67) = 70
        val index = calculateExposureIndex(-34)
        assertTrue("Index should be 70-71, was $index", index in 70..71)
    }

    @Test
    fun `typical WiFi signal (-50 dBm) returns moderate index`() {
        // floor(2 * (-50 + 140) / 3) = floor(2 * 90 / 3) = floor(60) = 60
        val index = calculateExposureIndex(-50)
        assertEquals(60, index)
    }

    @Test
    fun `weak cellular signal (-100 dBm) returns low index`() {
        // floor(2 * (-100 + 140) / 3) = floor(2 * 40 / 3) = floor(26.67) = 26
        val index = calculateExposureIndex(-100)
        assertTrue("Index should be ~26, was $index", index in 25..28)
    }

    @Test
    fun `very weak signal (-120 dBm) returns very low index`() {
        // floor(2 * (-120 + 140) / 3) = floor(2 * 20 / 3) = floor(13.33) = 13
        val index = calculateExposureIndex(-120)
        assertTrue("Index should be ~13, was $index", index in 12..15)
    }

    // ==================== ElectroSmart Scenario Tests ====================

    @Test
    fun `typical home WiFi router nearby (-45 dBm)`() {
        // floor(2 * 95 / 3) = 63 - Moderate
        val index = calculateExposureIndex(-45)
        assertTrue("Close router should be moderate (55-65), was $index", index in 55..65)
    }

    @Test
    fun `neighbor WiFi (-70 dBm)`() {
        // floor(2 * 70 / 3) = 46 - Just above LOW threshold
        val index = calculateExposureIndex(-70)
        assertTrue("Neighbor WiFi should be moderate (43-50), was $index", index in 43..50)
    }

    @Test
    fun `Bluetooth device nearby (-60 dBm)`() {
        // floor(2 * 80 / 3) = 53 - Moderate
        val index = calculateExposureIndex(-60)
        assertTrue("Nearby Bluetooth should be moderate (50-55), was $index", index in 50..56)
    }

    @Test
    fun `distant cell tower (-110 dBm)`() {
        // floor(2 * 30 / 3) = 20 - LOW
        val index = calculateExposureIndex(-110)
        assertTrue("Distant cell should be low (18-22), was $index", index in 18..22)
    }

    // ==================== Color Threshold Tests ====================

    @Test
    fun `index 42 is in green zone`() {
        val index = calculateExposureIndex(-76) // Should be 42
        assertTrue("Index $index should be < 43 (green zone)", index < 43)
    }

    @Test
    fun `index 43 is in orange zone`() {
        val index = calculateExposureIndex(-75) // Should be 43
        assertTrue("Index $index should be >= 43 (orange zone)", index >= 43)
        assertTrue("Index $index should be < 71 (orange zone)", index < 71)
    }

    @Test
    fun `index 71 is in red zone`() {
        val index = calculateExposureIndex(-33) // Should be 71
        assertTrue("Index $index should be >= 71 (red zone)", index >= 71)
    }
}
