package com.shawcw.dsp

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    /** In-band bins on the short window, for the peak and the in-band noise floor. */
    val detectionBins: Int = 16,
    /** Fine in-band bins on the long window, for the frequency estimate and spectrum. */
    val spectrumBins: Int = 24,
    /**
     * Tone turns on when the strongest in-band bin is at least this many times
     * the median of the rest of the band (the bins away from the peak). A CW
     * tone is one sharp bin above an otherwise flat band; broadband or filtered
     * static fills the whole band, so its peak barely exceeds the rest. Because
     * the reference is inside the band, this holds whatever the receiver's audio
     * filter does to the noise shape, and at any volume.
     */
    val onContrast: Double = 5.0,
    /** Tone turns off when contrast drops below this, giving hysteresis. */
    val offContrast: Double = 3.0,
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
        require(detectionBins >= 5) { "need enough detection bins to estimate an in-band floor" }
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
    /** In-band noise floor: median of the band away from the peak. */
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
 * On/off keys on in-band spectral contrast over a short window: how far the
 * strongest bin stands above the median of the rest of the band. A real CW tone
 * is a single sharp peak; a quiet room, loud static, and receiver-filtered hiss
 * all fill the band evenly, so their peak barely clears the rest and they read
 * as no tone. Estimating noise inside the band makes this independent of both
 * volume and the receiver's audio passband shape. The short window releases
 * quickly, keeping fast dits separate. A second, longer window estimates the
 * dominant frequency and feeds the spectrum, where resolution beats speed.
 *
 * Frequencies inside a notch (see [setNotches]) are ignored, which is how the
 * phone's own vibration signature is kept from self triggering the detector.
 */
class ToneDetector(private val config: DetectorConfig) {

    // Timing path: short window gives the instantaneous peak in-band energy.
    private val shortWindow = FloatArray(config.windowSize)
    private val bandHz: DoubleArray
    private val bandGoertzels: List<Goertzel>

    // Frequency, spectrum, and the noise floor: long window, for resolution.
    private val freqWindow = FloatArray(config.freqWindowSize)
    private val fineHz: DoubleArray
    private val fineGoertzels: List<Goertzel>
    /** Fine bins within this index distance of the peak are part of its main lobe. */
    private val guardBins: Int

    // Noise magnitude scales as 1/sqrt(window), so the short-window peak and the
    // long-window floor are on different scales for noise. This puts the floor on
    // the short window's scale before they are compared.
    private val floorScale: Double = sqrt(config.freqWindowSize.toDouble() / config.windowSize)

    private var notches: List<Double> = emptyList()
    private var active = false
    private var onCount = 0
    private var offCount = 0

    init {
        bandHz = spread(config.lowHz, config.highHz, config.detectionBins)
        bandGoertzels = bandHz.map { Goertzel(it, config.sampleRate, config.windowSize) }

        fineHz = spread(config.lowHz, config.highHz, config.spectrumBins)
        fineGoertzels = fineHz.map { Goertzel(it, config.sampleRate, config.freqWindowSize) }

        // The long window's first-null width, in fine bins, is how far the tone
        // leaks; the noise floor is taken from beyond it.
        val lobeHz = config.sampleRate.toDouble() / config.freqWindowSize
        val binSpacingHz = (config.highHz - config.lowHz) / (config.spectrumBins - 1)
        guardBins = (lobeHz / binSpacingHz).roundToInt().coerceIn(1, config.spectrumBins / 3)
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

        // Fast path: instantaneous peak in-band energy from the short window.
        var peakBand = 0.0
        for (i in bandGoertzels.indices) {
            if (isNotched(bandHz[i])) continue
            val m = bandGoertzels[i].magnitude(shortWindow)
            if (m > peakBand) peakBand = m
        }

        // Resolution path: fine spectrum, dominant frequency, and the in-band
        // noise floor (median of the band away from the peak).
        val fineMags = DoubleArray(fineGoertzels.size)
        var peakFine = 0.0
        var peakFineIdx = -1
        for (i in fineGoertzels.indices) {
            if (isNotched(fineHz[i])) {
                fineMags[i] = Double.NaN
                continue
            }
            val m = fineGoertzels[i].magnitude(freqWindow)
            fineMags[i] = m
            if (m > peakFine) {
                peakFine = m
                peakFineIdx = i
            }
        }
        val noise = inBandFloor(fineMags, peakFineIdx)
        // NaNs were only placeholders for notched bins; clear them for the view.
        for (i in fineMags.indices) if (fineMags[i].isNaN()) fineMags[i] = 0.0

        val contrast = peakBand / (noise * floorScale).coerceAtLeast(1e-9)
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

        val dominantHz = if (peakFineIdx >= 0) interpolatedHz(peakFineIdx, fineMags) else config.centerHz
        return Detection(active, dominantHz, peakBand, noise, fineMags)
    }

    /**
     * Median magnitude of the band excluding the peak and its main lobe, the
     * estimate of in-band noise. Falls back to all non-peak bins if the guard
     * leaves too few to be meaningful (a very narrow user band).
     */
    private fun inBandFloor(mags: DoubleArray, peakIdx: Int): Double {
        val kept = ArrayList<Double>(mags.size)
        for (i in mags.indices) {
            if (mags[i].isNaN()) continue
            if (peakIdx >= 0 && abs(i - peakIdx) <= guardBins) continue
            kept.add(mags[i])
        }
        if (kept.size < 3) {
            kept.clear()
            for (i in mags.indices) {
                if (mags[i].isNaN() || i == peakIdx) continue
                kept.add(mags[i])
            }
        }
        if (kept.isEmpty()) return 0.0
        kept.sort()
        val mid = kept.size / 2
        return if (kept.size % 2 == 1) kept[mid] else (kept[mid - 1] + kept[mid]) / 2.0
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
