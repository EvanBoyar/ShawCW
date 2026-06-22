package com.shawcw.dsp

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Single-frequency power estimator. Cheaper than a full FFT when we only care
 * about a handful of known frequencies, which is exactly the CW case: we watch
 * a narrow band around the operator's tone rather than the whole spectrum.
 *
 * One instance is tied to one target frequency, sample rate and block size.
 * Feed it a block of normalized samples with [magnitude].
 */
class Goertzel(
    val targetHz: Double,
    sampleRate: Int,
    blockSize: Int,
) {
    private val coeff: Double

    init {
        require(targetHz > 0.0) { "targetHz must be positive" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(blockSize > 0) { "blockSize must be positive" }
        // Snap the target to the nearest bin so the filter sits on a whole
        // number of cycles per block, which keeps the estimate stable.
        val k = Math.round(blockSize.toDouble() * targetHz / sampleRate).toInt()
        val omega = 2.0 * Math.PI * k / blockSize
        coeff = 2.0 * cos(omega)
    }

    /** Returns the magnitude (not power) of [targetHz] across [block]. */
    fun magnitude(block: FloatArray): Double {
        var s1 = 0.0
        var s2 = 0.0
        for (sample in block) {
            val s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        if (power <= 0.0) return 0.0
        // Normalize by block length so magnitude is independent of block size.
        return sqrt(power) / block.size
    }
}
