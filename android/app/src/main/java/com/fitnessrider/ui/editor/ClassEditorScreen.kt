package com.fitnessrider.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.fitnessrider.ui.components.HandPositionBadge
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
                            val newOffset = if (activeSegment.cues.isEmpty()) 0 else (activeSegment.cues.last().offsetMs + 30_000).coerceAtMost(activeSegment.durationMs)
                            currentEditingCue = WorkoutCue(
                                id = UUID.randomUUID().toString(),
                                segmentId = activeSegment.id,
                                offsetMs = newOffset,
                                posture = PostureType.STANDING_CLIMB,
                                handPosition = PostureType.STANDING_CLIMB.defaultHandPosition,
                                targetRpm = 65,
                                resistanceLevel = "LEVEL 6",
                                message = "起立站姿爬坡",
                                reminders = emptyList()
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
                                .clickable {
                                    currentEditingCue = cue
                                    isEditingCueDialogVisible = true
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hand Position Badge
                            HandPositionBadge(
                                position = cue.handPosition,
                                isCompact = true,
                                showTitle = false
                            )
                            Spacer(modifier = Modifier.width(10.dp))

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
                                if (cue.reminders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "💬 " + cue.reminders.joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = TopBarGreenDark,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                if (cue.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = cue.message, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
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

    if (isEditingCueDialogVisible && currentEditingCue != null) {
        CueEditorDialog(
            cue = currentEditingCue!!,
            onDismiss = {
                isEditingCueDialogVisible = false
                currentEditingCue = null
            },
            onSave = { savedCue ->
                if (activeSegment != null) {
                    val existingIndex = activeSegment.cues.indexOfFirst { it.id == savedCue.id }
                    val updatedCues = if (existingIndex >= 0) {
                        activeSegment.cues.toMutableList().also { it[existingIndex] = savedCue }
                    } else {
                        (activeSegment.cues + savedCue).sortedBy { it.offsetMs }
                    }
                    val updatedSeg = activeSegment.copy(cues = updatedCues)
                    val updatedSegs = workoutClass.segments.toMutableList().also { it[selectedSegmentIndex] = updatedSeg }
                    workoutClass = workoutClass.copy(segments = updatedSegs)
                }
                isEditingCueDialogVisible = false
                currentEditingCue = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CueEditorDialog(
    cue: WorkoutCue,
    onDismiss: () -> Unit,
    onSave: (WorkoutCue) -> Unit
) {
    var editingCue by remember { mutableStateOf(cue) }
    var isShowingRemindersPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "編輯動作提示 (Cue)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Time code
                val sec = editingCue.offsetMs / 1000
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("時間點", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = String.format("%02d:%02d (%d 秒)", sec / 60, sec % 60, sec),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TopBarGreenDark,
                        fontSize = 14.sp
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Posture Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("騎乘姿勢", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PostureType.entries.forEach { posture ->
                            val isSelected = posture == editingCue.posture
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) TopBarGreenDark else CardBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) TopBarGreenDark else CardBorder
                                ),
                                modifier = Modifier.clickable {
                                    editingCue = editingCue.copy(
                                        posture = posture,
                                        handPosition = posture.defaultHandPosition
                                    )
                                }
                            ) {
                                Text(
                                    text = posture.localizedName,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = editingCue.posture.trainingGoalDescription,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Hand Position Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("握把把位指引", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HandPosition.entries.forEach { pos ->
                            val isSelected = pos == editingCue.handPosition
                            Button(
                                onClick = { editingCue = editingCue.copy(handPosition = pos) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) TopBarGreenDark else CardBackground,
                                    contentColor = if (isSelected) Color.White else TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) TopBarGreenDark else CardBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(pos.shortTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HandPositionBadge(position = editingCue.handPosition, isCompact = true)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = editingCue.handPosition.gripDescription,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                HorizontalDivider(color = CardBorder)

                // RPM & Resistance
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("目標踏頻 (RPM)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (editingCue.targetRpm > 40) editingCue = editingCue.copy(targetRpm = editingCue.targetRpm - 5) }
                            ) {
                                Text("-5", fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                            }
                            Text(
                                text = "${editingCue.targetRpm}",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { if (editingCue.targetRpm < 150) editingCue = editingCue.copy(targetRpm = editingCue.targetRpm + 5) }
                            ) {
                                Text("+5", fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editingCue.resistanceLevel,
                        onValueChange = { editingCue = editingCue.copy(resistanceLevel = it) },
                        label = { Text("建議阻力") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = CardBorder)

                // Coaching Reminders Library Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("教練專業口訣提示 (${editingCue.reminders.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { isShowingRemindersPicker = true }) {
                            Text("選取口訣庫...", color = TopBarGreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (editingCue.reminders.isEmpty()) {
                        Text(
                            "尚未選取口訣，課堂中將預設輪播姿勢訓練目標",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            editingCue.reminders.forEach { reminder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBackground, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.FormatQuote,
                                        contentDescription = null,
                                        tint = TopBarGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = reminder,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            editingCue = editingCue.copy(reminders = editingCue.reminders - reminder)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "移除", tint = AccentRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CardBorder)

                // Message
                OutlinedTextField(
                    value = editingCue.message,
                    onValueChange = { editingCue = editingCue.copy(message = it) },
                    label = { Text("備註說明 (Message)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editingCue) },
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark)
            ) {
                Text("確定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )

    if (isShowingRemindersPicker) {
        RemindersPickerDialog(
            posture = editingCue.posture,
            initialReminders = editingCue.reminders,
            onDismiss = { isShowingRemindersPicker = false },
            onSave = { updated ->
                editingCue = editingCue.copy(reminders = updated)
                isShowingRemindersPicker = false
            }
        )
    }
}

@Composable
fun RemindersPickerDialog(
    posture: PostureType,
    initialReminders: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val selectedReminders = remember { mutableStateListOf<String>().apply { addAll(initialReminders) } }
    var customInput by remember { mutableStateOf("") }
    val categories = remember(posture) { CoachingReminderLibrary.categories(posture) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("選擇指導口訣", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Custom input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        placeholder = { Text("輸入自訂口訣...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = customInput.trim()
                            if (trimmed.isNotEmpty() && !selectedReminders.contains(trimmed)) {
                                selectedReminders.add(trimmed)
                                customInput = ""
                            }
                        },
                        enabled = customInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("加入", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = CardBorder)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        item {
                            Text(
                                text = category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TopBarGreenDark,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(category.reminders.size) { idx ->
                            val reminder = category.reminders[idx]
                            val isChecked = selectedReminders.contains(reminder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            selectedReminders.remove(reminder)
                                        } else {
                                            selectedReminders.add(reminder)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedReminders.contains(reminder)) selectedReminders.add(reminder)
                                        } else {
                                            selectedReminders.remove(reminder)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = TopBarGreenDark)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reminder,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedReminders.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark)
            ) {
                Text("完成 (${selectedReminders.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

