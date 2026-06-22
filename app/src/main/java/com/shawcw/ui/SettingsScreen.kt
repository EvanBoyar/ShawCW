package com.shawcw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawcw.CalibrationState
import com.shawcw.settings.ColorPalette
import com.shawcw.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    calibration: CalibrationState,
    onSetLow: (Double) -> Unit,
    onSetCenter: (Double) -> Unit,
    onSetHigh: (Double) -> Unit,
    onSetPalette: (ColorPalette) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleColor: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
    onClearNotches: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FrequencySection(settings, onSetLow, onSetCenter, onSetHigh)
            FeedbackSection(settings, onToggleHaptic, onToggleFlashlight, onToggleColor)
            ColorSection(settings, onSetPalette)
            VibrationSection(settings, calibration, onCalibrate, onClearNotches)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

private const val MIN_HZ = 300.0
private const val MAX_HZ = 1200.0

@Composable
private fun FrequencySection(
    settings: Settings,
    onSetLow: (Double) -> Unit,
    onSetCenter: (Double) -> Unit,
    onSetHigh: (Double) -> Unit,
) {
    SettingsCard("Tone frequencies") {
        Text(
            "The center is the tone you hear when the radio is zero beat. Low and high bound how far an operator may drift.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FrequencySlider("Low", settings.lowHz, MIN_HZ, settings.centerHz, onSetLow)
        FrequencySlider("Center", settings.centerHz, settings.lowHz, settings.highHz, onSetCenter)
        FrequencySlider("High", settings.highHz, settings.centerHz, MAX_HZ, onSetHigh)
    }
}

@Composable
private fun FrequencySlider(
    label: String,
    value: Double,
    min: Double,
    max: Double,
    onChange: (Double) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()} Hz", color = MaterialTheme.colorScheme.primary)
        }
        // Guard against an inverted range when two handles meet.
        val safeMax = if (max > min) max else min + 1.0
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), safeMax.toFloat()),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = min.toFloat()..safeMax.toFloat(),
        )
    }
}

@Composable
private fun FeedbackSection(
    settings: Settings,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleColor: (Boolean) -> Unit,
) {
    SettingsCard("Feedback") {
        ToggleRow("Haptic (vibration)", settings.hapticEnabled, onToggleHaptic)
        ToggleRow("Flashlight", settings.flashlightEnabled, onToggleFlashlight)
        ToggleRow("Color", settings.colorEnabled, onToggleColor)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSection(settings: Settings, onSetPalette: (ColorPalette) -> Unit) {
    SettingsCard("Color palette") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPalette.entries.forEach { palette ->
                FilterChip(
                    selected = settings.colorPalette == palette,
                    onClick = { onSetPalette(palette) },
                    label = { Text(palette.label()) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(paletteStops(settings.colorPalette))),
        ) {}
    }
}

private fun ColorPalette.label(): String = when (this) {
    ColorPalette.SPECTRUM -> "Spectrum"
    ColorPalette.EMBER -> "Ember"
    ColorPalette.OCEAN -> "Ocean"
}

@Composable
private fun VibrationSection(
    settings: Settings,
    calibration: CalibrationState,
    onCalibrate: () -> Unit,
    onClearNotches: () -> Unit,
) {
    SettingsCard("Vibration calibration") {
        Text(
            "Haptic feedback makes the phone buzz, and that buzz leaks into the mic. Calibrate to measure your phone's own vibration so it can be filtered out. Stop listening first; keep the phone on a quiet surface.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val notchText = if (settings.vibrationNotchHz.isEmpty()) {
            "No vibration profile yet"
        } else {
            "Filtering: " + settings.vibrationNotchHz.joinToString(", ") { "${it.toInt()} Hz" }
        }
        Text(notchText, style = MaterialTheme.typography.bodyMedium)

        when (calibration) {
            is CalibrationState.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Text("Measuring vibration...", style = MaterialTheme.typography.bodyMedium)
            }
            is CalibrationState.Failed -> Text(
                calibration.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCalibrate,
                enabled = !settings.listening && calibration !is CalibrationState.Running,
            ) {
                Text("Calibrate")
            }
            if (settings.vibrationNotchHz.isNotEmpty()) {
                OutlinedButton(onClick = onClearNotches) {
                    Text("Clear")
                }
            }
        }
        if (settings.listening) {
            Text(
                "Stop listening to calibrate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
