package com.shawcw.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BC0BE),
    background = Color(0xFF0B132B),
    surface = Color(0xFF1C2541),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C7C78),
)

@Composable
fun ShawCWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
