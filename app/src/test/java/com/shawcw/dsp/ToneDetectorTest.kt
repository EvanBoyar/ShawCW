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

    /** A block of tone (optional) plus uniform broadband noise. */
    private fun signalBlock(
        toneHz: Double,
        toneAmp: Double,
        noiseAmp: Double,
        rng: Random,
        phase: Double,
    ): Pair<FloatArray, Double> {
        val block = FloatArray(config.blockSize)
        var p = phase
        val step = 2 * PI * toneHz / config.sampleRate
        for (i in block.indices) {
            val tone = toneAmp * sin(p)
            val noise = noiseAmp * (rng.nextDouble() * 2 - 1)
            block[i] = (tone + noise).toFloat()
            p += step
        }
        return block to p
    }

    private fun feed(
        detector: ToneDetector,
        blocks: Int,
        toneHz: Double,
        toneAmp: Double,
        noiseAmp: Double,
        rng: Random,
        startPhase: Double = 0.0,
    ): Detection {
        var phase = startPhase
        var last: Detection? = null
        repeat(blocks) {
            val (block, next) = signalBlock(toneHz, toneAmp, noiseAmp, rng, phase)
            phase = next
            last = detector.process(block)
        }
        return last!!
    }

    @Test
    fun detectsSustainedCenterTone() {
        val detector = ToneDetector(config)
        val rng = Random(1)
        var detected = false
        var phase = 0.0
        repeat(20) {
            val (block, next) = signalBlock(config.centerHz, 0.2, 0.01, rng, phase)
            phase = next
            if (detector.process(block).isTone) detected = true
        }
        assertTrue("a clear tone over light noise should be detected", detected)
    }

    @Test
    fun rejectsBroadbandNoise() {
        val detector = ToneDetector(config)
        val rng = Random(7)
        var everOn = false
        var phase = 0.0
        repeat(80) {
            val (block, next) = signalBlock(0.0, 0.0, 0.2, rng, phase)
            phase = next
            if (detector.process(block).isTone) everOn = true
        }
        assertFalse("broadband static should not register as a tone", everOn)
    }

    @Test
    fun rejectsQuietRoom() {
        // The reported bug: near silence triggered constant detection because
        // tiny magnitudes produced huge ratios. Contrast keeps a quiet room flat.
        val detector = ToneDetector(config)
        val rng = Random(11)
        var everOn = false
        repeat(120) {
            if (feed(detector, 1, 0.0, 0.0, 0.003, rng).isTone) everOn = true
        }
        assertFalse("a quiet room must not be detected as a tone", everOn)
    }

    @Test
    fun rejectsLoudStatic() {
        // Detection must be level independent: loud broadband static still has
        // no dominant bin, so it is not a tone.
        val detector = ToneDetector(config)
        val rng = Random(13)
        var everOn = false
        repeat(80) {
            if (feed(detector, 1, 0.0, 0.0, 0.5, rng).isTone) everOn = true
        }
        assertFalse("loud static should not register as a tone", everOn)
    }

    @Test
    fun toleratesFadingWithoutChopping() {
        val detector = ToneDetector(config)
        val rng = Random(3)
        val phase = feedPhase(detector, 10, config.centerHz, 0.4, 0.05, rng)
        // One faint block (tone drops into the noise) must not end the tone,
        // thanks to the off-debounce.
        val (weak, _) = signalBlock(config.centerHz, 0.0, 0.05, rng, phase)
        assertTrue("a single faded block should not end the tone", detector.process(weak).isTone)
    }

    @Test
    fun clearsAfterSustainedSilence() {
        val detector = ToneDetector(config)
        val rng = Random(4)
        feed(detector, 10, config.centerHz, 0.4, 0.05, rng)
        val end = feed(detector, 10, 0.0, 0.0, 0.05, rng)
        assertFalse("the tone should clear once the signal is gone", end.isTone)
    }

    @Test
    fun reportsFrequencyNearOffCenterTone() {
        val detector = ToneDetector(config)
        val rng = Random(5)
        val last = feed(detector, 20, 650.0, 0.3, 0.01, rng)
        assertEquals(650.0, last.dominantHz, 20.0)
    }

    private fun feedPhase(
        detector: ToneDetector,
        blocks: Int,
        toneHz: Double,
        toneAmp: Double,
        noiseAmp: Double,
        rng: Random,
    ): Double {
        var phase = 0.0
        repeat(blocks) {
            val (block, next) = signalBlock(toneHz, toneAmp, noiseAmp, rng, phase)
            phase = next
            detector.process(block)
        }
        return phase
    }
}
