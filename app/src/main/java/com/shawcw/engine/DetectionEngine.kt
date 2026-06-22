package com.shawcw.engine

import android.content.Context
import com.shawcw.AppState
import com.shawcw.ToneState
import com.shawcw.audio.AudioCapture
import com.shawcw.audio.AudioRecordCapture
import com.shawcw.dsp.DetectorConfig
import com.shawcw.dsp.ToneDetector
import com.shawcw.feedback.HapticFeedback
import com.shawcw.feedback.TorchFeedback
import com.shawcw.settings.Settings

/**
 * Wires capture to detection to feedback. The capture thread calls [onBlock]
 * for every audio block; the engine runs the detector and fans the result out
 * to the enabled outputs and to [AppState] for the UI.
 */
class DetectionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val haptic = HapticFeedback(appContext)
    private val torch = TorchFeedback(appContext)

    private var capture: AudioCapture? = null
    private var detector: ToneDetector? = null
    private var settings: Settings = Settings()

    fun start(initial: Settings) {
        if (capture != null) return
        settings = initial

        val cap = AudioRecordCapture()
        val det = ToneDetector(
            DetectorConfig(
                sampleRate = cap.sampleRate,
                blockSize = cap.blockSize,
                lowHz = initial.lowHz,
                centerHz = initial.centerHz,
                highHz = initial.highHz,
            ),
        ).also { it.setNotches(initial.vibrationNotchHz) }

        capture = cap
        detector = det
        cap.start(::onBlock)
    }

    /** Applies changed settings without tearing down capture where possible. */
    fun update(newSettings: Settings) {
        val old = settings
        settings = newSettings
        if (!newSettings.hapticEnabled) haptic.setActive(false)
        if (!newSettings.flashlightEnabled) torch.setActive(false)
        val bandChanged = old.lowHz != newSettings.lowHz ||
            old.centerHz != newSettings.centerHz ||
            old.highHz != newSettings.highHz
        if (bandChanged) {
            detector = ToneDetector(
                DetectorConfig(
                    sampleRate = capture?.sampleRate ?: 16_000,
                    blockSize = capture?.blockSize ?: 512,
                    lowHz = newSettings.lowHz,
                    centerHz = newSettings.centerHz,
                    highHz = newSettings.highHz,
                ),
            )
        }
        detector?.setNotches(newSettings.vibrationNotchHz)
    }

    private fun onBlock(block: FloatArray) {
        val result = detector?.process(block) ?: return

        if (settings.hapticEnabled) haptic.setActive(result.isTone)
        if (settings.flashlightEnabled) torch.setActive(result.isTone)

        AppState.setTone(ToneState(isTone = result.isTone, dominantHz = result.dominantHz))
    }

    fun stop() {
        capture?.stop()
        capture = null
        detector = null
        haptic.setActive(false)
        torch.setActive(false)
        AppState.setTone(ToneState())
    }

    fun release() {
        stop()
        haptic.release()
        torch.release()
    }
}
