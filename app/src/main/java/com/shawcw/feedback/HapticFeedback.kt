package com.shawcw.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Drives the vibration motor for the duration of a detected tone. This is the
 * primary output, so it is also the source of the self feedback problem: the
 * motor's own sound leaks back into the mic. The detector notches that out (see
 * the vibration calibration setting); this class only starts and stops the buzz.
 */
class HapticFeedback(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private var on = false

    /** Mirrors [active] onto the motor, starting or stopping as it changes. */
    fun setActive(active: Boolean) {
        if (active == on) return
        on = active
        if (active) {
            // A long one-shot we cancel on tone end, rather than restarting each
            // block, so a steady tone reads as one continuous buzz.
            vibrator.vibrate(VibrationEffect.createOneShot(60_000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.cancel()
        }
    }

    fun release() {
        vibrator.cancel()
        on = false
    }
}
