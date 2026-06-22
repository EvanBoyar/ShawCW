package com.shawcw.dsp

import kotlin.math.abs

/**
 * Configuration for [ToneDetector]. Frequencies come straight from the user's
 * tone settings: [centerHz] is the zero-beat tone, [lowHz]/[highHz] bound how
 * far an operator may drift off center.
 */
data class DetectorConfig(
    val sampleRate: Int = 16_000,
    val blockSize: Int = 512,
    val lowHz: Double = 500.0,
    val centerHz: Double = 600.0,
    val highHz: Double = 700.0,
    /** Number of Goertzel bins spread across [lowHz]..[highHz]. */
    val binCount: Int = 24,
    /**
     * Tone turns on when the strongest bin is at least this many times the
     * median bin (spectral contrast). A real CW tone is one bin towering over
     * the rest; broadband noise lifts every bin together and stays near 1.
     */
    val onContrast: Double = 5.0,
    /** Tone turns off when contrast drops below this, giving hysteresis. */
    val offContrast: Double = 3.0,
    /** Consecutive blocks above the on threshold before reporting a tone. */
    val minOnBlocks: Int = 2,
    /** Consecutive blocks below the off threshold before clearing a tone. */
    val minOffBlocks: Int = 3,
    /**
     * Absolute magnitude the peak bin must reach before any detection. Guards
     * against dividing near-zero magnitudes in digital silence, where contrast
     * would otherwise be meaningless. Not a loudness gate; real room noise sits
     * well above this.
     */
    val absoluteGate: Double = 0.0008,
    /** Half-width in Hz of frequencies notched out (the vibration signature). */
    val notchHalfWidthHz: Double = 8.0,
) {
    init {
        require(lowHz < highHz) { "lowHz must be below highHz" }
        require(centerHz in lowHz..highHz) { "centerHz must sit within the band" }
        require(binCount >= 1) { "need at least one bin" }
        require(offContrast < onContrast) { "offContrast must be below onContrast for hysteresis" }
    }
}

/** Result of processing one block of audio. */
data class Detection(
    /** True while a CW tone is considered present. */
    val isTone: Boolean,
    /** Best estimate of the dominant tone frequency in Hz. */
    val dominantHz: Double,
    /** Magnitude of the strongest bin. */
    val magnitude: Double,
    /** Median bin magnitude, the in-band noise reference for this block. */
    val noiseFloor: Double,
    /** Per-bin magnitudes for this block, aligned with [ToneDetector.binFrequencies]. */
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
 * Turns a stream of audio blocks into on/off tone events.
 *
 * Detection keys on spectral contrast within each block: how far the strongest
 * bin stands above the median of the band. This is independent of overall
 * level, so a quiet room (all bins low and roughly equal) and loud static (all
 * bins high and roughly equal) both read as no tone, while a real CW signal
 * (one bin dominating) reads as a tone at any volume. Hysteresis plus the
 * off-debounce ([minOffBlocks]) lets a fading tone dip without being chopped
 * into separate elements.
 *
 * Frequencies inside a notch (see [setNotches]) are ignored, which is how the
 * phone's own vibration signature is kept from self triggering the detector.
 */
class ToneDetector(private val config: DetectorConfig) {

    private val bins: List<Goertzel>
    private val binHz: DoubleArray
    private var notches: List<Double> = emptyList()

    private var active = false
    private var onCount = 0
    private var offCount = 0

    init {
        val hz = DoubleArray(config.binCount)
        val step = if (config.binCount == 1) 0.0
            else (config.highHz - config.lowHz) / (config.binCount - 1)
        for (i in 0 until config.binCount) {
            hz[i] = config.lowHz + step * i
        }
        binHz = hz
        bins = hz.map { Goertzel(it, config.sampleRate, config.blockSize) }
    }

    /** Center frequency of each Goertzel bin, low to high. */
    val binFrequencies: DoubleArray
        get() = binHz.copyOf()

    /** Frequencies (Hz) to ignore, for example the measured vibration tone. */
    fun setNotches(frequencies: List<Double>) {
        notches = frequencies
    }

    private fun isNotched(hz: Double): Boolean =
        notches.any { abs(it - hz) <= config.notchHalfWidthHz }

    /** Resets all state, for example when settings change. */
    fun reset() {
        active = false
        onCount = 0
        offCount = 0
    }

    fun process(block: FloatArray): Detection {
        val mags = DoubleArray(bins.size)
        val bandMags = ArrayList<Double>(bins.size)
        var peakMag = 0.0
        var peakIdx = -1
        for (i in bins.indices) {
            if (isNotched(binHz[i])) continue
            val m = bins[i].magnitude(block)
            mags[i] = m
            bandMags.add(m)
            if (m > peakMag) {
                peakMag = m
                peakIdx = i
            }
        }

        val median = medianOf(bandMags)
        val contrast = peakMag / median.coerceAtLeast(1e-6)
        val gateOpen = peakMag >= config.absoluteGate

        if (!active) {
            if (gateOpen && contrast >= config.onContrast) {
                onCount++
                if (onCount >= config.minOnBlocks) {
                    active = true
                    offCount = 0
                }
            } else {
                onCount = 0
            }
        } else {
            if (!gateOpen || contrast < config.offContrast) {
                offCount++
                if (offCount >= config.minOffBlocks) {
                    active = false
                    onCount = 0
                }
            } else {
                offCount = 0
            }
        }

        val dominantHz = if (peakIdx >= 0) interpolatedHz(peakIdx, mags) else config.centerHz
        return Detection(active, dominantHz, peakMag, median, mags)
    }

    private fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /**
     * Quadratic interpolation around the peak bin for a finer frequency
     * estimate than the bin spacing alone, which the color feedback relies on.
     */
    private fun interpolatedHz(peakIdx: Int, mags: DoubleArray): Double {
        if (peakIdx <= 0 || peakIdx >= bins.size - 1) return binHz[peakIdx]
        val left = mags[peakIdx - 1]
        val center = mags[peakIdx]
        val right = mags[peakIdx + 1]
        val denom = left - 2 * center + right
        if (denom == 0.0) return binHz[peakIdx]
        val offset = 0.5 * (left - right) / denom
        val step = binHz[1] - binHz[0]
        return binHz[peakIdx] + offset * step
    }
}
