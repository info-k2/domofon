package com.domofon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF00391A),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0B1220),
    surface = Color(0xFF121A2A),
    onBackground = Color(0xFFE8EEF8),
    onSurface = Color(0xFFE8EEF8),
    error = Color(0xFFFF8A80),
)

@Composable
fun DomofonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
