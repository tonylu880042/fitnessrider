package com.fitnessrider.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.model.WorkoutCue
import com.fitnessrider.theme.*
import kotlin.math.max

@Composable
fun WaveformCanvas(
    samples: FloatArray,
    cues: List<WorkoutCue>,
    durationMs: Int,
    currentOffsetMs: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val safeDuration = durationMs.coerceAtLeast(1000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    val seekMs = (ratio * safeDuration).toInt()
                    onSeek(seekMs)
                }
            }
            .pointerInput(safeDuration) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    val seekMs = (ratio * safeDuration).toInt()
                    onSeek(seekMs)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            val progress = (currentOffsetMs.toFloat() / safeDuration).coerceIn(0f, 1f)
            val playheadX = width * progress

            val totalSeconds = safeDuration / 1000
            val intervalSeconds = when {
                totalSeconds <= 120 -> 30
                totalSeconds <= 300 -> 60
                else -> 120
            }

            var sec = intervalSeconds
            val textStyle = TextStyle(
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )

            while (sec < totalSeconds) {
                val gridX = width * (sec.toFloat() / totalSeconds)
                drawLine(
                    color = CardBorder,
                    start = Offset(gridX, 0f),
                    end = Offset(gridX, height),
                    strokeWidth = 1f
                )

                val timeStr = String.format("%02d:%02d", sec / 60, sec % 60)
                val textLayout = textMeasurer.measure(timeStr, textStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = timeStr,
                    style = textStyle,
                    topLeft = Offset(gridX + 4f, 4f)
                )

                sec += intervalSeconds
            }

            if (samples.isNotEmpty()) {
                val barWidth = 2.5.dp.toPx()
                val spacing = 1.5.dp.toPx()
                val totalSlot = barWidth + spacing
                val barCount = (width / totalSlot).toInt().coerceAtLeast(1)
                val midY = height / 2f

                for (i in 0 until barCount) {
                    val sampleIdx = ((i.toFloat() / barCount) * samples.size).toInt().coerceIn(0, samples.size - 1)
                    val amplitude = samples[sampleIdx].coerceIn(0f, 1f)
                    val barHeight = max(4f, amplitude * (height * 0.72f))
                    val x = i * totalSlot
                    val isPlayed = x <= playheadX

                    val barColor = if (isPlayed) TopBarGreen else CardBorder.copy(alpha = 0.9f)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, midY - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(1.5f.dp.toPx(), 1.5f.dp.toPx())
                    )
                }
            }

            for (cue in cues) {
                val cueProgress = (cue.offsetMs.toFloat() / safeDuration).coerceIn(0f, 1f)
                val cueX = width * cueProgress

                drawLine(
                    color = TopBarGreenDark,
                    start = Offset(cueX, 0f),
                    end = Offset(cueX, height),
                    strokeWidth = 2.dp.toPx()
                )

                drawCircle(
                    color = TopBarGreenDark,
                    radius = 6.dp.toPx(),
                    center = Offset(cueX, 12.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(cueX, 12.dp.toPx())
                )

                val rpmText = "${cue.targetRpm}"
                val rpmStyle = TextStyle(
                    color = TopBarGreenDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                val rpmLayout = textMeasurer.measure(rpmText, rpmStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = rpmText,
                    style = rpmStyle,
                    topLeft = Offset(cueX - rpmLayout.size.width / 2f, height - 16.dp.toPx())
                )
            }

            drawLine(
                color = AccentRed,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = 2.5.dp.toPx()
            )

            val trianglePath = Path().apply {
                moveTo(playheadX - 6.dp.toPx(), 0f)
                lineTo(playheadX + 6.dp.toPx(), 0f)
                lineTo(playheadX, 8.dp.toPx())
                close()
            }
            drawPath(trianglePath, color = AccentRed)
        }
    }
}
