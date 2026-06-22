package com.shawcw.settings

/**
 * User facing settings. Defaults follow the project plan: a 600 Hz zero-beat
 * tone with a 500 to 700 Hz expected range.
 *
 * This is an in memory model for now. Persisting it (DataStore) is a later
 * step; keep this a plain data class so it stays trivial to unit test.
 */
data class Settings(
    val listening: Boolean = false,

    val hapticEnabled: Boolean = true,
    val flashlightEnabled: Boolean = false,
    val colorEnabled: Boolean = true,

    val centerHz: Double = 600.0,
    val lowHz: Double = 500.0,
    val highHz: Double = 700.0,

    /** Measured frequencies of the phone's own vibration, to notch out. */
    val vibrationNotchHz: List<Double> = emptyList(),
)
