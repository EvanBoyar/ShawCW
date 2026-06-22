package com.shawcw.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep navy surfaces with a teal accent, carried over from the launcher icon.
// The app is dark only: it is meant to be glanced at in dim radio shacks and
// while the screen would otherwise be off.
private val ShawColors = darkColorScheme(
    primary = Color(0xFF5BC0BE),
    onPrimary = Color(0xFF03201F),
    primaryContainer = Color(0xFF1C6E6B),
    onPrimaryContainer = Color(0xFFB6F3F0),
    secondary = Color(0xFF9FB3D1),
    background = Color(0xFF080D1C),
    onBackground = Color(0xFFE3E8F5),
    surface = Color(0xFF0E1530),
    onSurface = Color(0xFFE3E8F5),
    surfaceVariant = Color(0xFF1C2541),
    onSurfaceVariant = Color(0xFFA9B4D0),
    outline = Color(0xFF3A4566),
)

@Composable
fun ShawCWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShawColors,
        content = content,
    )
}
