package com.shawcw.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.shawcw.feedback.FrequencyColor
import com.shawcw.settings.ColorPalette

/**
 * Maps a detected frequency to the on-screen feedback color for the chosen
 * palette. Center reads as the middle of each ramp, so sitting on the zero-beat
 * tone shows one steady color and drifting off shows a shift.
 */
fun toneColor(
    palette: ColorPalette,
    hz: Double,
    lowHz: Double,
    centerHz: Double,
    highHz: Double,
): Color {
    return when (palette) {
        ColorPalette.SPECTRUM -> {
            val hue = FrequencyColor.hueFor(hz, lowHz, centerHz, highHz)
            Color.hsv(hue, 0.85f, 1f)
        }
        ColorPalette.EMBER -> rampColor(EMBER_STOPS, FrequencyColor.position(hz, lowHz, highHz))
        ColorPalette.OCEAN -> rampColor(OCEAN_STOPS, FrequencyColor.position(hz, lowHz, highHz))
    }
}

/** A representative gradient for [palette], used for previews and legends. */
fun paletteStops(palette: ColorPalette): List<Color> = when (palette) {
    ColorPalette.SPECTRUM -> SPECTRUM_STOPS
    ColorPalette.EMBER -> EMBER_STOPS
    ColorPalette.OCEAN -> OCEAN_STOPS
}

private val SPECTRUM_STOPS = listOf(
    Color(0xFF2B6CF0),
    Color(0xFF18C29C),
    Color(0xFFF0B429),
    Color(0xFFE5484D),
)

private val EMBER_STOPS = listOf(
    Color(0xFF7A1F1F),
    Color(0xFFE5484D),
    Color(0xFFF0883E),
    Color(0xFFF5D90A),
)

private val OCEAN_STOPS = listOf(
    Color(0xFF0B3D91),
    Color(0xFF1457C7),
    Color(0xFF18A0C2),
    Color(0xFF3BE0C4),
)

private fun rampColor(stops: List<Color>, position: Float): Color {
    if (stops.size == 1) return stops.first()
    val scaled = position.coerceIn(0f, 1f) * (stops.size - 1)
    val index = scaled.toInt().coerceIn(0, stops.size - 2)
    val frac = scaled - index
    return lerp(stops[index], stops[index + 1], frac)
}
