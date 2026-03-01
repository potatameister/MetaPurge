package com.metapurge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkNavy = Color(0xFF0B1120)
val DarkNavyLight = Color(0xFF1E293B)
val SkyBlue = Color(0xFF38BDF8)
val SkyBlueDark = Color(0xFF0EA5E9)
val White = Color(0xFFFFFFFF)
val SlateGray = Color(0xFF94A3B8)
val SlateDark = Color(0xFF475569)
val LightGray = Color(0xFFF1F5F9)

private val AppColorScheme = lightColorScheme(
    primary = DarkNavy,
    secondary = DarkNavyLight,
    tertiary = SkyBlue,
    background = LightGray,
    surface = White,
    surfaceVariant = LightGray,
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
