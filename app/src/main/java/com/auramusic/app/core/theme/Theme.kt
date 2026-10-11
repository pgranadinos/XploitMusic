package com.auramusic.app.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AuraPrimaryTheme,
    onPrimary = AuraOnPrimaryTheme,
    primaryContainer = XploitSteelBlueDark,
    onPrimaryContainer = AuraTextPrimary,
    secondary = XploitSteelBlue,
    onSecondary = AuraOnPrimaryTheme,
    secondaryContainer = XploitCardDark,
    onSecondaryContainer = AuraTextPrimary,
    tertiary = XploitSteelBlueLight,
    onTertiary = AuraOnPrimaryTheme,
    background = AuraBackground,
    surface = AuraSurface,
    surfaceVariant = AuraSurfaceVariant,
    onBackground = AuraTextPrimary,
    onSurface = AuraTextPrimary,
    onSurfaceVariant = AuraTextSecondary,
    error = AuraErrorTheme,
    onError = AuraTextPrimary,
    outline = AuraDivider,
    outlineVariant = AuraDivider,
    surfaceTint = Color.Transparent
)

private val LightColorScheme = lightColorScheme(
    primary = XploitSteelBlueDark,
    onPrimary = Color.White,
    secondary = XploitSteelBlueDark,
    tertiary = XploitSteelBlueDark,
    background = Color(0xFFF4F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE5EDF3),
    onBackground = Color(0xFF101820),
    onSurface = Color(0xFF101820),
    onSurfaceVariant = Color(0xFF536270),
    outline = Color(0xFFBAC8D3),
    surfaceTint = Color.Transparent
)

@Composable
fun XploitMusicTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disabled to keep our curated palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = XploitShapes,
        content = content
    )
}
