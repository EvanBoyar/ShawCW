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
)

/**
 * Process wide state shared between the UI and the listening service. Kept as a
 * simple singleton of flows so both sides observe the same source of truth
 * without a DI framework. Persistence of [settings] is a later concern.
 */
object AppState {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _tone = MutableStateFlow(ToneState())
    val tone: StateFlow<ToneState> = _tone.asStateFlow()

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update(transform)
    }

    fun setTone(state: ToneState) {
        _tone.value = state
    }
}
