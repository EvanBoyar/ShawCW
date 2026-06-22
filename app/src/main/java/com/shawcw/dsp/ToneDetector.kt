package com.shawcw.dsp

import kotlin.math.abs
import kotlin.math.max

/**
 * Configuration for [ToneDetector]. Frequencies come straight from the user's
 * tone settings: [centerHz] is the zero-beat tone, [lowHz]/[highHz] bound how
 * far an operator may drift off center.
 *
 * The detector runs two analyses per hop. A short [windowSize] drives on/off
 * timing: short means fast release, so dits past 20 wpm stay separate. A longer
 * [freqWindowSize] drives the frequency estimate and the spectrum display:
 * longer means fine frequency resolution for the color feedback. [hopSize] sets
 * how often a decision is made. Defaults at 16 kHz: 16 ms timing window, 32 ms
 * frequency window, 8 ms hop.
 */
data class DetectorConfig(
    val sampleRate: Int = 16_000,
    val windowSize: Int = 256,
    val freqWindowSize: Int = 512,
    val hopSize: Int = 128,
    val lowHz: Double = 500.0,
    val centerHz: Double = 600.0,
    val highHz: Double = 700.0,
    /** Coarse in-band bins on the short window, used to find tone energy. */
    val bandBins: Int = 8,
    /** Bins placed outside the band to estimate the broadband noise. */
    val referenceBins: Int = 8,
    /** Fine in-band bins on the long window, for the frequency estimate and spectrum. */
    val spectrumBins: Int = 24,
    /**
     * Tone turns on when the strongest in-band bin is at least this many times
     * the median reference bin. A CW tone lifts one in-band bin without touching
     * the reference bands; broadband noise and static lift both together, so
     * their ratio stays near 1 regardless of volume.
     */
    val onContrast: Double = 4.0,
    /** Tone turns off when contrast drops below this, giving hysteresis. */
    val offContrast: Double = 2.5,
    /** Consecutive hops above the on threshold before reporting a tone. */
    val minOnHops: Int = 2,
    /** Consecutive hops below the off threshold before clearing a tone. */
    val minOffHops: Int = 2,
    /**
     * Absolute magnitude the peak bin must reach before any detection. Guards
     * against dividing near-zero magnitudes in digital silence; real room noise
     * sits well above this, so it is not a loudness gate.
     */
    val absoluteGate: Double = 0.0008,
    /** Half-width in Hz of frequencies notched out (the vibration signature). */
    val notchHalfWidthHz: Double = 8.0,
) {
    init {
        require(lowHz < highHz) { "lowHz must be below highHz" }
        require(centerHz in lowHz..highHz) { "centerHz must sit within the band" }
        require(bandBins >= 1) { "need at least one band bin" }
        require(referenceBins >= 2) { "need at least two reference bins" }
        require(spectrumBins >= 3) { "need at least three spectrum bins" }
        require(hopSize in 1..windowSize) { "hopSize must be within 1..windowSize" }
        require(offContrast < onContrast) { "offContrast must be below onContrast for hysteresis" }
    }
}

/** Result of processing one hop of audio. */
data class Detection(
    /** True while a CW tone is considered present. */
    val isTone: Boolean,
    /** Best estimate of the dominant tone frequency in Hz. */
    val dominantHz: Double,
    /** Magnitude of the strongest in-band bin (timing window). */
    val magnitude: Double,
    /** Median reference-band magnitude, the noise reference for this window. */
    val noiseFloor: Double,
    /** Fine in-band magnitudes for the spectrum, aligned with [ToneDetector.binFrequencies]. */
    val magnitudes: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Detection) return false
        return isTone == other.isTone &&
            dominantHz == other.dominantHz &&
            magnitude == other.magnitude &&
            noiseFloor == other.noiseFloor &&
            magnitudes.contentEquals(other.magnitudes)
    }

    override fun hashCode(): Int {
        var result = isTone.hashCode()
        result = 31 * result + dominantHz.hashCode()
        result = 31 * result + magnitude.hashCode()
        result = 31 * result + noiseFloor.hashCode()
        result = 31 * result + magnitudes.contentHashCode()
        return result
    }
}

/**
 * Turns a stream of audio hops into on/off tone events.
 *
 * On/off keys on spectral contrast over a short window: how far the strongest
 * in-band bin stands above the median of reference bins placed just outside the
 * band. That ratio is level independent, so a quiet room and loud static both
 * read as no tone while a real CW signal reads as a tone at any volume. The
 * short window releases quickly, keeping fast dits separate. A second, longer
 * window estimates the dominant frequency and feeds the spectrum, where fine
 * frequency resolution matters more than speed.
 *
 * Frequencies inside a notch (see [setNotches]) are ignored, which is how the
 * phone's own vibration signature is kept from self triggering the detector.
 */
class ToneDetector(private val config: DetectorConfig) {

    // Timing path: short window.
    private val shortWindow = FloatArray(config.windowSize)
    private val bandHz: DoubleArray
    private val bandGoertzels: List<Goertzel>
    private val referenceHz: DoubleArray
    private val referenceGoertzels: List<Goertzel>

    // Frequency and spectrum path: long window.
    private val freqWindow = FloatArray(config.freqWindowSize)
    private val fineHz: DoubleArray
    private val fineGoertzels: List<Goertzel>

    private var notches: List<Double> = emptyList()
    private var active = false
    private var onCount = 0
    private var offCount = 0

