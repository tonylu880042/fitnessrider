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
import com.fitnessrider.model.WorkoutClass
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.CircleProgressBar
import com.fitnessrider.ui.components.TopNavBar

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

    val activeSegment = audioManager.currentSegment
    val currentMs = (currentOffsetSec * 1000).toInt()
    val activeCue = activeSegment?.cues?.lastOrNull { it.offsetMs <= currentMs } ?: activeSegment?.cues?.firstOrNull()
    val nextCue = activeSegment?.cues?.firstOrNull { it.offsetMs > currentMs }

    val remainingCueSec = if (nextCue != null) {
        ((nextCue.offsetMs - currentMs) / 1000).coerceAtLeast(0)
    } else {
        (currentDurationSec - currentOffsetSec).toInt().coerceAtLeast(0)
    }

    val progressRatio = if (currentDurationSec > 0) {
        (currentOffsetSec / currentDurationSec).toFloat().coerceIn(0f, 1f)
    } else 0f

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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "課堂曲目", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${workoutClass.segments.size} 首", fontSize = 13.sp, color = TextSecondary)
                    }
                    Divider(color = CardBorder)

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        itemsIndexed(workoutClass.segments) { index, segment ->
                            val isCurrent = index == currentSegmentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) TopBarGreen.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        audioManager.loadClass(workoutClass, index)
                                        audioManager.play()
                                    }
                                    .padding(8.dp),
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
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = segment.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
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
                        // Posture
                        if (activeCue != null) {
                            Text(
                                text = activeCue.posture.localizedName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TopBarGreenDark
                            )
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

                Spacer(modifier = Modifier.height(16.dp))

                // Resistance & Steppers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Resistance Badge
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
                            Text(text = "(${nextCue.targetRpm} RPM, ${nextCue.resistanceLevel})", fontSize = 13.sp, color = TopBarGreenDark)
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
    }
}
