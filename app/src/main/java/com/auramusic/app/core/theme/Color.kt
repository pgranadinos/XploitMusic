package com.auramusic.app.core.theme

import androidx.compose.ui.graphics.Color

// XploitMusic Premium Palette
val XploitViolet = Color(0xFF9B7DFF)       // Brightened from 0xFF7C4DFF for better contrast
val XploitDeepPurple = Color(0xFF8B5CF6)   // Brightened from 0xFF651FFF for visibility
val XploitCyan = Color(0xFF00E5FF)
val XploitPink = Color(0xFFFF4081)
val XploitGold = Color(0xFFFFD740)

// Dark Mode
val XploitBlack = Color(0xFF0A0A0F)
val XploitDarkSurface = Color(0xFF161622)  // Slightly lighter for better layering
val XploitCardDark = Color(0xFF22223A)     // Raised from 0xFF1E1E2E for visible cards

// Light mode (fallback)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Legacy compat
val AuraBlack = XploitBlack
val AuraDarkGrey = XploitDarkSurface
val AuraAccent = XploitViolet
val AuraWhite = Color(0xFFFFFFFF)

// Aura Design System (Spotify/Harmony Inspired)
val AuraBackground = XploitBlack
val AuraSurface = XploitDarkSurface
val AuraSurfaceVariant = XploitCardDark
val AuraPrimaryTheme = XploitViolet
val AuraOnPrimaryTheme = Color(0xFFFFFFFF)
val AuraTextPrimary = Color(0xFFF0F0F5)    // Slightly off-white for comfortable reading
val AuraTextSecondary = Color(0xFFCCCCD6)  // Brightened from 0xFFB3B3B3 for better contrast
val AuraTextDisabled = Color(0xFF8888A0)   // Brightened from 0xFF535353 (was invisible!)
val AuraErrorTheme = Color(0xFFFF6B6B)     // Slightly brighter red for contrast

// Additional semantic colors for menus and navigation
val AuraNavUnselected = Color(0xFF9E9EB5)  // Clear unselected nav items
val AuraDivider = Color(0xFF2D2D45)        // Visible divider color
