package com.fitnessrider.ui.classlist

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.SegmentProgressBar
import com.fitnessrider.ui.components.TopNavBar
import com.fitnessrider.util.VersionLifecycleManager

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
    val context = LocalContext.current
    var isWarningDismissed by rememberSaveable { mutableStateOf(false) }
    val remainingDays = remember { VersionLifecycleManager.getRemainingDays(context) }

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

        // Advance expiration warning banner (shows when 1 <= remainingDays <= 7)
        if (!isWarningDismissed && remainingDays in 1..7) {
            Surface(
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(1.dp, Color(0xFFFFEEBA), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "版本即將到期提醒",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF856404)
                        )
                        Text(
                            text = "目前測試版本將於 ${remainingDays} 天後到期。請提前更新以避免影響上課。",
                            fontSize = 12.sp,
                            color = Color(0xFF856404).copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(VersionLifecycleManager.updateUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("更新", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { isWarningDismissed = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "關閉提示",
                            tint = Color(0xFF856404),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

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
