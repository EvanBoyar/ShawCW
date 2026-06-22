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

    /** Like [feed] but against an explicit [cfg], for non-default bands. */
    private fun feedTone(
        detector: ToneDetector,
        cfg: DetectorConfig,
        hops: Int,
        toneHz: Double,
        toneAmp: Double,
        noiseAmp: Double,
        rng: Random,
    ): Detection {
        var last: Detection? = null
        var phase = 0.0
        val step = 2 * PI * toneHz / cfg.sampleRate
        repeat(hops) {
            val block = FloatArray(cfg.hopSize)
            for (i in block.indices) {
                block[i] = (toneAmp * sin(phase) + noiseAmp * (rng.nextDouble() * 2 - 1)).toFloat()
                phase += step
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
    fun detectsCleanToneInNarrowBand() {
        // The reported bug: a narrow band killed detection because every bin fell
        // inside the tone's own main lobe, so the in-band floor matched the peak.
        // The floor is now read from a wider analysis span, so a narrow band still
        // detects a clean tone instead of needing an absurdly wide one.
        val narrow = DetectorConfig(sampleRate = 16_000, lowHz = 650.0, centerHz = 684.0, highHz = 700.0)
        val detector = ToneDetector(narrow)
        val last = feedTone(detector, narrow, hops = 40, toneHz = 684.0, toneAmp = 0.25, noiseAmp = 0.01, rng = Random(1))
        assertTrue("a clean tone in a narrow band must be detected", last.isTone)
    }

    @Test
    fun narrowBandRejectsBroadbandStatic() {
        // Across many seeds and levels: a narrow band must never latch onto white
        // noise. Noise fakes the contrast for at most two hops, below minOnHops.
        val narrow = DetectorConfig(sampleRate = 16_000, lowHz = 650.0, centerHz = 684.0, highHz = 700.0)
        for (seed in 0 until 40) {
            for (amp in doubleArrayOf(0.1, 0.3, 0.5)) {
                val detector = ToneDetector(narrow)
                val rng = Random(seed.toLong())
                var everOn = false
                repeat(200) {
                    if (feedTone(detector, narrow, 1, 0.0, 0.0, amp, rng).isTone) everOn = true
                }
                assertFalse("narrow band fired on white noise (seed=$seed amp=$amp)", everOn)
            }
        }
    }

    @Test
    fun moderatelyNarrowBandRejectsBandFillingStatic() {
        // Down to about 100 Hz the in-band occupancy test still has the bins to
        // tell band-filling hiss from a single tone. (Below that is a documented
        // resolution limit, not covered here.) Modeled as dense random-phase
        // components, the Fourier picture of band-limited hiss: a steady fill, not
        // a handful of carriers that beat into gaps the floor tracker could chase.
        val band = DetectorConfig(sampleRate = 16_000, lowHz = 634.0, centerHz = 684.0, highHz = 734.0)
        val detector = ToneDetector(band)
        val rng = Random(21)
        val count = 50
        val components = (0 until count).map { i ->
            val f = band.lowHz + (band.highHz - band.lowHz) * i / (count - 1).toDouble()
            Triple(f, 0.03 + 0.02 * rng.nextDouble(), rng.nextDouble() * 2 * PI)
        }
        var phase0 = 0L
        var everOn = false
        repeat(150) {
            val block = FloatArray(band.hopSize)
            for (j in block.indices) {
                val t = (phase0 + j).toDouble() / band.sampleRate
                var v = 0.0
                for ((f, a, ph) in components) v += a * sin(2 * PI * f * t + ph)
                block[j] = v.toFloat()
            }
            phase0 += band.hopSize
            if (detector.process(block).isTone) everOn = true
        }
        assertFalse("a 100 Hz band must reject static that fills it", everOn)
    }

    @Test
    fun reportsFrequencyNearOffCenterTone() {
        val detector = ToneDetector(config)
        val last = feed(detector, 30, 650.0, 0.3, 0.01, Random(5))
        assertEquals(650.0, last.dominantHz, 25.0)
    }

    @Test
    fun rejectsSingleImpulsePerWindow() {
        // A flat broadband transient (one impulse per analysis window) has instant
        // spectral peakedness near unity, so the flatness gate rejects it even
        // though, after quiet, the lagging tracked floor would otherwise let the
        // spike's contrast through. Impulses are spaced one per long window so the
        // spectrum stays flat (no comb), and run long enough that minOnHops alone
        // would not save it; only the flatness gate does.
        val detector = ToneDetector(config)
        val rng = Random(101)
        repeat(40) { feed(detector, 1, 0.0, 0.0, 0.001, rng) }
        var everOn = false
        repeat(60) { i ->
            val block = FloatArray(hop)
            if (i % (config.freqWindowSize / hop) == 0) block[0] = 1.0f
            if (detector.process(block).isTone) everOn = true
        }
        assertFalse("a flat broadband transient must not register as a tone", everOn)
    }

    @Test
    fun sensitivityLowersThresholdToDetectWeakerTone() {
        // A weak tone that the least-sensitive setting rejects, the most-sensitive
        // setting catches. Same input fed to two independently configured detectors.
        fun runAt(sensitivity: Double): Boolean {
            val detector = ToneDetector(config).also { it.setSensitivity(sensitivity) }
            return feed(detector, 40, config.centerHz, 0.03, 0.02, Random(55)).isTone
        }
        assertFalse("a weak tone should not detect at the least sensitive setting", runAt(0.0))
        assertTrue("the same weak tone should detect at the most sensitive setting", runAt(1.0))
    }

    @Test
    fun sensitivityDefaultMatchesBaseline() {
        // Sensitivity 0.5 must reproduce the behavior of never setting it.
        val baseline = ToneDetector(config)
        val explicit = ToneDetector(config).also { it.setSensitivity(0.5) }
        val rng1 = Random(1)
        val rng2 = Random(1)
        repeat(30) {
            val a = feed(baseline, 1, config.centerHz, 0.2, 0.01, rng1).isTone
            val b = feed(explicit, 1, config.centerHz, 0.2, 0.01, rng2).isTone
            assertEquals("decision must match baseline at sensitivity 0.5", a, b)
        }
    }

    @Test
    fun liveSensitivityDoesNotResetState() {
        val detector = ToneDetector(config)
        val state = PhaseState()
        val on = feed(detector, 20, config.centerHz, 0.3, 0.02, Random(7), state)
        assertTrue("tone should be latched before changing sensitivity", on.isTone)
        // Changing sensitivity live must not drop the latched tone.
        detector.setSensitivity(0.7)
        val after = feed(detector, 1, config.centerHz, 0.3, 0.02, Random(7), state)
        assertTrue("a live sensitivity change must not reset the active tone", after.isTone)
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
