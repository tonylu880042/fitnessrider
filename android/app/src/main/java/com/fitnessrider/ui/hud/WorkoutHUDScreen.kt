package com.fitnessrider.ui.hud

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.audio.AudioEngineManager
import com.fitnessrider.model.AppSettings
import com.fitnessrider.model.HandPosition
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.CircleProgressBar
import com.fitnessrider.ui.components.HandPositionBadge
import com.fitnessrider.ui.components.TopNavBar
import kotlinx.coroutines.delay

@Composable
fun WorkoutHUDScreen(
    workoutClass: WorkoutClass,
    audioManager: AudioEngineManager,
    onExitClick: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.getInstance(context) }

    // Screen Keep Awake effect
    DisposableEffect(Unit) {
        val activity = context as? Activity
        if (settings.keepScreenAwakeInHUD) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        audioManager.loadClass(workoutClass)
        audioManager.play()

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            audioManager.pause()
        }
    }

    val isPlaying by audioManager.isPlaying.collectAsState()
    val currentRate by audioManager.currentRate.collectAsState()
    val currentOffsetSec by audioManager.currentOffsetSeconds.collectAsState()
    val currentDurationSec by audioManager.currentDurationSeconds.collectAsState()
    val currentSegmentIndex by audioManager.currentSegmentIndex.collectAsState()

    var isDrawerOpen by remember { mutableStateOf(true) }
    var isShowingPostureInfo by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableStateOf(0) }

    val activeSegment = audioManager.currentSegment
    val currentMs = (currentOffsetSec * 1000).toInt()
    val activeCue = activeSegment?.cues?.lastOrNull { it.offsetMs <= currentMs } ?: activeSegment?.cues?.firstOrNull()
    val nextCue = activeSegment?.cues?.firstOrNull { it.offsetMs > currentMs }

    // Auto-cycle coaching reminders every 4 seconds
    LaunchedEffect(activeCue?.id) {
        while (true) {
            delay(4000L)
            reminderTick++
        }
    }

    val remainingCueSec = if (nextCue != null) {
        ((nextCue.offsetMs - currentMs) / 1000).coerceAtLeast(0)
    } else {
        (currentDurationSec - currentOffsetSec).toInt().coerceAtLeast(0)
    }

    val progressRatio = if (currentDurationSec > 0) {
        (currentOffsetSec / currentDurationSec).toFloat().coerceIn(0f, 1f)
    } else 0f

    val resistanceDelta = remember(activeCue, activeSegment) {
        if (activeCue != null && activeSegment != null) {
            val index = activeSegment.cues.indexOfFirst { it.id == activeCue.id }
            if (index > 0) {
                val prev = activeSegment.cues[index - 1]
                val currNum = activeCue.resistanceLevel.filter { it.isDigit() }.toIntOrNull() ?: 0
                val prevNum = prev.resistanceLevel.filter { it.isDigit() }.toIntOrNull() ?: 0
                when {
                    currNum > prevNum -> Pair("▲ 阻力加重 (+1)", true)
                    currNum < prevNum -> Pair("▼ 阻力減輕 (-1)", false)
                    else -> null
                }
            } else null
        } else null
    }

    val currentCoachingPrompt = remember(activeCue, reminderTick) {
        if (activeCue != null && activeCue.reminders.isNotEmpty()) {
            val idx = reminderTick % activeCue.reminders.size
            activeCue.reminders[idx]
        } else {
            activeCue?.posture?.trainingGoalDescription ?: "專注踩踏，維持穩定節拍"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        // Cockpit Header (#84BF09)
        TopNavBar(
            title = "",
            leading = {
                IconButton(onClick = onExitClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "退出課堂", tint = Color.White)
                }
                IconButton(onClick = { isDrawerOpen = !isDrawerOpen }) {
                    Icon(Icons.Default.Menu, contentDescription = "曲目清單", tint = Color.White)
                }
                Column {
                    Text(
                        text = workoutClass.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "預估卡路里: ${workoutClass.estimatedCalories.toInt()} kcal",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { audioManager.previousSegment() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = Color.White)
                    }
                    IconButton(
                        onClick = { audioManager.togglePlayPause() },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放/暫停",
                            tint = TopBarGreenDark
                        )
                    }
                    IconButton(onClick = { audioManager.nextSegment() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = Color.White)
                    }
                }
            }
        )

        // Cockpit Stage
        Row(modifier = Modifier.fillMaxSize()) {
            // Track Drawer (Left)
            if (isDrawerOpen) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(CardBackground)
                        .border(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardHeaderBackground)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "課堂播放清單",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${workoutClass.segments.size} 首",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                    HorizontalDivider(color = CardBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(workoutClass.segments) { index, segment ->
                            val isCurrent = index == currentSegmentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        audioManager.loadClass(workoutClass, index)
                                        audioManager.play()
                                    }
                                    .background(if (isCurrent) TopBarGreen.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(if (isCurrent) TopBarGreenDark else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color.White else TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = segment.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) TopBarGreenDark else TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${segment.formattedDuration} • ${segment.effectiveBpm.toInt()} BPM",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cockpit Center (CircleProgressBar + Controls)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Giant Circle Progress Bar (300dp)
                CircleProgressBar(
                    progress = progressRatio,
                    strokeWidth = 20.dp,
                    ringColor = TopBarGreen,
                    trackColor = CardBorder,
                    modifier = Modifier.size(300.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Posture with info click
                        if (activeCue != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isShowingPostureInfo = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activeCue.posture.localizedName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TopBarGreenDark
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "姿勢說明",
                                    tint = TopBarGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // GIANT RPM
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${activeCue?.targetRpm ?: 85}",
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RPM",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // BPM
                        if (activeSegment != null) {
                            Text(
                                text = "♫ ${activeSegment.effectiveBpm.toInt()} BPM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Countdown
                        val min = remainingCueSec / 60
                        val sec = remainingCueSec % 60
                        Text(
                            text = String.format("%02d:%02d", min, sec),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (remainingCueSec <= 5) AccentRed else TopBarGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Coaching Prompt Live Banner
                Surface(
                    color = TopBarGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = TopBarGreenDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentCoachingPrompt,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Info Strip: Hand Position, Resistance & Steppers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hand Position Badge
                    if (activeCue != null) {
                        HandPositionBadge(position = activeCue.handPosition, isCompact = false)
                    }

                    // Resistance Badge + Delta Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = TopBarGreenDark,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = activeCue?.resistanceLevel ?: "LEVEL 5",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        if (resistanceDelta != null) {
                            Surface(
                                color = if (resistanceDelta.second) AccentRed.copy(alpha = 0.15f) else TopBarGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = resistanceDelta.first,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (resistanceDelta.second) AccentRed else TopBarGreenDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Steppers (-2%, 100%, +2%)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { audioManager.adjustRatePercent(-2.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "-2%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Button(
                            onClick = { audioManager.resetRate() },
                            colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "100%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { audioManager.adjustRatePercent(2.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "+2%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Text(
                            text = "${(currentRate * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Next Cue Preview Banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            2.dp,
                            if (remainingCueSec <= 5) AccentRed else TopBarGreen,
                            RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = TopBarGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        if (nextCue != null) {
                            Text(text = "下一動作: ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(text = nextCue.posture.localizedName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${nextCue.targetRpm} RPM, ${nextCue.resistanceLevel}, ${nextCue.handPosition.shortTitle})",
                                fontSize = 13.sp,
                                color = TopBarGreenDark
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "$remainingCueSec 秒後轉換",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (remainingCueSec <= 5) AccentRed else TopBarGreenDark
                            )
                        } else {
                            Text(text = "此段落最後動作，堅持踩到底！", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Posture Purpose Dialog
        if (isShowingPostureInfo && activeCue != null) {
            AlertDialog(
                onDismissRequest = { isShowingPostureInfo = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = activeCue.posture.localizedName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "${activeCue.posture.defaultRpm} RPM", fontSize = 14.sp, color = TextSecondary)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "【訓練目標與效益】", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(text = activeCue.posture.trainingGoalDescription, fontSize = 14.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "【握把把位指引】", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HandPositionBadge(position = activeCue.handPosition, isCompact = true)
                            Text(text = activeCue.handPosition.gripDescription, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isShowingPostureInfo = false }) {
                        Text("關閉", color = TopBarGreenDark, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
