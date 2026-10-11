package com.xploitmusic.app.core.theme

import androidx.compose.ui.graphics.Color

// XploitMusic brand palette
val XploitSteelBlue = Color(0xFF4682B4)
val XploitSteelBlueLight = Color(0xFF6FA8D3)
val XploitSteelBlueDark = Color(0xFF2F5F87)

// Legacy accent names kept temporarily for screens still pending the UI migration.
val XploitViolet = XploitSteelBlue
val XploitDeepPurple = XploitSteelBlueDark
// Dark foundation: blue-black surfaces instead of the previous green-grey tint.
val XploitBlack = Color(0xFF070B0F)
val XploitDarkSurface = Color(0xFF111820)
val XploitCardDark = Color(0xFF18232D)

// Legacy compat
val AuraBlack = XploitBlack
val AuraDarkGrey = XploitDarkSurface
val AuraAccent = XploitViolet
val AuraWhite = Color(0xFFFFFFFF)

// Aura Design System (Spotify/Harmony Inspired)
val AuraBackground = XploitBlack
val AuraSurface = XploitDarkSurface
val AuraSurfaceVariant = XploitCardDark
val AuraPrimaryTheme = XploitSteelBlue
val AuraOnPrimaryTheme = Color(0xFF06131D)
val AuraTextPrimary = Color(0xFFF4F7FA)
val AuraTextSecondary = Color(0xFFA7B1BA)
val AuraTextDisabled = Color(0xFF68737D)
val AuraErrorTheme = Color(0xFFFF6B6B)

// Additional semantic colors for menus and navigation
val AuraNavUnselected = AuraTextSecondary
val AuraDivider = Color(0xFF2A3946)
