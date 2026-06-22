package com.shawcw.feedback

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.shawcw.audio.AudioRecordCapture
import com.shawcw.dsp.SpectrumAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures the phone's own vibration signature. It runs the motor while
 * recording, builds a coarse spectrum of what the mic hears, and returns the
 * strongest frequencies. Those become detector notches so the haptic output
 * cannot feed back and self trigger detection.
 *
 * Must run while normal listening is stopped, since it needs the mic to itself.
 */
class VibrationCalibrator(context: Context) {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }

    /**
     * Vibrates and records for [durationMs], returning the detected vibration
     * frequencies. A short lead-in is discarded so the motor's spin-up does not
     * skew the measurement.
     */
    suspend fun calibrate(durationMs: Long = 1500L): List<Double> = withContext(Dispatchers.Default) {
        val capture = AudioRecordCapture()
        val analyzer = SpectrumAnalyzer(capture.sampleRate, capture.blockSize)
        val leadInMs = 250L

        val start = SystemClock.elapsedRealtime()
        capture.start { block ->
            if (SystemClock.elapsedRealtime() - start >= leadInMs) {
                analyzer.accumulate(block)
            }
        }
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
        )

        try {
            while (SystemClock.elapsedRealtime() - start < durationMs) {
                Thread.sleep(50)
            }
        } finally {
            vibrator.cancel()
            capture.stop()
        }

        analyzer.peaks(maxCount = 3)
    }
}
