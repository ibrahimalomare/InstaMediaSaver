package com.example.instamediasaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFFFF6B9D),
    onPrimary = Color(0xFF1A0910),
    primaryContainer = Color(0xFF5D1936),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFF7BD0FF),
    onSecondary = Color(0xFF003549),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFF1F3F9),
    surface = Color(0xFF191C24),
    onSurface = Color(0xFFF1F3F9),
    surfaceVariant = Color(0xFF252A36),
    onSurfaceVariant = Color(0xFFC5C9D6),
    error = Color(0xFFFF8A80)
)

private val LightColorPalette = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3E001E),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF151820),
    surface = Color.White,
    onSurface = Color(0xFF151820),
    surfaceVariant = Color(0xFFE9ECF4),
    onSurfaceVariant = Color(0xFF454A59)
)

@Composable
fun InstaMediaSaverTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
