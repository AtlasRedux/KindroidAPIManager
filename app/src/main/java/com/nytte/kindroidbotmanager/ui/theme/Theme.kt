package com.nytte.kindroidbotmanager.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors matching the Windows dark theme from Main.cpp
val DarkBackground = Color(0xFF1E1E1E)
val DarkSurface = Color(0xFF2D2D2D)
val DarkSurfaceVariant = Color(0xFF383838)
val AccentBlue = Color(0xFF007ACC)
val TextPrimary = Color(0xFFDCDCDC)
val TextSecondary = Color(0xFFAAAAAA)
val ErrorRed = Color(0xFFCF6679)
val SuccessGreen = Color(0xFF4CAF50)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF555555)
)

@Composable
fun KindroidBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
