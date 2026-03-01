package com.metapurge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DarkNavy = Color(0xFF0B1120)
val DarkNavyLight = Color(0xFF1E293B)
val SkyBlue = Color(0xFF38BDF8)
val SkyBlueDark = Color(0xFF0EA5E9)
val White = Color(0xFFFFFFFF)
val SlateGray = Color(0xFF94A3B8)
val SlateDark = Color(0xFF475569)

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    secondary = SkyBlueDark,
    tertiary = SkyBlue,
    background = DarkNavy,
    surface = White,
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = DarkNavy,
    onSecondary = DarkNavy,
    onTertiary = DarkNavy,
    onBackground = White,
    onSurface = DarkNavy,
    onSurfaceVariant = SlateDark,
    outline = SlateGray,
    error = Color(0xFFDC2626),
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = SkyBlueDark,
    secondary = SkyBlue,
    tertiary = SkyBlueDark,
    background = Color(0xFFF8FAFC),
    surface = White,
    surfaceVariant = Color(0xFFE2E8F0),
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = DarkNavy,
    onSurface = DarkNavy,
    onSurfaceVariant = SlateDark,
    outline = SlateGray,
    error = Color(0xFFDC2626),
    onError = White
)

@Composable
fun MetaPurgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val backgroundColor = if (darkTheme) DarkNavy else Color(0xFFF8FAFC)
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
