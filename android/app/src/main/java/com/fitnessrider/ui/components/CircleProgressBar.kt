package com.fitnessrider.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnessrider.theme.CardBorder
import com.fitnessrider.theme.TopBarGreen

@Composable
fun CircleProgressBar(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
    ringColor: Color = TopBarGreen,
    trackColor: Color = CardBorder,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()

            // Background Track Circle
            drawCircle(
                color = trackColor,
                style = Stroke(width = strokePx)
            )

            // Foreground Progress Arc
            val sweepAngle = (progress.coerceIn(0f, 1f)) * 360f
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        content()
    }
}
