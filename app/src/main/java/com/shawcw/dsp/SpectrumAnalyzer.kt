package com.shawcw.dsp

/**
 * Accumulates a coarse magnitude spectrum across many blocks, then reports the
 * strongest frequencies. Used by vibration calibration to learn which tones the
 * phone's own motor produces so the detector can notch them out.
 */
class SpectrumAnalyzer(
    sampleRate: Int,
    blockSize: Int,
    minHz: Double = 150.0,
    maxHz: Double = 3500.0,
    stepHz: Double = 20.0,
) {
    private val frequencies: DoubleArray
    private val bins: List<Goertzel>
    private val sums: DoubleArray
    private var blocks = 0

    init {
        val count = ((maxHz - minHz) / stepHz).toInt() + 1
        frequencies = DoubleArray(count) { minHz + it * stepHz }
        bins = frequencies.map { Goertzel(it, sampleRate, blockSize) }
        sums = DoubleArray(count)
    }

    fun accumulate(block: FloatArray) {
        for (i in bins.indices) {
            sums[i] += bins[i].magnitude(block)
        }
        blocks++
    }

    /**
     * Returns up to [maxCount] frequencies whose average magnitude stands out
     * above the median by [prominence], strongest first. Local maxima only, so
     * neighboring bins of one peak are not reported separately.
     */
    fun peaks(maxCount: Int = 3, prominence: Double = 3.0): List<Double> {
        if (blocks == 0) return emptyList()
        val avg = DoubleArray(sums.size) { sums[it] / blocks }
        val median = avg.sorted()[avg.size / 2].coerceAtLeast(1e-9)

        val candidates = mutableListOf<Pair<Double, Double>>() // frequency to magnitude
        for (i in avg.indices) {
            val left = if (i > 0) avg[i - 1] else 0.0
            val right = if (i < avg.size - 1) avg[i + 1] else 0.0
            val isLocalMax = avg[i] >= left && avg[i] >= right
            if (isLocalMax && avg[i] > median * prominence) {
                candidates.add(frequencies[i] to avg[i])
            }
        }
        return candidates.sortedByDescending { it.second }.take(maxCount).map { it.first }
    }
}
