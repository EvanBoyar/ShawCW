package com.shawcw.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawcw.AppState
import com.shawcw.ToneState
import com.shawcw.feedback.FrequencyColor
import com.shawcw.service.ListeningService
import com.shawcw.settings.Settings

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* state is reflected by the system; nothing to do here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        setContent {
            ShawCWTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onListeningChange = { on ->
                            AppState.updateSettings { it.copy(listening = on) }
                            if (on) ListeningService.start(this) else ListeningService.stop(this)
                        },
                    )
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissions.launch(needed)
    }
}

@Composable
private fun MainScreen(onListeningChange: (Boolean) -> Unit) {
    val settings by AppState.settings.collectAsState()
    val tone by AppState.tone.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "ShawCW", fontSize = 28.sp)

        ColorSplotch(settings = settings, tone = tone)

        ToggleRow("Listening", settings.listening, onListeningChange)
        ToggleRow("Haptic", settings.hapticEnabled) { on ->
            AppState.updateSettings { it.copy(hapticEnabled = on) }
        }
        ToggleRow("Flashlight", settings.flashlightEnabled) { on ->
            AppState.updateSettings { it.copy(flashlightEnabled = on) }
        }
        ToggleRow("Color", settings.colorEnabled) { on ->
            AppState.updateSettings { it.copy(colorEnabled = on) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (tone.isTone) "Tone at ${tone.dominantHz.toInt()} Hz" else "No tone",
        )
    }
}

@Composable
private fun ColorSplotch(settings: Settings, tone: ToneState) {
    val color = if (settings.colorEnabled && tone.isTone) {
        val hue = FrequencyColor.hueFor(
            tone.dominantHz,
            settings.lowHz,
            settings.centerHz,
            settings.highHz,
        )
        Color.hsv(hue, 0.85f, 1.0f)
    } else {
        Color(0xFF24304F)
    }
    Spacer(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 18.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
