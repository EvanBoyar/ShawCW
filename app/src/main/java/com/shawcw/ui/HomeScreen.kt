package com.shawcw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawcw.SpectrumState
import com.shawcw.ToneState
import com.shawcw.settings.Settings
import com.shawcw.ui.components.ColorOrb
import com.shawcw.ui.components.ListenButton
import com.shawcw.ui.components.SpectrumView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: Settings,
    tone: ToneState,
    toneActive: Boolean,
    spectrum: SpectrumState,
    onToggleListening: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleColor: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val activeColor = toneColor(
        settings.colorPalette,
        tone.dominantHz,
        settings.lowHz,
        settings.centerHz,
        settings.highHz,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ShawCW", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                ColorOrb(
                    active = toneActive && settings.colorEnabled,
                    toneColor = if (settings.colorEnabled) activeColor else MaterialTheme.colorScheme.primary,
                )
            }

            ToneReadout(tone = tone, listening = settings.listening)

            SpectrumView(
                spectrum = spectrum,
                barColor = MaterialTheme.colorScheme.primary,
                floorColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutputChips(
                settings = settings,
                onToggleHaptic = onToggleHaptic,
                onToggleFlashlight = onToggleFlashlight,
                onToggleColor = onToggleColor,
            )

            Spacer(Modifier.height(4.dp))

            ListenButton(
                listening = settings.listening,
                onClick = { onToggleListening(!settings.listening) },
            )
        }
    }
}

@Composable
private fun ToneReadout(tone: ToneState, listening: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val headline = when {
            !listening -> "Idle"
            tone.isTone -> "${tone.dominantHz.toInt()} Hz"
            else -> "Listening"
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (tone.isTone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (listening) "Tap the button below to stop" else "Tap the button below to start",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutputChips(
    settings: Settings,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleColor: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        FilterChip(
            selected = settings.hapticEnabled,
            onClick = { onToggleHaptic(!settings.hapticEnabled) },
            label = { Text("Haptic") },
            leadingIcon = { Icon(Icons.Filled.Vibration, contentDescription = null) },
        )
        FilterChip(
            selected = settings.flashlightEnabled,
            onClick = { onToggleFlashlight(!settings.flashlightEnabled) },
            label = { Text("Flash") },
            leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
        )
        FilterChip(
            selected = settings.colorEnabled,
            onClick = { onToggleColor(!settings.colorEnabled) },
            label = { Text("Color") },
            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
        )
    }
}
