package com.shawcw

import com.shawcw.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live tone state surfaced to the UI. */
data class ToneState(
    val isTone: Boolean = false,
    val dominantHz: Double = 0.0,
    /** Strength of the dominant bin relative to the noise floor, 0..1. */
    val level: Float = 0f,
)

/**
 * Snapshot of the per-bin spectrum for the live visualization. A fresh array is
 * pushed each block, so reference inequality drives recomposition.
 */
data class SpectrumState(
    val frequencies: DoubleArray = DoubleArray(0),
    val magnitudes: DoubleArray = DoubleArray(0),
    val noiseFloor: Double = 0.0,
)

/** Progress of a vibration calibration run. */
sealed interface CalibrationState {
    data object Idle : CalibrationState
    data object Running : CalibrationState
    data class Done(val frequencies: List<Double>) : CalibrationState
    data class Failed(val reason: String) : CalibrationState
}

/**
 * Process wide state shared between the UI and the listening service. Kept as a
 * simple singleton of flows so both sides observe the same source of truth
 * without a DI framework. [settings] is persisted by SettingsStore.
 */
object AppState {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _tone = MutableStateFlow(ToneState())
    val tone: StateFlow<ToneState> = _tone.asStateFlow()

    // On/off only, pushed at the full hop rate so the color and flash track fast
    // CW. It is a bare Boolean, so the flow emits only on edges and stays cheap.
    private val _toneActive = MutableStateFlow(false)
    val toneActive: StateFlow<Boolean> = _toneActive.asStateFlow()

    private val _spectrum = MutableStateFlow(SpectrumState())
    val spectrum: StateFlow<SpectrumState> = _spectrum.asStateFlow()

    private val _calibration = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val calibration: StateFlow<CalibrationState> = _calibration.asStateFlow()

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update(transform)
    }

    /** Replaces settings wholesale, used when loading persisted values. */
    fun setSettings(settings: Settings) {
        _settings.value = settings
    }

    fun setTone(state: ToneState) {
        _tone.value = state
    }

    fun setToneActive(active: Boolean) {
        _toneActive.value = active
    }

    fun setSpectrum(state: SpectrumState) {
        _spectrum.value = state
    }

    fun setCalibration(state: CalibrationState) {
        _calibration.value = state
    }
}
