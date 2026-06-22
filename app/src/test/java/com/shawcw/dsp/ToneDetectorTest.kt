package com.shawcw.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ToneDetectorTest {

    private val config = DetectorConfig(sampleRate = 16_000)
    private val hop get() = config.hopSize

    /** Feeds [hops] hops of a tone (optional) plus broadband noise. */
    private fun feed(
        detector: ToneDetector,
        hops: Int,
        toneHz: Double,
        toneAmp: Double,
        noiseAmp: Double,
        rng: Random,
        state: PhaseState = PhaseState(),
    ): Detection {
        var last: Detection? = null
        repeat(hops) {
            val block = FloatArray(hop)
            val step = 2 * PI * toneHz / config.sampleRate
            for (i in block.indices) {
                val tone = toneAmp * sin(state.phase)
                val noise = noiseAmp * (rng.nextDouble() * 2 - 1)
                block[i] = (tone + noise).toFloat()
                state.phase += step
            }
            last = detector.process(block)
        }
        return last!!
    }

    private class PhaseState(var phase: Double = 0.0)

    @Test
    fun detectsSustainedCenterTone() {
        val detector = ToneDetector(config)
        val last = feed(detector, 30, config.centerHz, 0.2, 0.01, Random(1))
        assertTrue("a clear tone over light noise should be detected", last.isTone)
    }

    @Test
    fun rejectsBroadbandNoise() {
        val detector = ToneDetector(config)
        val rng = Random(7)
        var everOn = false
        repeat(120) { if (feed(detector, 1, 0.0, 0.0, 0.2, rng).isTone) everOn = true }
        assertFalse("broadband static should not register as a tone", everOn)
    }

    @Test
    fun rejectsQuietRoom() {
        // The earlier bug: near silence triggered constant detection. Contrast
        // against reference bands keeps a quiet room flat.
        val detector = ToneDetector(config)
        val rng = Random(11)
        var everOn = false
        repeat(200) { if (feed(detector, 1, 0.0, 0.0, 0.003, rng).isTone) everOn = true }
        assertFalse("a quiet room must not be detected as a tone", everOn)
    }

    @Test
    fun rejectsLoudStatic() {
        val detector = ToneDetector(config)
        val rng = Random(13)
        var everOn = false
        repeat(120) { if (feed(detector, 1, 0.0, 0.0, 0.5, rng).isTone) everOn = true }
        assertFalse("loud static should not register as a tone", everOn)
    }

    @Test
    fun rejectsFilteredInBandStatic() {
        // The real-world failure: a CW receiver filters its audio to the passband,
        // so hiss is concentrated inside the band and absent outside. Estimating
        // noise from inside the band must still reject it: many in-band bins are
        // lit, so no single bin dominates.
        val detector = ToneDetector(config)
        val rng = Random(21)
        // Band-limited noise: many random-phase components spread across the band.
        val components = (0 until 14).map { i ->
            val f = config.lowHz + (config.highHz - config.lowHz) * i / 13.0
            Triple(f, 0.05 + 0.03 * rng.nextDouble(), rng.nextDouble() * 2 * PI)
        }
        var phase0 = 0L
        var everOn = false
        repeat(150) {
            val block = FloatArray(hop)
            for (j in block.indices) {
                val t = (phase0 + j).toDouble() / config.sampleRate
                var v = 0.0
                for ((f, a, ph) in components) v += a * sin(2 * PI * f * t + ph)
                block[j] = v.toFloat()
            }
            phase0 += hop
            if (detector.process(block).isTone) everOn = true
        }
        assertFalse("filtered in-band static must not register as a tone", everOn)
    }

    @Test
    fun toleratesFadingWithoutChopping() {
        val detector = ToneDetector(config)
        val rng = Random(3)
        val state = PhaseState()
        feed(detector, 20, config.centerHz, 0.4, 0.05, rng, state)
        // A single faint hop (tone drops into the noise) must not end the tone.
        val faded = feed(detector, 1, config.centerHz, 0.0, 0.05, rng, state)
        assertTrue("a single faded hop should not end the tone", faded.isTone)
    }

    @Test
    fun clearsAfterSustainedSilence() {
        val detector = ToneDetector(config)
        val rng = Random(4)
        val state = PhaseState()
        feed(detector, 20, config.centerHz, 0.4, 0.05, rng, state)
        val end = feed(detector, 20, 0.0, 0.0, 0.05, rng, state)
        assertFalse("the tone should clear once the signal is gone", end.isTone)
    }

    @Test
    fun reportsFrequencyNearOffCenterTone() {
        val detector = ToneDetector(config)
        val last = feed(detector, 30, 650.0, 0.3, 0.01, Random(5))
        assertEquals(650.0, last.dominantHz, 25.0)
    }

    @Test
    fun separatesDitsAtTwentyWpm() {
        // ~19 wpm: 64 ms elements and gaps (1024 samples at 16 kHz). The detector
        // must release between dits, not smear them into one continuous tone.
        val detector = ToneDetector(config)
        val rng = Random(9)
        val elementSamples = 1024
        val dits = 6
        val step = 2 * PI * config.centerHz / config.sampleRate

        var phase = 0.0
        var t = 0
        var prev = false
        var risingEdges = 0
        var offHops = 0
        val totalHops = dits * 2 * (elementSamples / hop)
        repeat(totalHops) {
            val block = FloatArray(hop)
            for (i in block.indices) {
                val on = (t / elementSamples) % 2 == 0
                val tone = if (on) 0.35 * sin(phase) else 0.0
                block[i] = (tone + 0.02 * (rng.nextDouble() * 2 - 1)).toFloat()
                phase += step
                t++
            }
            val isTone = detector.process(block).isTone
            if (isTone && !prev) risingEdges++
            if (!isTone) offHops++
            prev = isTone
        }

        assertTrue("dits should be detected as separate elements (got $risingEdges edges)", risingEdges >= 4)
        assertTrue("detector must release between dits", offHops > 0)
    }
}
