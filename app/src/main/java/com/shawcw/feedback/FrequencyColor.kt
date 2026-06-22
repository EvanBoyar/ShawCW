package com.shawcw.feedback

/**
 * Maps a detected tone frequency to a hue so the color splotch shifts as an
 * operator drifts off the zero-beat center. Pure and testable; the UI converts
 * the returned hue into an actual color.
 *
 * Center maps to the middle of the hue range, low and high to the ends, so
 * "on frequency" reads as one steady color and drift reads as a color shift.
 */
object FrequencyColor {

    /** Hue in degrees [0, 360) for [hz] given the user's band. */
    fun hueFor(hz: Double, lowHz: Double, centerHz: Double, highHz: Double): Float {
        val clamped = hz.coerceIn(lowHz, highHz)
        // Below center maps onto [200, 120] (blue to green),
        // above center onto [120, 0] (green to red).
        return if (clamped <= centerHz) {
            val t = if (centerHz == lowHz) 0.0 else (clamped - lowHz) / (centerHz - lowHz)
            lerp(200.0, 120.0, t).toFloat()
        } else {
            val t = if (highHz == centerHz) 0.0 else (clamped - centerHz) / (highHz - centerHz)
            lerp(120.0, 0.0, t).toFloat()
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)
}
