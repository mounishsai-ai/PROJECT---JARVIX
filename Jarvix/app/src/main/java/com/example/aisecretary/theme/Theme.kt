package com.example.aisecretary.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary          = ElectricViolet,
    onPrimary        = TextPrimary,
    primaryContainer = CardNavyElevated,
    secondary        = AquaAccent,
    onSecondary      = TextPrimary,
    background       = DeepNavy,
    onBackground     = TextPrimary,
    surface          = SurfaceNavy,
    onSurface        = TextPrimary,
    surfaceVariant   = CardNavy,
    onSurfaceVariant = TextSecondary,
    outline          = TextMuted,
    error            = ErrorRed,
    onError          = Color.White,
)

@Composable
fun AISecretaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content,
    )
}
