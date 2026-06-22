package com.shawcw.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * [AudioCapture] backed by [AudioRecord]. Simple and NDK free; good enough to
 * develop the detection and feedback paths against. Replace with an Oboe based
 * implementation when capture latency and jitter need tightening.
 *
 * Uses the VOICE_RECOGNITION source, which on most devices skips aggressive
 * voice processing (AGC, noise suppression) that would fight our own filtering.
 */
class AudioRecordCapture(
    override val sampleRate: Int = 16_000,
    override val blockSize: Int = 512,
) : AudioCapture {

    @Volatile
    override var isRunning: Boolean = false
        private set

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    override fun start(onBlock: (FloatArray) -> Unit) {
        if (isRunning) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minBuffer, blockSize * 2 * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        record = recorder
        recorder.startRecording()
        isRunning = true

        worker = thread(name = "ShawCW-capture") {
            val shorts = ShortArray(blockSize)
            val floats = FloatArray(blockSize)
            while (isRunning) {
                var read = 0
                while (read < blockSize && isRunning) {
                    val n = recorder.read(shorts, read, blockSize - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < blockSize) continue
                for (i in 0 until blockSize) {
                    floats[i] = shorts[i] / 32768f
                }
                onBlock(floats.copyOf())
            }
        }
    }

    override fun stop() {
        isRunning = false
        worker?.join(500)
        worker = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
    }
}
