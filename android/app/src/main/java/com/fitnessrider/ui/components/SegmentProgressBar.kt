package com.fitnessrider.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnessrider.model.WorkoutSegment
import com.fitnessrider.theme.colorForZone

@Composable
fun SegmentProgressBar(
    segments: List<WorkoutSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val totalDuration = segments.sumOf { it.durationMs }.coerceAtLeast(1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (seg in segments) {
            val weight = (seg.durationMs.toFloat() / totalDuration.toFloat()).coerceAtLeast(0.01f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(colorForZone(seg.intensityZone))
            )
        }
    }
}
