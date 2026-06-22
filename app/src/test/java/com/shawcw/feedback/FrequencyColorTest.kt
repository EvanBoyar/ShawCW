package com.shawcw.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyColorTest {

    @Test
    fun centerMapsToGreen() {
        val hue = FrequencyColor.hueFor(600.0, 500.0, 600.0, 700.0)
        assertEquals(120.0f, hue, 0.001f)
    }

    @Test
    fun lowAndHighStraddleCenter() {
        val low = FrequencyColor.hueFor(500.0, 500.0, 600.0, 700.0)
        val high = FrequencyColor.hueFor(700.0, 500.0, 600.0, 700.0)
        assertTrue("low end should be a higher hue than center", low > 120.0f)
        assertTrue("high end should be a lower hue than center", high < 120.0f)
    }

    @Test
    fun clampsOutOfBandInput() {
        val below = FrequencyColor.hueFor(400.0, 500.0, 600.0, 700.0)
        val atLow = FrequencyColor.hueFor(500.0, 500.0, 600.0, 700.0)
        assertEquals(atLow, below, 0.001f)
    }
}
