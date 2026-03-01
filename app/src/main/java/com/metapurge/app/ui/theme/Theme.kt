package com.metapurge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = DarkNavy,
    secondary = DarkNavyLight,
    tertiary = SkyBlue,
    background = Color(0xFFF1F5F9),
    surface = White,
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = White,
    onSecondary = White,
    onTertiary = DarkNavy,
    onBackground = DarkNavy,
    onSurface = DarkNavy,
    onSurfaceVariant = SlateDark,
    outline = SlateGray,
    error = Color(0xFFDC2626),
    onError = White
)

@Composable
fun MetaPurgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
