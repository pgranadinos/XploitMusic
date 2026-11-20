package com.xploitmusic.app.core.theme

import androidx.compose.ui.graphics.Color

// ── Noche Theme Tokens (Default) ──
val NocheBackground = Color(0xFF070B0F)
val NocheSurface = Color(0xFF111820)
val NocheCard = Color(0xFF18232D)
val NocheBorder = Color(0xFF2A3946)

val NocheTextPrimary = Color(0xFFF4F7FA)
val NocheTextSecondary = Color(0xFFA7B1BA)
val NocheTextMuted = Color(0xFF68737D)

val NochePrimary = Color(0xFF4682B4)
val NochePrimaryLight = Color(0xFF6FA8D3)
val NocheOnPrimary = Color(0xFF06131D)

// ── Arena Theme Tokens ──
val ArenaBackground = Color(0xFFF4ECE1)
val ArenaSurface = Color(0xFFF4ECE1)
val ArenaCard = Color(0xFFE9D8C2)
val ArenaBorder = Color(0xFFD4BFA5)

val ArenaTextPrimary = Color(0xFF2A2118)
val ArenaTextSecondary = Color(0xFF6B5B4B)

val ArenaPrimary = Color(0xFF2F5F87)
val ArenaPrimaryLight = Color(0xFF5B8FB9)
val ArenaOnPrimary = Color(0xFFFFFFFF)

// ── Legacy Compatibility Tokens ──
val XploitSteelBlue = NochePrimary
val XploitSteelBlueLight = NochePrimaryLight
val XploitSteelBlueDark = ArenaPrimary
val XploitBlack = NocheBackground
val XploitDarkSurface = NocheSurface
val XploitCardDark = NocheCard

val AuraBackground = NocheBackground
val AuraSurface = NocheSurface
val AuraSurfaceVariant = NocheCard
val AuraPrimaryTheme = NochePrimary
val AuraOnPrimaryTheme = NocheOnPrimary
val AuraTextPrimary = NocheTextPrimary
val AuraTextSecondary = NocheTextSecondary
val AuraTextDisabled = NocheTextMuted
val AuraErrorTheme = Color(0xFFFF6B6B)
val AuraDivider = NocheBorder
