package com.dermatrack.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClinicalLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF176B5D),
    onPrimary = Color.White,
    secondary = Color(0xFF5F6F52),
    tertiary = Color(0xFF8A5A44),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4ECE8),
    onSurface = Color(0xFF17211E),
    onSurfaceVariant = Color(0xFF4B5C56),
    error = Color(0xFFB3261E),
)

@Composable
fun DermaTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClinicalLightScheme,
        typography = DermaTrackTypography,
        content = content,
    )
}
