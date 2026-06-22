package com.shawcw.engine

import android.content.Context
import com.shawcw.AppState
import com.shawcw.SpectrumState
import com.shawcw.ToneState
import com.shawcw.audio.AudioCapture
import com.shawcw.audio.AudioRecordCapture
import com.shawcw.dsp.DetectorConfig
import com.shawcw.dsp.ToneDetector
import com.shawcw.feedback.HapticFeedback
import com.shawcw.feedback.TorchFeedback
import com.shawcw.settings.Settings

/**
 * Wires capture to detection to feedback. The capture thread calls [onHop] for
 * every audio hop; the engine runs the detector and fans the result out to the
 * enabled outputs and to [AppState] for the UI.
 *
 * Feedback (haptic, torch) runs at the full hop rate so it tracks fast CW; the
 * UI flows are decimated to roughly 30 per second so 125 hops a second do not
 * thrash Compose.
 */
class DetectionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val haptic = HapticFeedback(appContext)
    private val torch = TorchFeedback(appContext)

    private var capture: AudioCapture? = null
    private var detector: ToneDetector? = null
    private var settings: Settings = Settings()
    private var uiTick = 0

    fun start(initial: Settings) {
        if (capture != null) return
        settings = initial

        val config = configFor(initial)
        capture = AudioRecordCapture(sampleRate = config.sampleRate, blockSize = config.hopSize)
        detector = ToneDetector(config).also {
            it.setNotches(initial.vibrationNotchHz)
            it.setSensitivity(initial.sensitivity)
        }
        capture?.start(::onHop)
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
            detector = ToneDetector(configFor(newSettings))
        }
        detector?.setNotches(newSettings.vibrationNotchHz)
        detector?.setSensitivity(newSettings.sensitivity)
    }

    private fun configFor(s: Settings): DetectorConfig =
        DetectorConfig(lowHz = s.lowHz, centerHz = s.centerHz, highHz = s.highHz)

    private fun onHop(hop: FloatArray) {
        val det = detector ?: return
        val result = det.process(hop)

        if (settings.hapticEnabled) haptic.setActive(result.isTone)
        if (settings.flashlightEnabled) torch.setActive(result.isTone)

        // On/off at the full hop rate so the color orb is as crisp as the flash.
        // The flow dedupes, so this only emits on dit edges.
        AppState.setToneActive(result.isTone)

        // Decimate the heavier UI updates; the signals above already ran at the
        // full hop rate.
        if (uiTick++ % UI_DECIMATION != 0) return

        // Level is the dominant bin relative to the noise floor, mapped onto
        // 0..1 across the on/off threshold range so the meter reads naturally.
        val ratio = if (result.noiseFloor > 0.0) result.magnitude / result.noiseFloor else 0.0
        val level = ((ratio - 1.0) / 8.0).coerceIn(0.0, 1.0).toFloat()

        AppState.setTone(
            ToneState(isTone = result.isTone, dominantHz = result.dominantHz, level = level),
        )
        AppState.setSpectrum(
            SpectrumState(
                frequencies = det.binFrequencies,
                magnitudes = result.magnitudes,
                noiseFloor = result.spectrumFloor,
            ),
        )
    }

    fun stop() {
        capture?.stop()
        capture = null
        detector = null
        haptic.setActive(false)
        torch.setActive(false)
        AppState.setToneActive(false)
        AppState.setTone(ToneState())
    }

    fun release() {
        stop()
        haptic.release()
        torch.release()
    }

    private companion object {
        const val UI_DECIMATION = 4
    }
}
