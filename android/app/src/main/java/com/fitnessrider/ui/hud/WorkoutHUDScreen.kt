package com.fitnessrider.ui.hud

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.fitnessrider.R
import com.fitnessrider.audio.AudioEngineManager
import com.fitnessrider.model.AppSettings
import com.fitnessrider.model.HandPosition
import com.fitnessrider.model.PostureType
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.model.WorkoutCue
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
    var isShowingExitConfirmDialog by remember { mutableStateOf(false) }
    var reminderTick by remember { mutableStateOf(0) }

    // Intercept hardware/gesture back press to prevent accidental class exit
    BackHandler(enabled = true) {
        isShowingExitConfirmDialog = true
    }

    // 5-second Next Cue warning pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "nextCuePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

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

    val totalClassMs = remember(workoutClass) {
        if (workoutClass.totalDurationMs > 0) workoutClass.totalDurationMs else workoutClass.segments.sumOf { it.durationMs }
    }
    val totalClassSec = (totalClassMs / 1000).coerceAtLeast(1)
    val priorSegmentsSec = remember(currentSegmentIndex, workoutClass) {
        workoutClass.segments.take(currentSegmentIndex).sumOf { it.durationMs } / 1000
    }
    val totalElapsedSec = (priorSegmentsSec + currentOffsetSec.toInt()).coerceIn(0, totalClassSec)
    val formattedTotalElapsed = String.format("%02d:%02d", totalElapsedSec / 60, totalElapsedSec % 60)
    val formattedTotalDuration = String.format("%02d:%02d", totalClassSec / 60, totalClassSec % 60)

    // Dynamic real-time calorie calculation
    val realtimeCalories = remember(totalElapsedSec, totalClassSec, workoutClass.estimatedCalories) {
        if (totalClassSec > 0) {
            val ratio = (totalElapsedSec.toDouble() / totalClassSec.toDouble()).coerceIn(0.0, 1.0)
            (workoutClass.estimatedCalories * ratio).toInt()
        } else 0
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
                IconButton(onClick = { isShowingExitConfirmDialog = true }) {
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
                        text = "即時消耗: $realtimeCalories / ${workoutClass.estimatedCalories.toInt()} kcal",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { audioManager.previousSegment() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = Color.White)
                    }
                    IconButton(onClick = { audioManager.seekBy(-10.0) }) {
                        Icon(Icons.Default.FastRewind, contentDescription = "快退10秒", tint = Color.White)
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
                    IconButton(onClick = { audioManager.seekBy(10.0) }) {
                        Icon(Icons.Default.FastForward, contentDescription = "快進10秒", tint = Color.White)
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

            // Cockpit Center (3-Column Layout: Hand Position Card | RPM Gauge | Posture Figure Card + Controls)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 3-Column Telemetry Row (Hand Position, Giant Gauge, Riding Posture with Elapsed Time on top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Left: Hand Position Card (Large Graphic)
                    HandPositionCockpitCard(cue = activeCue)

                    Spacer(modifier = Modifier.width(28.dp))

                    // Center: Giant Circle Progress Bar (300dp)
                    val currentZoneColor = colorForZone(activeSegment?.intensityZone ?: 2)
                    CircleProgressBar(
                        progress = progressRatio,
                        strokeWidth = 20.dp,
                        ringColor = currentZoneColor,
                        trackColor = CardBorder,
                        modifier = Modifier.size(300.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = currentZoneColor,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "ZONE ${activeSegment?.intensityZone ?: 2}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "目標轉速",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )

                            // GIANT RPM
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${activeCue?.targetRpm ?: 85}",
                                    fontSize = 82.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "RPM",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 14.dp)
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

                            Spacer(modifier = Modifier.height(4.dp))

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

                    Spacer(modifier = Modifier.width(28.dp))

                    // Right: Posture Figure Card with Total Workout Elapsed Time Badge directly on top
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Total Workout Elapsed Time Badge (課程進行時間)
                        Surface(
                            modifier = Modifier
                                .width(170.dp)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(TopBarGreen.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "課程進行時間",
                                        tint = TopBarGreenDark,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "課程時間",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = formattedTotalElapsed,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = " / $formattedTotalDuration",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(bottom = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Right: Posture Figure Card (Rider Illustration + Benefits info)
                        PostureFigureCockpitCard(
                            cue = activeCue,
                            onInfoClick = { isShowingPostureInfo = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Coaching Prompt Live Banner
                Surface(
                    color = TopBarGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Info Strip: Resistance Badge & Playback Speed Steppers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
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
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "-2%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Button(
                            onClick = { audioManager.resetRate() },
                            colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "100%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = { audioManager.adjustRatePercent(2.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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

                // Bottom Next Cue Preview Banner with <= 5s pulsing warning
                val isNextCueWarning = remainingCueSec <= 5 && nextCue != null
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            2.dp,
                            if (isNextCueWarning) AccentRed.copy(alpha = pulseAlpha) else TopBarGreen,
                            RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isNextCueWarning) AccentRed.copy(alpha = 0.10f * pulseAlpha) else Color.White,
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
                            tint = if (isNextCueWarning) AccentRed else TopBarGreenDark,
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
                                color = if (isNextCueWarning) AccentRed else TopBarGreenDark
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "$remainingCueSec 秒後轉換",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isNextCueWarning) AccentRed else TopBarGreenDark
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF505050), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = getPostureDrawableRes(activeCue.posture)),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Column {
                            Text(text = activeCue.posture.localizedName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TopBarGreenDark)
                            Text(text = "建議目標轉速: ${activeCue.posture.defaultRpm} RPM", fontSize = 13.sp, color = TextSecondary)
                        }
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
                        Text("我知道了", color = TopBarGreenDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }

        // Exit Confirmation Dialog
        if (isShowingExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { isShowingExitConfirmDialog = false },
                title = {
                    Text(
                        text = "退出課堂確認",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "確定要結束並退出課堂嗎？目前授課進度將不會儲存。",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isShowingExitConfirmDialog = false
                            onExitClick()
                        }
                    ) {
                        Text(
                            text = "結束課堂",
                            color = AccentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { isShowingExitConfirmDialog = false }
                    ) {
                        Text(
                            text = "繼續騎乘",
                            color = TopBarGreenDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun HandPositionCockpitCard(
    cue: WorkoutCue?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(170.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "握把把位",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Circular Hand Position Icon (105dp)
            if (cue != null) {
                Image(
                    painter = painterResource(id = getHandPositionDrawableRes(cue.handPosition)),
                    contentDescription = cue.handPosition.localizedName,
                    modifier = Modifier.size(105.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .background(CardBorder.copy(alpha = 0.3f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cue?.handPosition?.shortTitle ?: "1 號位",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = TopBarGreenDark
            )

            Text(
                text = when (cue?.handPosition) {
                    HandPosition.POSITION_1 -> "平把中段"
                    HandPosition.POSITION_2 -> "橫桿轉折"
                    HandPosition.POSITION_3 -> "前端牛角"
                    null -> "平把中段"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when (cue?.handPosition) {
                    HandPosition.POSITION_1 -> "雙手放近身平把"
                    HandPosition.POSITION_2 -> "手握橫桿轉折處"
                    HandPosition.POSITION_3 -> "雙手扣住前端牛角"
                    null -> "雙手放近身平把"
                },
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PostureFigureCockpitCard(
    cue: WorkoutCue?,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(170.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onInfoClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "騎乘姿勢",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = "姿勢說明",
                    tint = TopBarGreenDark,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Circular Posture Icon (105dp)
            if (cue != null) {
                Image(
                    painter = painterResource(id = getPostureDrawableRes(cue.posture)),
                    contentDescription = cue.posture.localizedName,
                    modifier = Modifier.size(105.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .background(CardBorder.copy(alpha = 0.3f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cue?.posture?.localizedName ?: "坐姿平路",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = TopBarGreenDark
            )

            Text(
                text = "建議 ${cue?.targetRpm ?: 85} RPM",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when (cue?.posture) {
                    PostureType.SEATED_FLAT -> "基礎體能建立"
                    PostureType.STANDING_FLAT -> "核心穩定鍛鍊"
                    PostureType.SEATED_CLIMB -> "臀腿阻力爬坡"
                    PostureType.STANDING_CLIMB -> "重阻力站立攀登"
                    PostureType.JUMPS -> "動態抽車跳躍"
                    PostureType.SPRINT -> "極限全力衝刺"
                    PostureType.RECOVERY -> "緩和放鬆心率"
                    null -> "基礎體能建立"
                },
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

private fun getPostureDrawableRes(posture: PostureType): Int {
    return when (posture) {
        PostureType.SEATED_FLAT -> R.drawable.icon_sit
        PostureType.STANDING_FLAT -> R.drawable.icon_stand
        PostureType.SEATED_CLIMB -> R.drawable.icon_sit_climbing
        PostureType.STANDING_CLIMB -> R.drawable.icon_stand_climbing
        PostureType.JUMPS -> R.drawable.icon_stand
        PostureType.SPRINT -> R.drawable.icon_stand_climbing
        PostureType.RECOVERY -> R.drawable.icon_sit
    }
}

private fun getHandPositionDrawableRes(position: HandPosition): Int {
    return when (position) {
        HandPosition.POSITION_1 -> R.drawable.icon_hand_position_1
        HandPosition.POSITION_2 -> R.drawable.icon_hand_position_2
        HandPosition.POSITION_3 -> R.drawable.icon_hand_position_3
    }
}

