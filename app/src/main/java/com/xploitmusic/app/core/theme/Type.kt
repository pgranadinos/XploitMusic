package com.xploitmusic.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// XploitMusic Premium Typography
// Uses system sans-serif (Roboto on most Android devices)
// with carefully tuned weights, sizes, and letter spacing
private val XploitFontFamily = FontFamily.SansSerif

val Typography = Typography(
    // ── Display ──
    displayLarge = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, // Títulos de Playlist/Álbum
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),

    // ── Headlines ──
    headlineLarge = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // ── Titles ──
    titleLarge = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Bold, // Headers de secciones
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.SemiBold, // Nombres de canciones
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body ──
    bodyLarge = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Normal, // Nombres de artistas
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // ── Labels ──
    labelLarge = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = XploitFontFamily,
        fontWeight = FontWeight.Medium, // Tags, Duración
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
