package com.fitnessrider.theme

import androidx.compose.ui.graphics.Color

val TopBarGreen = Color(0xFF84BF09)
val TopBarGreenDark = Color(0xFF6EA306)
val TopBarGreenLight = Color(0xFFA2D636)

val CanvasWhite = Color(0xFFFFFFFF)
val CardBackground = Color(0xFFF7F9FB)
val CardHeaderBackground = Color(0xFFEEF2F6)
val CardBorder = Color(0xFFE2E8F0)

val TextPrimary = Color(0xFF212529)
val TextSecondary = Color(0xFF6C757D)
val TextMuted = Color(0xFFADB5BD)

val AccentRed = Color(0xFFE53E3E)
val AccentOrange = Color(0xFFED8936)
val AccentBlue = Color(0xFF3182CE)

val Zone1 = Color(0xFF4299E1)
val Zone2 = Color(0xFF48BB78)
val Zone3 = Color(0xFFECC94B)
val Zone4 = Color(0xFFED8936)
val Zone5 = Color(0xFFE53E3E)

fun colorForZone(zone: Int): Color = when (zone) {
    1 -> Zone1
    2 -> Zone2
    3 -> Zone3
    4 -> Zone4
    5 -> Zone5
    else -> TopBarGreen
}
