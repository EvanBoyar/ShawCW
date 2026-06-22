package com.shawcw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shawcw.settings.ColorPalette
import com.shawcw.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists [Settings] with DataStore. The "listening" flag is deliberately not
 * persisted; the app starts idle and the user turns it on.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val flow: Flow<Settings> = store.data.map { prefs ->
        val defaults = Settings()
        Settings(
            listening = false,
            hapticEnabled = prefs[Keys.HAPTIC] ?: defaults.hapticEnabled,
            flashlightEnabled = prefs[Keys.FLASHLIGHT] ?: defaults.flashlightEnabled,
            colorEnabled = prefs[Keys.COLOR] ?: defaults.colorEnabled,
            centerHz = prefs[Keys.CENTER] ?: defaults.centerHz,
            lowHz = prefs[Keys.LOW] ?: defaults.lowHz,
            highHz = prefs[Keys.HIGH] ?: defaults.highHz,
            sensitivity = prefs[Keys.SENSITIVITY] ?: defaults.sensitivity,
            colorPalette = prefs[Keys.PALETTE]?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() }
                ?: defaults.colorPalette,
            showSpectrum = prefs[Keys.SHOW_SPECTRUM] ?: defaults.showSpectrum,
            vibrationNotchHz = prefs[Keys.NOTCHES]
                ?.split(",")
                ?.mapNotNull { it.trim().toDoubleOrNull() }
                ?: defaults.vibrationNotchHz,
        )
    }

    suspend fun save(settings: Settings) {
        store.edit { prefs ->
            prefs[Keys.HAPTIC] = settings.hapticEnabled
            prefs[Keys.FLASHLIGHT] = settings.flashlightEnabled
            prefs[Keys.COLOR] = settings.colorEnabled
            prefs[Keys.CENTER] = settings.centerHz
            prefs[Keys.LOW] = settings.lowHz
            prefs[Keys.HIGH] = settings.highHz
            prefs[Keys.SENSITIVITY] = settings.sensitivity
            prefs[Keys.PALETTE] = settings.colorPalette.name
            prefs[Keys.SHOW_SPECTRUM] = settings.showSpectrum
            prefs[Keys.NOTCHES] = settings.vibrationNotchHz.joinToString(",")
        }
    }

    private object Keys {
        val HAPTIC = booleanPreferencesKey("haptic")
        val FLASHLIGHT = booleanPreferencesKey("flashlight")
        val COLOR = booleanPreferencesKey("color")
        val CENTER = doublePreferencesKey("center_hz")
        val LOW = doublePreferencesKey("low_hz")
        val HIGH = doublePreferencesKey("high_hz")
        val SENSITIVITY = doublePreferencesKey("sensitivity")
        val PALETTE = stringPreferencesKey("color_palette")
        val SHOW_SPECTRUM = booleanPreferencesKey("show_spectrum")
        val NOTCHES = stringPreferencesKey("vibration_notches")
    }
}
