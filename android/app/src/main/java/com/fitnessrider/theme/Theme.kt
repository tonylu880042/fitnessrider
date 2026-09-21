package com.fitnessrider.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = TopBarGreen,
    onPrimary = CanvasWhite,
    primaryContainer = TopBarGreenLight,
    onPrimaryContainer = TopBarGreenDark,
    background = CanvasWhite,
    onBackground = TextPrimary,
    surface = CanvasWhite,
    onSurface = TextPrimary,
    outline = CardBorder
)

@Composable
fun FitnessRiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
