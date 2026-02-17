package com.rydius.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = TextOnPrimary,
    primaryContainer = Secondary,
    onPrimaryContainer = TextOnPrimary,
    secondary = Secondary,
    onSecondary = TextOnPrimary,
    secondaryContainer = Accent,
    tertiary = Success,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = TextOnPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = Secondary,
    onPrimary = Primary,
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = TextOnPrimary,
    secondary = Accent,
    onSecondary = Primary,
    secondaryContainer = SecondaryVariant,
    tertiary = Success,
    background = SurfaceDark,
    onBackground = TextDark,
    surface = CardDark,
    onSurface = TextDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = Error,
    onError = TextOnPrimary
)

@Composable
fun RideMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Primary.toArgb()
            window.navigationBarColor = Primary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
