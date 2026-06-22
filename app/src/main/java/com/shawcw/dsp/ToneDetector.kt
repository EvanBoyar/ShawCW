package com.shawcw.dsp

import kotlin.math.abs
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
    /**
     * Window for the spectrum display and the dominant-frequency (color)
     * estimate. Longer than [freqWindowSize] on purpose: the floor window is kept
     * short so the contrast decision stays fast and robust, but the display wants
     * fine frequency resolution. At 16 kHz, 2048 resolves about 8 Hz, so the
     * spectrum bins land on distinct frequencies instead of collapsing into
     * groups that move as one, and the color tracks small drifts. This window
     * drives no on/off timing, so its length costs nothing there.
     */
    val spectrumWindowSize: Int = 2048,
    val hopSize: Int = 128,
    val lowHz: Double = 500.0,
    val centerHz: Double = 600.0,
    val highHz: Double = 700.0,
    /** In-band bins on the short window, for the peak energy. */
    val detectionBins: Int = 16,
    /** Fine in-band bins on the spectrum window, for the frequency estimate and spectrum. */
    val spectrumBins: Int = 24,
    /**
     * Tone turns on when the strongest in-band bin is at least this many times
     * the median of the rest of the band (the bins away from the peak). A CW
     * tone is one sharp bin above an otherwise flat band; broadband or filtered
     * static fills the whole band, so its peak barely exceeds the rest. Because
     * the reference is inside the band, this holds whatever the receiver's audio
     * filter does to the noise shape, and at any volume.
     *
     * This holds against synthetic tones (contrast 10 to 20) and real mic-captured
     * CW alike, but only because the floor is the tracked between-dit background
     * (see [floorRiseRate]); against the raw per-hop floor a real dit, whose
     * keying sidebands inflate the floor as the tone peaks, never cleared it.
     */
    val onContrast: Double = 5.0,
    /** Tone turns off when contrast drops below this, giving hysteresis. */
    val offContrast: Double = 3.0,
    /**
     * Minimum instant spectral peakedness (peak over the untracked in-band floor)
     * required to turn on. A narrow CW tone is one sharp bin far above the in-band
     * median, so its instant ratio is high; a broadband percussive impulse (a
     * table tap) is a flat spectrum, ratio near 1, and is rejected. Checked only
     * on the on transition, never to stay on, so a sustained dah whose keying
     * sidelobes raise the instant floor is not dropped. Kept below [onContrast] so
     * it never becomes the binding on condition for a real tone.
     */
    val onInstantFlatness: Double = 1.8,
    /**
     * Consecutive hops above the on threshold before reporting a tone. White
     * noise momentarily fakes the in-band contrast for at most two hops in a row;
     * a real CW element holds it for dozens. Three hops (24 ms at the default
     * hop) clears that noise ceiling while still catching dits well past 40 wpm.
     */
    val minOnHops: Int = 3,
    /** Consecutive hops below the off threshold before clearing a tone. */
    val minOffHops: Int = 2,
    /**
     * Element on/off keys on an adaptive threshold floating between the running
     * high and low of the in-band peak envelope, the way a CW reader does. This
     * self-adapts to keying depth, which on real audio varies enormously: a clean
     * signal drops nearly to silence between elements, but a mic hearing a radio
     * speaker in a room hears the tone ring through the gaps, so the peak only
     * dips part way. A fixed release fraction handles one or the other, not both;
     * a floating threshold tracks either.
     *
     * [envRate] is how fast each envelope edge relaxes toward the peak when the
     * peak is on the far side of it (the high edge decaying down, the low edge
     * rising up); both attack instantly toward a new extreme. [keyFraction] places
     * the threshold this far up from the low toward the high. An element is on
     * while the peak is above the threshold and off below it.
     */
    val envRate: Double = 0.03,
    /** Where the keying threshold sits between the envelope low and high. */
    val keyFraction: Double = 0.4,
    /**
     * Minimum envelope depth, as a fraction of the high, for the keying threshold
     * to apply at all. Below it the signal is treated as unmodulated (a steady
     * carrier or a held key), which stays on for as long as the contrast says a
     * signal is present, rather than chattering on envelope noise.
     */
    val minKeyDepth: Double = 0.25,
    /**
     * Absolute magnitude the peak bin must reach before any detection. Guards
     * against dividing near-zero magnitudes in digital silence; real room noise
     * sits well above this, so it is not a loudness gate.
     */
    val absoluteGate: Double = 0.0008,
    /** Half-width in Hz of frequencies notched out (the vibration signature). */
    val notchHalfWidthHz: Double = 8.0,
    /**
     * How fast the tracked noise floor rises toward the per-hop estimate, per
     * hop. The floor follows downward instantly but upward at this rate, so a
     * passing dit (and the keying sidebands it sprays across the band, which
     * inflate the raw in-band estimate right when the tone is loudest) does not
     * drag the floor up with it, while a real, sustained rise in the background
     * is followed within a few hundred milliseconds. Without this, the raw floor
     * tracks the tone and the contrast never climbs; with it, a dit stands
     * cleanly against the between-dit background. At 0.05 the floor catches a
     * sustained change with a time constant near 160 ms (20 hops).
     */
    val floorRiseRate: Double = 0.05,
    /**
     * How fast the tracked floor rises while a tone is already detected. Much
     * slower than [floorRiseRate] so a held element is not chased away, but
     * nonzero so the floor still creeps up to a genuinely sustained background
     * (the phone's own vibration coupling into the mic during haptic output),
     * which lets a self-fed detection release once the real tone stops instead of
     * lingering. A hard hold (rate 0) makes the first tone after a quiet spell
     * linger for as long as the vibration keeps the peak up.
     */
    val activeFloorRiseRate: Double = 0.012,
    /**
     * How fast the tracked floor falls toward a lower per-hop estimate, per hop.
     * Faster than the rise so the floor settles onto a real gap between dits
     * quickly, but not instant: a one-hop trough in the raw estimate (which
     * broadband noise produces constantly) must not yank the floor down, or a
     * following noise peak reads as high contrast and false-triggers. A sustained
     * gap pulls it most of the way down within a few hops; a noise flicker barely
     * moves it.
     */
    val floorFallRate: Double = 0.5,
    /**
     * Minimum width in Hz of the span the noise floor is estimated over. The
     * contrast test needs clean reference bins that sit outside the tone's own
     * main lobe; at this sample rate the long window resolves about 31 Hz, so a
     * band narrower than a few main lobes has no room for a clean reference and
     * even a single pure tone fails to clear the floor. When the user's band is
     * narrower than this, the floor is read from a wider analysis span centered
     * on the band while the peak, frequency estimate, spectrum and color stay
     * bound to [lowHz, highHz]. The default band is already this wide, so the
     * default behavior is unchanged.
     */
    val minAnalysisHz: Double = 200.0,
    /** Bins spread across the analysis span for the in-band noise floor. */
    val referenceBins: Int = 24,
) {
    init {
        require(lowHz < highHz) { "lowHz must be below highHz" }
        require(centerHz in lowHz..highHz) { "centerHz must sit within the band" }
        require(detectionBins >= 5) { "need enough detection bins to estimate an in-band floor" }
        require(spectrumBins >= 3) { "need at least three spectrum bins" }
        require(referenceBins >= 5) { "need enough reference bins to estimate a floor" }
        require(spectrumWindowSize >= freqWindowSize) { "spectrum window should be at least the floor window" }
        require(hopSize in 1..windowSize) { "hopSize must be within 1..windowSize" }
        require(offContrast < onContrast) { "offContrast must be below onContrast for hysteresis" }
        require(onInstantFlatness in 0.0..onContrast) { "onInstantFlatness must be between 0 and onContrast" }
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
    /** Tracked in-band noise floor used for the on/off decision (floor window). */
    val noiseFloor: Double,
    /** Fine in-band magnitudes for the spectrum, aligned with [ToneDetector.binFrequencies]. */
    val magnitudes: DoubleArray,
    /**
     * Noise floor on the spectrum window's scale, for the spectrum display only.
     * The decision uses [noiseFloor]; this matches the displayed [magnitudes] so
     * the floor line sits correctly among the bars.
     */
    val spectrumFloor: Double,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Detection) return false
        return isTone == other.isTone &&
            dominantHz == other.dominantHz &&
            magnitude == other.magnitude &&
            noiseFloor == other.noiseFloor &&
            spectrumFloor == other.spectrumFloor &&
            magnitudes.contentEquals(other.magnitudes)
    }

    override fun hashCode(): Int {
        var result = isTone.hashCode()
        result = 31 * result + dominantHz.hashCode()
        result = 31 * result + magnitude.hashCode()
        result = 31 * result + noiseFloor.hashCode()
        result = 31 * result + spectrumFloor.hashCode()
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

    // Frequency and spectrum: a longer window over the user band, for fine
    // display resolution and the color estimate. Drives no on/off timing.
    private val spectrumWindow = FloatArray(config.spectrumWindowSize)
    private val fineHz: DoubleArray
    private val fineGoertzels: List<Goertzel>
    /** Spectrum bins within this many Hz of the peak are part of its main lobe. */
    private val spectrumGuardHz: Double

    // Noise floor: floor window over an analysis span at least minAnalysisHz wide.
    // A narrow user band cannot host a clean noise reference (its bins all fall
    // inside the tone's main lobe), so the floor is read from this wider span.
    private val freqWindow = FloatArray(config.freqWindowSize)
    private val refHz: DoubleArray
    private val refGoertzels: List<Goertzel>
    /** Reference bins within this many Hz of the peak are part of its main lobe. */
    private val guardHz: Double

    // Noise magnitude scales as 1/sqrt(window), so the short-window peak and the
    // long-window floor are on different scales for noise. This puts the floor on
    // the short window's scale before they are compared.
    private val floorScale: Double = sqrt(config.freqWindowSize.toDouble() / config.windowSize)

    private var notches: List<Double> = emptyList()
    private var active = false
    private var onCount = 0
    private var offCount = 0

    // Effective on/off contrast thresholds, scaled by the live user sensitivity.
    // Initialized from config so the default reproduces the configured behavior.
    private var effectiveOnContrast = config.onContrast
    private var effectiveOffContrast = config.offContrast

    // Tracked noise floor: follows the per-hop estimate down instantly, up slowly
    // (see [DetectorConfig.floorRiseRate]). Negative means uninitialized.
    private var trackedFloor = -1.0

    // Running high and low of the in-band peak envelope, for element on/off
    // timing. Negative means uninitialized.
    private var envHigh = -1.0
    private var envLow = -1.0

    init {
        bandHz = spread(config.lowHz, config.highHz, config.detectionBins)
        bandGoertzels = bandHz.map { Goertzel(it, config.sampleRate, config.windowSize) }

        fineHz = spread(config.lowHz, config.highHz, config.spectrumBins)
        fineGoertzels = fineHz.map { Goertzel(it, config.sampleRate, config.spectrumWindowSize) }
        spectrumGuardHz = config.sampleRate.toDouble() / config.spectrumWindowSize

        // Analysis span for the floor: at least minAnalysisHz wide, centered so it
        // covers the whole user band with room on both sides of the tone.
        val half = maxOf((config.highHz - config.lowHz) / 2.0, config.minAnalysisHz / 2.0)
        val anaLow = minOf(config.lowHz, config.centerHz - half).coerceAtLeast(MIN_FREQ_HZ)
        val anaHigh = maxOf(config.highHz, config.centerHz + half)
        refHz = spread(anaLow, anaHigh, config.referenceBins)
        refGoertzels = refHz.map { Goertzel(it, config.sampleRate, config.freqWindowSize) }

        // The long window's first-null width is how far the tone leaks; the noise
        // floor is taken from reference bins beyond it.
        guardHz = config.sampleRate.toDouble() / config.freqWindowSize
    }

    /** Center frequency of each fine spectrum bin, low to high. */
    val binFrequencies: DoubleArray
        get() = fineHz.copyOf()

    /** Frequencies (Hz) to ignore, for example the measured vibration tone. */
    fun setNotches(frequencies: List<Double>) {
        notches = frequencies
    }

    /**
     * Live detection sensitivity in [0, 1]; 0.5 reproduces the configured
     * contrasts. Higher values lower the on/off thresholds so weaker signals are
     * detected. Applied without rebuilding, so the tracked floor, peak envelope,
     * and on/off state survive a slider drag. Both thresholds scale by the same
     * factor, so [DetectorConfig.offContrast] stays below [DetectorConfig.onContrast].
     */
    fun setSensitivity(value: Double) {
        val scale = sensitivityScale(value.coerceIn(0.0, 1.0))
        effectiveOnContrast = config.onContrast * scale
        effectiveOffContrast = config.offContrast * scale
    }

    private fun isNotched(hz: Double): Boolean =
        notches.any { abs(it - hz) <= config.notchHalfWidthHz }

    /** Resets all state, for example when settings change. */
    fun reset() {
        shortWindow.fill(0f)
        freqWindow.fill(0f)
        spectrumWindow.fill(0f)
        active = false
        onCount = 0
        offCount = 0
        trackedFloor = -1.0
        envHigh = -1.0
        envLow = -1.0
    }

    /**
     * Feeds one hop of fresh samples and returns the decision for the windows
     * ending at this hop. [hop] should be [DetectorConfig.hopSize] samples; a
     * larger block is accepted and only its most recent window is kept.
     */
    fun process(hop: FloatArray): Detection {
        slideIn(shortWindow, hop)
        slideIn(freqWindow, hop)
        slideIn(spectrumWindow, hop)

        // Fast path: instantaneous peak in-band energy from the short window.
        var peakBand = 0.0
        for (i in bandGoertzels.indices) {
            if (isNotched(bandHz[i])) continue
            val m = bandGoertzels[i].magnitude(shortWindow)
            if (m > peakBand) peakBand = m
        }

        // Resolution path: fine spectrum and the dominant frequency, over the
        // user band, for the color feedback and the spectrum display.
        val fineMags = DoubleArray(fineGoertzels.size)
        var peakFine = 0.0
        var peakFineIdx = -1
        for (i in fineGoertzels.indices) {
            if (isNotched(fineHz[i])) {
                fineMags[i] = 0.0
                continue
            }
            val m = fineGoertzels[i].magnitude(spectrumWindow)
            fineMags[i] = m
            if (m > peakFine) {
                peakFine = m
                peakFineIdx = i
            }
        }
        val peakHz = if (peakFineIdx >= 0) fineHz[peakFineIdx] else -1.0
        val spectrumFloor = spectrumFloor(fineMags, peakFineIdx)

        // In-band noise floor: median of the wider analysis span away from the
        // peak's main lobe. The span reaches beyond a narrow user band so there
        // are clean reference bins; within the band it still counts non-peak bins
        // so filtered static (which fills the band) keeps the floor high.
        val noise = referenceFloor(peakHz)

        // Instant spectral peakedness: peak over the untracked floor. A narrow tone
        // is a sharp single bin far above the in-band median, so this is large; a
        // broadband percussive impulse (a table tap) is a flat spectrum, so it sits
        // near 1. Used only to gate turning on, never to stay on.
        val instContrast = peakBand / (noise * floorScale).coerceAtLeast(1e-9)

        // Track the floor: fall toward a lower estimate quickly, rise slowly, and
        // rise only very slowly while a tone is already on. A sustained element (a
        // dah, or a steady carrier) leaks into the reference bins the whole time it
        // sounds, so the raw estimate climbs under it; left to rise at the normal
        // rate the floor chases the tone up and the contrast decays until it
        // releases mid element. The slow while-active rate keeps the floor near the
        // pre-element background so a dah stays detected for its full length, but
        // unlike a hard hold it still creeps up to a genuinely sustained level,
        // such as the phone's own vibration coupling into the mic, so a detection
        // the haptic is feeding does not linger after the real tone has stopped.
        // `active` here is the previous hop's decision, made below.
        trackedFloor = when {
            trackedFloor < 0.0 -> noise
            noise < trackedFloor -> trackedFloor + (noise - trackedFloor) * config.floorFallRate
            active -> trackedFloor + (noise - trackedFloor) * config.activeFloorRiseRate
            else -> trackedFloor + (noise - trackedFloor) * config.floorRiseRate
        }

        val contrast = peakBand / (trackedFloor * floorScale).coerceAtLeast(1e-9)
        val gateOpen = peakBand >= config.absoluteGate

        // Peak envelope, tracked high and low. Each edge jumps instantly to a new
        // extreme and relaxes back toward the peak at envRate. The keying threshold
        // floats between them. The contrast decides whether a signal is present at
        // all (noise rejection); this threshold times the elements within it. The
        // two jobs are split on purpose, so neither the floor chasing a long dah
        // nor a held-low floor can drop a dah early or weld elements together.
        if (envHigh < 0.0) { envHigh = peakBand; envLow = peakBand }
        envHigh = if (peakBand > envHigh) peakBand else envHigh + (peakBand - envHigh) * config.envRate
        envLow = if (peakBand < envLow) peakBand else envLow + (peakBand - envLow) * config.envRate
        val threshold = envLow + (envHigh - envLow) * config.keyFraction
        val keyed = (envHigh - envLow) >= envHigh * config.minKeyDepth
        val aboveThreshold = peakBand >= threshold

        if (!active) {
            // To turn on, a signal must clear the contrast (not noise), be a sharp
            // peak right now rather than a flat broadband impulse (a table tap), and,
            // when keyed, be in an element rather than a gap.
            if (gateOpen &&
                contrast >= effectiveOnContrast &&
                instContrast >= config.onInstantFlatness &&
                (aboveThreshold || !keyed)
            ) {
                onCount++
                if (onCount >= config.minOnHops) {
                    active = true
                    offCount = 0
                }
            } else {
                onCount = 0
            }
        } else {
            val lostSignal = !gateOpen || contrast < effectiveOffContrast
            val betweenElements = keyed && !aboveThreshold
            if (lostSignal || betweenElements) {
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
        return Detection(active, dominantHz, peakBand, trackedFloor, fineMags, spectrumFloor)
    }

    /**
     * Median of the spectrum bins away from the peak's main lobe, on the spectrum
     * window's scale. For the display floor line only; the on/off decision uses
     * [referenceFloor]. Falls back to all non-peak bins for a very narrow band.
     */
    private fun spectrumFloor(mags: DoubleArray, peakIdx: Int): Double {
        val peakHz = if (peakIdx >= 0) fineHz[peakIdx] else -1.0
        val kept = ArrayList<Double>(mags.size)
        for (i in mags.indices) {
            if (isNotched(fineHz[i])) continue
            if (peakHz >= 0.0 && abs(fineHz[i] - peakHz) <= spectrumGuardHz) continue
            kept.add(mags[i])
        }
        if (kept.size < 3) {
            kept.clear()
            for (i in mags.indices) {
                if (isNotched(fineHz[i])) continue
                if (i == peakIdx) continue
                kept.add(mags[i])
            }
        }
        return median(kept)
    }

    /**
     * Estimate of the in-band noise the peak must clear. It is the larger of two
     * medians taken over the reference bins (each excluding the peak's main lobe,
     * [guardHz] of [peakHz], and any notched bins):
     *
     * - the wide median over the whole analysis span. This reaches beyond a
     *   narrow user band, so a single pure tone has clean low reference bins and
     *   clears it, and broadband static fills it and is rejected.
     * - the in-band median over only the reference bins inside [lowHz, highHz].
     *   Static confined to the band lights these even when the wider span is
     *   quiet, so it keeps the floor high and the static is rejected; a pure tone
     *   leaves them low. It abstains (returns nothing) when the band is too
     *   narrow to host enough bins outside the main lobe to judge occupancy, the
     *   fundamental resolution limit at this window length.
     *
     * A real tone is low on both; static of any width is high on at least one.
     */
    private fun referenceFloor(peakHz: Double): Double {
        val wide = ArrayList<Double>(refHz.size)
        val inBand = ArrayList<Double>(refHz.size)
        for (i in refHz.indices) {
            if (isNotched(refHz[i])) continue
            if (peakHz >= 0.0 && abs(refHz[i] - peakHz) <= guardHz) continue
            val m = refGoertzels[i].magnitude(freqWindow)
            wide.add(m)
            if (refHz[i] >= config.lowHz && refHz[i] <= config.highHz) inBand.add(m)
        }
        if (wide.size < 3) {
            // The guard swallowed almost everything (a tiny analysis span); fall
            // back to every non-notched bin but the peak itself.
            wide.clear()
            for (i in refHz.indices) {
                if (isNotched(refHz[i])) continue
                if (peakHz >= 0.0 && abs(refHz[i] - peakHz) < 1e-9) continue
                wide.add(refGoertzels[i].magnitude(freqWindow))
            }
        }
        val wideFloor = median(wide)
        val inBandFloor = if (inBand.size >= MIN_INBAND_FLOOR_BINS) median(inBand) else 0.0
        return maxOf(wideFloor, inBandFloor)
    }

    private fun median(values: ArrayList<Double>): Double {
        if (values.isEmpty()) return 0.0
        values.sort()
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
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
        /** Lowest frequency the analysis span is allowed to reach down to. */
        private const val MIN_FREQ_HZ = 50.0

        /**
         * Fewest in-band reference bins (outside the main lobe) needed to judge
         * whether the band is filled with static. Below this the band is too
         * narrow to tell a tone from band-filling hiss, so that test abstains.
         */
        private const val MIN_INBAND_FLOOR_BINS = 3

        /**
         * Contrast-threshold multiplier at the extremes of the sensitivity slider:
         * thresholds run from this many times the configured value (least
         * sensitive) down to its reciprocal (most sensitive), through 1.0 at the
         * 0.5 midpoint.
         */
        private const val SENS_RANGE = 2.0

        /**
         * Maps sensitivity [0, 1] to a contrast-threshold multiplier. Geometric so
         * equal slider travel is an equal ratio: 0.0 -> SENS_RANGE, 0.5 -> 1.0,
         * 1.0 -> 1 / SENS_RANGE.
         */
        private fun sensitivityScale(s: Double): Double = Math.pow(SENS_RANGE, 1.0 - 2.0 * s)

        /** [count] frequencies spread evenly across [from]..[to] inclusive. */
        private fun spread(from: Double, to: Double, count: Int): DoubleArray {
            if (count == 1) return doubleArrayOf((from + to) / 2.0)
            val step = (to - from) / (count - 1)
            return DoubleArray(count) { from + step * it }
        }
    }
}
