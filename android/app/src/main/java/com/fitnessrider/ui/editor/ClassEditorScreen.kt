package com.fitnessrider.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.model.*
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.TopNavBar
import java.util.UUID

@Composable
fun ClassEditorScreen(
    initialClass: WorkoutClass,
    onSave: (WorkoutClass) -> Unit,
    onCancel: () -> Unit
) {
    var workoutClass by remember { mutableStateOf(initialClass) }
    var selectedSegmentIndex by remember { mutableStateOf(0) }
    var isEditingCueDialogVisible by remember { mutableStateOf(false) }
    var currentEditingCue by remember { mutableStateOf<WorkoutCue?>(null) }

    val activeSegment = if (workoutClass.segments.isNotEmpty() && selectedSegmentIndex in workoutClass.segments.indices) {
        workoutClass.segments[selectedSegmentIndex]
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        // TopBar (#84BF09)
        TopNavBar(
            title = "",
            leading = {
                TextButton(onClick = onCancel) {
                    Text(text = "取消", color = Color.White, fontSize = 16.sp)
                }
            },
            trailing = {
                TextButton(onClick = { onSave(workoutClass) }) {
                    Text(text = "儲存", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            centerContent = {
                TextField(
                    value = workoutClass.title,
                    onValueChange = { workoutClass = workoutClass.copy(title = it) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        )

        // Sub Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardHeaderBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⏱ 總時長: ${workoutClass.formattedDuration}", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "🔥 預估消耗: ${workoutClass.estimatedCalories.toInt()} kcal", fontSize = 13.sp, color = TextSecondary)
        }

        Divider(color = CardBorder)

        // Horizontal Segment Cards
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(workoutClass.segments) { index, segment ->
                val isSelected = index == selectedSegmentIndex
                Column(
                    modifier = Modifier
                        .width(170.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) TopBarGreen.copy(alpha = 0.1f) else CardBackground)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) TopBarGreen else CardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedSegmentIndex = index }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier
                                .background(if (isSelected) TopBarGreen else CardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = segment.formattedDuration, fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = segment.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row {
                        Text(text = "${segment.effectiveBpm.toInt()} BPM", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${segment.cues.size} Cues", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Add Segment Card
            item {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, TopBarGreen, RoundedCornerShape(10.dp))
                        .clickable {
                            val newSeg = WorkoutSegment(
                                id = UUID.randomUUID().toString(),
                                classId = workoutClass.id,
                                orderIndex = workoutClass.segments.size,
                                title = "段落 ${workoutClass.segments.size + 1}",
                                durationMs = 300_000,
                                cues = listOf(
                                    WorkoutCue(
                                        id = UUID.randomUUID().toString(),
                                        offsetMs = 0,
                                        posture = PostureType.SEATED_FLAT,
                                        targetRpm = 85,
                                        resistanceLevel = "LEVEL 4",
                                        message = "坐姿平路巡航"
                                    )
                                )
                            )
                            workoutClass = workoutClass.copy(segments = workoutClass.segments + newSeg)
                            selectedSegmentIndex = workoutClass.segments.size - 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TopBarGreen)
                        Text(text = "新增段落", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TopBarGreen)
                    }
                }
            }
        }

        Divider(color = CardBorder)

        // Cue Section
        if (activeSegment != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "動作設定 (${activeSegment.title})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            currentEditingCue = WorkoutCue(
                                id = UUID.randomUUID().toString(),
                                segmentId = activeSegment.id,
                                offsetMs = 0,
                                posture = PostureType.STANDING_CLIMB,
                                targetRpm = 65,
                                resistanceLevel = "LEVEL 6",
                                message = "起立站姿爬坡"
                            )
                            isEditingCueDialogVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "標記 Cue 點", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(activeSegment.cues) { _, cue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cue.posture.localizedName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${cue.targetRpm} RPM",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(TopBarGreen, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = cue.resistanceLevel, fontSize = 12.sp, color = TextSecondary)
                                }
                                if (cue.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = cue.message, fontSize = 12.sp, color = TextSecondary)
                                }
                            }

                            val sec = cue.offsetMs / 1000
                            Text(
                                text = String.format("%02d:%02d", sec / 60, sec % 60),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )

                            IconButton(onClick = {
                                val updatedCues = activeSegment.cues.filter { it.id != cue.id }
                                val updatedSeg = activeSegment.copy(cues = updatedCues)
                                val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                                workoutClass = workoutClass.copy(segments = updatedSegs)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = AccentRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
