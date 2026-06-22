package com.shawcw.settings

/** Color mapping used by the color feedback. */
enum class ColorPalette {
    /** Full hue sweep across the band (blue through green to red). */
    SPECTRUM,

    /** Warm ramp, dark red through orange to yellow. */
    EMBER,

    /** Cool ramp, deep blue through cyan to teal. */
    OCEAN,
}

/**
 * User facing settings. Defaults follow the project plan: a 600 Hz zero-beat
 * tone with a 500 to 700 Hz expected range.
 *
 * Persisted by SettingsStore. Keep this a plain data class so it stays trivial
 * to unit test and to serialize.
 */
data class Settings(
    val listening: Boolean = false,

    val hapticEnabled: Boolean = true,
    val flashlightEnabled: Boolean = false,
    val colorEnabled: Boolean = true,

    val centerHz: Double = 600.0,
    val lowHz: Double = 500.0,
    val highHz: Double = 700.0,

    val colorPalette: ColorPalette = ColorPalette.SPECTRUM,

    /** Measured frequencies of the phone's own vibration, to notch out. */
    val vibrationNotchHz: List<Double> = emptyList(),
)
