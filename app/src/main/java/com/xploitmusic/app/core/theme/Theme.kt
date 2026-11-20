package com.xploitmusic.app.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppTheme {
    NOCHE,
    ARENA
}

val NocheColorScheme = darkColorScheme(
    primary = NochePrimary,
    onPrimary = NocheOnPrimary,
    primaryContainer = NochePrimaryLight,
    onPrimaryContainer = NocheOnPrimary,
    secondary = NochePrimary,
    onSecondary = NocheOnPrimary,
    secondaryContainer = NocheCard,
    onSecondaryContainer = NocheTextPrimary,
    tertiary = NochePrimaryLight,
    onTertiary = NocheOnPrimary,
    background = NocheBackground,
    surface = NocheSurface,
    surfaceVariant = NocheCard,
    onBackground = NocheTextPrimary,
    onSurface = NocheTextPrimary,
    onSurfaceVariant = NocheTextSecondary,
    error = AuraErrorTheme,
    onError = NocheTextPrimary,
    outline = NocheBorder,
    outlineVariant = NocheBorder,
    surfaceTint = Color.Transparent
)

val ArenaColorScheme = lightColorScheme(
    primary = ArenaPrimary,
    onPrimary = ArenaOnPrimary,
    primaryContainer = ArenaPrimaryLight,
    onPrimaryContainer = ArenaTextPrimary,
    secondary = ArenaPrimary,
    onSecondary = ArenaOnPrimary,
    secondaryContainer = ArenaCard,
    onSecondaryContainer = ArenaTextPrimary,
    tertiary = ArenaPrimaryLight,
    onTertiary = ArenaOnPrimary,
    background = ArenaBackground,
    surface = ArenaSurface,
    surfaceVariant = ArenaCard,
    onBackground = ArenaTextPrimary,
    onSurface = ArenaTextPrimary,
    onSurfaceVariant = ArenaTextSecondary,
    error = AuraErrorTheme,
    onError = ArenaOnPrimary,
    outline = ArenaBorder,
    outlineVariant = ArenaBorder,
    surfaceTint = Color.Transparent
)

@Composable
fun XploitMusicTheme(
    themeMode: AppTheme = AppTheme.NOCHE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppTheme.NOCHE -> NocheColorScheme
        AppTheme.ARENA -> ArenaColorScheme
    }

    val isLight = themeMode == AppTheme.ARENA

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = XploitShapes,
        content = content
    )
}
