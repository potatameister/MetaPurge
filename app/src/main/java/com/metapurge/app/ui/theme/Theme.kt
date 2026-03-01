package com.metapurge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    secondary = SkyBlueDark,
    tertiary = SkyBlue,
    background = DarkNavy,
    surface = White,
    surfaceVariant = SlateGray,
    onPrimary = DarkNavy,
    onSecondary = DarkNavy,
    onTertiary = DarkNavy,
    onBackground = White,
    onSurface = DarkNavy,
    onSurfaceVariant = SlateDark,
    outline = SlateGray,
    error = SkyBlue,
    onError = White
)

@Composable
fun MetaPurgeTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkNavy.toArgb()
            window.navigationBarColor = DarkNavy.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
