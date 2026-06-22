package com.shawcw.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shawcw.AppState
import com.shawcw.CalibrationState
import com.shawcw.feedback.VibrationCalibrator
import com.shawcw.service.ListeningService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* the UI reads permission state directly when it needs to act */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()
        setContent {
            ShawCWTheme {
                Surface(modifier = Modifier) {
                    ShawApp(
                        onStartListening = {
                            if (ensureMicPermission()) {
                                AppState.updateSettings { it.copy(listening = true) }
                                ListeningService.start(this)
                                true
                            } else {
                                false
                            }
                        },
                        onStopListening = {
                            AppState.updateSettings { it.copy(listening = false) }
                            ListeningService.stop(this)
                        },
                        hasMicPermission = { hasMicPermission() },
                        requestMic = { permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                    )
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissions.launch(needed)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureMicPermission(): Boolean {
        if (hasMicPermission()) return true
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        return false
    }
}

private enum class Screen { Home, Settings, Help }

@Composable
private fun ShawApp(
    onStartListening: () -> Boolean,
    onStopListening: () -> Unit,
    hasMicPermission: () -> Boolean,
    requestMic: () -> Unit,
) {
    val settings by AppState.settings.collectAsStateWithLifecycle()
    val tone by AppState.tone.collectAsStateWithLifecycle()
    val toneActive by AppState.toneActive.collectAsStateWithLifecycle()
    val spectrum by AppState.spectrum.collectAsStateWithLifecycle()
    val calibration by AppState.calibration.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calibrator = remember { VibrationCalibrator(context) }

    when (screen) {
        Screen.Home -> HomeScreen(
            settings = settings,
            tone = tone,
            toneActive = toneActive,
            spectrum = spectrum,
            onToggleListening = { wantOn ->
                if (wantOn) onStartListening() else onStopListening()
            },
            onToggleHaptic = { AppState.updateSettings { s -> s.copy(hapticEnabled = it) } },
            onToggleFlashlight = { AppState.updateSettings { s -> s.copy(flashlightEnabled = it) } },
            onToggleColor = { AppState.updateSettings { s -> s.copy(colorEnabled = it) } },
            onToggleSpectrum = { AppState.updateSettings { s -> s.copy(showSpectrum = it) } },
            onOpenSettings = { screen = Screen.Settings },
            onOpenHelp = { screen = Screen.Help },
        )

        Screen.Help -> HelpScreen(onBack = { screen = Screen.Home })

        Screen.Settings -> SettingsScreen(
            settings = settings,
            calibration = calibration,
            onSetLow = { v -> AppState.updateSettings { it.copy(lowHz = v.coerceAtMost(it.centerHz)) } },
            onSetCenter = { v ->
                AppState.updateSettings { it.copy(centerHz = v.coerceIn(it.lowHz, it.highHz)) }
            },
            onSetHigh = { v -> AppState.updateSettings { it.copy(highHz = v.coerceAtLeast(it.centerHz)) } },
            onSetPalette = { p -> AppState.updateSettings { it.copy(colorPalette = p) } },
            onToggleHaptic = { AppState.updateSettings { s -> s.copy(hapticEnabled = it) } },
            onToggleFlashlight = { AppState.updateSettings { s -> s.copy(flashlightEnabled = it) } },
            onToggleColor = { AppState.updateSettings { s -> s.copy(colorEnabled = it) } },
            onCalibrate = {
                if (!hasMicPermission()) {
                    requestMic()
                    AppState.setCalibration(CalibrationState.Failed("Microphone permission needed"))
                } else {
                    AppState.setCalibration(CalibrationState.Running)
                    scope.launch {
                        runCatching { calibrator.calibrate() }
                            .onSuccess { freqs ->
                                AppState.updateSettings { it.copy(vibrationNotchHz = freqs) }
                                AppState.setCalibration(CalibrationState.Done(freqs))
                            }
                            .onFailure {
                                AppState.setCalibration(
                                    CalibrationState.Failed("Calibration failed, try again"),
                                )
                            }
                    }
                }
            },
            onClearNotches = {
                AppState.updateSettings { it.copy(vibrationNotchHz = emptyList()) }
                AppState.setCalibration(CalibrationState.Idle)
            },
            onBack = { screen = Screen.Home },
        )
    }
}