    init {
        bandHz = spread(config.lowHz, config.highHz, config.bandBins)
        bandGoertzels = bandHz.map { Goertzel(it, config.sampleRate, config.windowSize) }

        referenceHz = referenceFrequencies()
        referenceGoertzels = referenceHz.map { Goertzel(it, config.sampleRate, config.windowSize) }

        fineHz = spread(config.lowHz, config.highHz, config.spectrumBins)
        fineGoertzels = fineHz.map { Goertzel(it, config.sampleRate, config.freqWindowSize) }
    }

    /** Center frequency of each fine spectrum bin, low to high. */
    val binFrequencies: DoubleArray
        get() = fineHz.copyOf()

    /** Frequencies (Hz) to ignore, for example the measured vibration tone. */
    fun setNotches(frequencies: List<Double>) {
        notches = frequencies
    }

    private fun isNotched(hz: Double): Boolean =
        notches.any { abs(it - hz) <= config.notchHalfWidthHz }

    /** Resets all state, for example when settings change. */
    fun reset() {
        shortWindow.fill(0f)
        freqWindow.fill(0f)
        active = false
        onCount = 0
        offCount = 0
    }

    /**
     * Feeds one hop of fresh samples and returns the decision for the windows
     * ending at this hop. [hop] should be [DetectorConfig.hopSize] samples; a
     * larger block is accepted and only its most recent window is kept.
     */
    fun process(hop: FloatArray): Detection {
        slideIn(shortWindow, hop)
        slideIn(freqWindow, hop)

        // Timing path: peak in-band energy versus the reference noise.
        var peakBand = 0.0
        for (i in bandGoertzels.indices) {
            if (isNotched(bandHz[i])) continue
            val m = bandGoertzels[i].magnitude(shortWindow)
            if (m > peakBand) peakBand = m
        }
        val noise = referenceMedian()
        val contrast = peakBand / noise.coerceAtLeast(1e-6)
        val gateOpen = peakBand >= config.absoluteGate

        if (!active) {
            if (gateOpen && contrast >= config.onContrast) {
                onCount++
                if (onCount >= config.minOnHops) {
                    active = true
                    offCount = 0
                }
            } else {
                onCount = 0
            }
        } else {
            if (!gateOpen || contrast < config.offContrast) {
                offCount++
                if (offCount >= config.minOffHops) {
                    active = false
                    onCount = 0
                }
            } else {
                offCount = 0
            }
        }

        // Frequency and spectrum path on the longer window.
        val fineMags = DoubleArray(fineGoertzels.size)
        var peakFine = 0.0
        var peakIdx = -1
        for (i in fineGoertzels.indices) {
            if (isNotched(fineHz[i])) continue
            val m = fineGoertzels[i].magnitude(freqWindow)
            fineMags[i] = m
            if (m > peakFine) {
                peakFine = m
                peakIdx = i
            }
        }
        val dominantHz = if (peakIdx >= 0) interpolatedHz(peakIdx, fineMags) else config.centerHz

        return Detection(active, dominantHz, peakBand, noise, fineMags)
    }

    private fun slideIn(window: FloatArray, hop: FloatArray) {
        val w = window.size
        val n = hop.size
        if (n >= w) {
            System.arraycopy(hop, n - w, window, 0, w)
        } else {
            System.arraycopy(window, n, window, 0, w - n)
            System.arraycopy(hop, 0, window, w - n, n)
        }
    }

    private fun referenceMedian(): Double {
        val values = DoubleArray(referenceGoertzels.size)
        for (i in referenceGoertzels.indices) {
            values[i] = referenceGoertzels[i].magnitude(shortWindow)
        }
        values.sort()
        val mid = values.size / 2
        return if (values.size % 2 == 1) {
            values[mid]
        } else {
            (values[mid - 1] + values[mid]) / 2.0
        }
    }

    /**
     * Reference bins sit a guard band beyond each side of the detection band, so
     * the short window's spectral leakage from an in-band tone does not reach
     * them. The guard is the window's first-null width.
     */
    private fun referenceFrequencies(): DoubleArray {
        val guard = max(40.0, config.sampleRate.toDouble() / config.windowSize)
        val span = (config.highHz - config.lowHz).coerceIn(120.0, 400.0)
        val nyquist = config.sampleRate / 2.0
        val floorHz = 80.0
        val ceilHz = nyquist - 80.0

        val perSide = max(1, config.referenceBins / 2)
        val low = spread(config.lowHz - guard - span, config.lowHz - guard, perSide)
        val high = spread(config.highHz + guard, config.highHz + guard + span, perSide)

        val refs = (low + high).filter { it in floorHz..ceilHz }.toDoubleArray()
        return if (refs.isNotEmpty()) {
            refs
        } else {
            doubleArrayOf((config.lowHz - guard).coerceAtLeast(floorHz), config.highHz + guard)
        }
    }

    /**
     * Quadratic interpolation around the peak bin for a finer frequency estimate
     * than the bin spacing alone, which the color feedback relies on.
     */
    private fun interpolatedHz(peakIdx: Int, mags: DoubleArray): Double {
        if (peakIdx <= 0 || peakIdx >= mags.size - 1) return fineHz[peakIdx]
        val left = mags[peakIdx - 1]
        val center = mags[peakIdx]
        val right = mags[peakIdx + 1]
        val denom = left - 2 * center + right
        if (denom == 0.0) return fineHz[peakIdx]
        val offset = 0.5 * (left - right) / denom
        val step = fineHz[1] - fineHz[0]
        return fineHz[peakIdx] + offset * step
    }

    companion object {
        /** [count] frequencies spread evenly across [from]..[to] inclusive. */
        private fun spread(from: Double, to: Double, count: Int): DoubleArray {
            if (count == 1) return doubleArrayOf((from + to) / 2.0)
            val step = (to - from) / (count - 1)
            return DoubleArray(count) { from + step * it }
        }
    }
}
