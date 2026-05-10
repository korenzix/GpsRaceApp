package com.gpsrace.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Minimal MVP theme — no custom colors or typography yet.
 * Swap out darkColorScheme() for a full ColorScheme once design is finalized.
 */
private val RaceColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),        // Green — "go" color
    onPrimary = Color.White,
    secondary = Color(0xFFFF5722),      // Orange-red — finish/alert
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun GpsRaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RaceColorScheme,
        content = content
    )
}
