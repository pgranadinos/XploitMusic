package com.auramusic.app.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
    secondary = XploitCyan,
    onSecondary = Color(0xFF003544),
    tertiary = XploitGold,
    onTertiary = Color(0xFF3B2F00),
    background = AuraBackground,
    surface = AuraSurface,
    surfaceVariant = AuraSurfaceVariant,
    onBackground = AuraTextPrimary,
    onSurface = AuraTextPrimary,
    onSurfaceVariant = AuraTextSecondary,
    error = AuraErrorTheme,
    onError = AuraTextPrimary,
    outline = AuraDivider,
    outlineVariant = AuraDivider
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
