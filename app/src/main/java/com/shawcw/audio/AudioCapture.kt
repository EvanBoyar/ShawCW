package com.shawcw.audio

/**
 * Source of fixed size blocks of mono audio, normalized to roughly [-1, 1].
 *
 * The detector and feedback paths depend only on this interface. The current
 * implementation is [AudioRecordCapture]; a low latency Oboe/AAudio native
 * implementation can be added behind this same seam once the NDK is set up.
 */
interface AudioCapture {
    val sampleRate: Int
    val blockSize: Int

    /** True once capture has started and is delivering blocks. */
    val isRunning: Boolean

    /**
     * Starts capture and invokes [onBlock] on a background thread for each
     * block of [blockSize] samples. Call [stop] to release the resources.
     */
    fun start(onBlock: (FloatArray) -> Unit)

    fun stop()
}
