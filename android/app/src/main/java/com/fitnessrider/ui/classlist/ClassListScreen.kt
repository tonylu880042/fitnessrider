package com.fitnessrider.ui.classlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.SegmentProgressBar
import com.fitnessrider.ui.components.TopNavBar

@Composable
fun ClassListScreen(
    classes: List<WorkoutClass>,
    onClassClick: (WorkoutClass) -> Unit,
    onEditClick: (WorkoutClass) -> Unit,
    onNewClassClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareClick: (WorkoutClass) -> Unit,
    onDeleteClick: (WorkoutClass) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        // Signature TopBar
        TopNavBar(
            title = "課表清單",
            leading = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
                }
            },
            trailing = {
                IconButton(onClick = onNewClassClick) {
                    Icon(Icons.Default.AddCircle, contentDescription = "新增課表", tint = Color.White)
                }
            }
        )

        if (classes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TopBarGreen
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "尚未建立任何飛輪課表",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "點擊右上角「+」開始編排課表",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(classes, key = { it.id }) { workoutClass ->
                    ClassCard(
                        workoutClass = workoutClass,
                        onPlayClick = { onClassClick(workoutClass) },
                        onEditClick = { onEditClick(workoutClass) },
                        onShareClick = { onShareClick(workoutClass) },
                        onDeleteClick = { onDeleteClick(workoutClass) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassCard(
    workoutClass: WorkoutClass,
    onPlayClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        // Upper Card (Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardHeaderBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workoutClass.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "⏱ ${workoutClass.formattedDuration} (${workoutClass.totalDurationMs / 1000} 秒)",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "🔥 ${workoutClass.estimatedCalories.toInt()} kcal",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, contentDescription = "分享課表包", tint = TextSecondary)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "刪除課表", tint = AccentRed)
            }
        }

        Divider(color = CardBorder)

        // Lower Card (Actions & Segments Preview)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "上課開騎", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Segments Preview Bar
            Column(modifier = Modifier.weight(1f)) {
                SegmentProgressBar(segments = workoutClass.segments, height = 10.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${workoutClass.segments.size} 首曲目・共 ${workoutClass.segments.sumOf { it.cues.size }} 個動作點",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Edit Button
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TopBarGreenDark),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TopBarGreenDark)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "編輯", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
