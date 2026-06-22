package com.shawcw.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ToneDetectorTest {

    private val config = DetectorConfig(sampleRate = 16_000, blockSize = 512)

    private fun toneBlock(hz: Double, amplitude: Double, phase: Double): Pair<FloatArray, Double> {
        val block = FloatArray(config.blockSize)
        var p = phase
        val step = 2 * PI * hz / config.sampleRate
        for (i in block.indices) {
            block[i] = (amplitude * sin(p)).toFloat()
            p += step
        }
        return block to p
    }

    private fun noiseBlock(amplitude: Double, rng: Random): FloatArray {
        return FloatArray(config.blockSize) { (amplitude * (rng.nextDouble() * 2 - 1)).toFloat() }
    }

    @Test
    fun detectsSustainedCenterTone() {
        val detector = ToneDetector(config)
        val rng = Random(1)

        // Prime the noise floor with quiet noise so the floor is non-zero.
        repeat(20) { detector.process(noiseBlock(0.01, rng)) }

        var phase = 0.0
        var detected = false
        repeat(20) {
            val (block, next) = toneBlock(config.centerHz, 0.3, phase)
            phase = next
            if (detector.process(block).isTone) detected = true
        }
        assertTrue("a clear 600 Hz tone should be detected", detected)
    }

    @Test
    fun rejectsBroadbandNoise() {
        val detector = ToneDetector(config)
        val rng = Random(7)
        var everOn = false
        repeat(60) {
            if (detector.process(noiseBlock(0.2, rng)).isTone) everOn = true
        }
        assertFalse("broadband static should not register as a tone", everOn)
    }

    @Test
    fun toleratesFadingWithoutChopping() {
        val detector = ToneDetector(config)
        val rng = Random(3)
        repeat(20) { detector.process(noiseBlock(0.01, rng)) }

        var phase = 0.0
        // Ramp up to a tone.
        repeat(10) {
            val (b, n) = toneBlock(config.centerHz, 0.3, phase); phase = n
            detector.process(b)
        }
        // A brief fade (one weak block) should not drop the tone, thanks to the
        // off-debounce.
        val (weak, n1) = toneBlock(config.centerHz, 0.02, phase); phase = n1
        val duringFade = detector.process(weak)
        assertTrue("a single faded block should not end the tone", duringFade.isTone)
    }

    @Test
    fun reportsFrequencyNearOffCenterTone() {
        val detector = ToneDetector(config)
        val rng = Random(5)
        repeat(20) { detector.process(noiseBlock(0.01, rng)) }

        var phase = 0.0
        var last = 0.0
        repeat(20) {
            val (b, n) = toneBlock(650.0, 0.3, phase); phase = n
            last = detector.process(b).dominantHz
        }
        assertEquals(650.0, last, 20.0)
    }
}
