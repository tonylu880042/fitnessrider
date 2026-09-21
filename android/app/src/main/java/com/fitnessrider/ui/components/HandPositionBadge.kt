package com.fitnessrider.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.model.HandPosition
import com.fitnessrider.theme.CardBorder
import com.fitnessrider.theme.TextPrimary
import com.fitnessrider.theme.TextSecondary
import com.fitnessrider.theme.TopBarGreenDark

@Composable
fun HandPositionBadge(
    position: HandPosition,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    showTitle: Boolean = true
) {
    Row(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(
                horizontal = if (isCompact) 8.dp else 12.dp,
                vertical = if (isCompact) 4.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 10.dp)
    ) {
        // Handlebar Canvas
        Canvas(
            modifier = Modifier.size(
                width = if (isCompact) 36.dp else 48.dp,
                height = if (isCompact) 24.dp else 32.dp
            )
        ) {
            val w = size.width
            val h = size.height

            // Base outline
            val baseColor = CardBorder
            val baseStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

            // Outer handlebar path
            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.1f)
                lineTo(w * 0.1f, h * 0.65f)
                lineTo(w * 0.35f, h * 0.85f)
                lineTo(w * 0.65f, h * 0.85f)
                lineTo(w * 0.9f, h * 0.65f)
                lineTo(w * 0.9f, h * 0.1f)
            }
            drawPath(path, color = baseColor, style = baseStroke)

            // Highlight path
            val highlightColor = TopBarGreenDark
            val highlightStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)

            when (position) {
                HandPosition.POSITION_1 -> {
                    // Center horizontal crossbar
                    drawLine(
                        color = highlightColor,
                        start = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.85f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.85f),
                        strokeWidth = highlightStroke.width,
                        cap = StrokeCap.Round
                    )
                }
                HandPosition.POSITION_2 -> {
                    // Left and right corners
                    drawLine(
                        color = highlightColor,
                        start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.55f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.85f),
                        strokeWidth = highlightStroke.width,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = highlightColor,
                        start = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.55f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.85f),
                        strokeWidth = highlightStroke.width,
                        cap = StrokeCap.Round
                    )
                }
                HandPosition.POSITION_3 -> {
                    // Bullhorns
                    drawLine(
                        color = highlightColor,
                        start = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.45f),
                        strokeWidth = highlightStroke.width,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = highlightColor,
                        start = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.1f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.45f),
                        strokeWidth = highlightStroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        if (showTitle) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "把位",
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${position.value}",
                        fontSize = if (isCompact) 14.sp else 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TopBarGreenDark
                    )
                }
                Text(
                    text = when (position) {
                        HandPosition.POSITION_1 -> "平把中段"
                        HandPosition.POSITION_2 -> "橫桿轉折"
                        HandPosition.POSITION_3 -> "前端牛角"
                    },
                    fontSize = if (isCompact) 9.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }
}
