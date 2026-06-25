package com.shawcw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawcw.BuildConfig
import com.shawcw.R
import com.shawcw.SpectrumState
import com.shawcw.ToneState
import com.shawcw.settings.Settings
import com.shawcw.ui.components.ColorOrb
import com.shawcw.ui.components.ListenButton
import com.shawcw.ui.components.SpectrumView
import kotlinx.coroutines.delay

private const val DONATE_URL = "https://buymeacoffee.com/elbow"

// Hold the last detected frequency on screen briefly after the tone stops, so a
// single dit does not leave the readout flickering between the value and "Listening".
private const val FREQUENCY_HOLD_MS = 1500L

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
    onToggleSpectrum: (Boolean) -> Unit,
    onSetSensitivity: (Double) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val activeColor = toneColor(
        settings.colorPalette,
        tone.dominantHz,
        settings.lowHz,
        settings.centerHz,
        settings.highHz,
    )
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_image),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp),
                    )
                },
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Shaw CW", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHelp) {
                        Icon(Icons.Filled.QuestionMark, contentDescription = "Help")
                    }
                    IconButton(onClick = { uriHandler.openUri(DONATE_URL) }) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Support the app", tint = Color(0xFFE5709A))
                    }
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
                .padding(padding),
        ) {
            // Everything above the listen button scrolls; the button itself is
            // pinned to the bottom so it is always reachable, including in landscape.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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

                if (settings.showSpectrum) {
                    SpectrumView(
                        spectrum = spectrum,
                        barColor = MaterialTheme.colorScheme.primary,
                        floorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutputChips(
                    settings = settings,
                    onToggleHaptic = onToggleHaptic,
                    onToggleFlashlight = onToggleFlashlight,
                    onToggleColor = onToggleColor,
                    onToggleSpectrum = onToggleSpectrum,
                )

                SensitivitySlider(
                    value = settings.sensitivity,
                    onChange = onSetSensitivity,
                )
            }

            ListenButton(
                listening = settings.listening,
                onClick = { onToggleListening(!settings.listening) },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ToneReadout(tone: ToneState, listening: Boolean) {
    // Latch the most recent frequency and keep it on screen for a moment after the
    // tone drops, so the readout stays steady through the gaps between elements.
    var heldHz by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listening, tone.isTone, tone.dominantHz) {
        when {
            !listening -> heldHz = null
            tone.isTone -> heldHz = tone.dominantHz.toInt()
            heldHz != null -> {
                delay(FREQUENCY_HOLD_MS)
                heldHz = null
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val headline = when {
            !listening -> "Idle"
            heldHz != null -> "$heldHz Hz"
            else -> "Listening"
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (heldHz != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (listening) "Tap the button below to stop" else "Tap the button below to start",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SensitivitySlider(
    value: Double,
    onChange: (Double) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Sensitivity", style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).toInt()}%", color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat().coerceIn(0f, 1f),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun OutputChips(
    settings: Settings,
    onToggleHaptic: (Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onToggleColor: (Boolean) -> Unit,
    onToggleSpectrum: (Boolean) -> Unit,
) {
    // Each chip takes an equal share of the width so all four icon-plus-label
    // chips fit on one row, on any screen size and in landscape.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutputChip(
            selected = settings.hapticEnabled,
            onClick = { onToggleHaptic(!settings.hapticEnabled) },
            label = "Haptic",
            icon = Icons.Filled.Vibration,
            modifier = Modifier.weight(1f),
        )
        OutputChip(
            selected = settings.flashlightEnabled,
            onClick = { onToggleFlashlight(!settings.flashlightEnabled) },
            label = "Flash",
            icon = Icons.Filled.Bolt,
            modifier = Modifier.weight(1f),
        )
        OutputChip(
            selected = settings.colorEnabled,
            onClick = { onToggleColor(!settings.colorEnabled) },
            label = "Color",
            icon = Icons.Filled.Palette,
            modifier = Modifier.weight(1f),
        )
        OutputChip(
            selected = settings.showSpectrum,
            onClick = { onToggleSpectrum(!settings.showSpectrum) },
            label = "EQ",
            icon = Icons.Filled.BarChart,
            modifier = Modifier.weight(1f),
        )
    }
}

// A compact toggle chip that mirrors the Material FilterChip look (teal fill when
// on, outlined grey when off) but uses tight padding so an icon plus a full label
// always fit inside an equal-width quarter of the row, with no truncation.
@Composable
private fun OutputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}
