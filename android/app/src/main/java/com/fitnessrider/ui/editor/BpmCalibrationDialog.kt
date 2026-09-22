package com.fitnessrider.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.audio.TapTempoDetector
import com.fitnessrider.theme.*
import kotlin.math.roundToInt

@Composable
fun BpmCalibrationDialog(
    initialBpm: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var calibratedBpm by remember { mutableStateOf(initialBpm.coerceIn(50.0, 220.0)) }
    val tapDetector = remember { TapTempoDetector() }
    var tapCount by remember { mutableStateOf(0) }
    var isPressedAnim by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressedAnim) 0.93f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        finishedListener = { isPressedAnim = false },
        label = "tapScale"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BPM 拍頻校正與測速",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = {
                    tapDetector.reset()
                    tapCount = 0
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "重設", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(12.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "當前拍頻 (Tempo)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.1f", calibratedBpm),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TopBarGreenDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BPM",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(TopBarGreen)
                        .border(4.dp, TopBarGreenDark, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPressedAnim = true
                            val detected = tapDetector.recordTap()
                            tapCount = tapDetector.tapCount
                            if (detected != null) {
                                calibratedBpm = detected
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TAP",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (tapCount < 2) "輕敲測速" else "已點擊 $tapCount 次",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Text(
                    text = "請隨音樂重拍連續點擊 3 次以上自動計算",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                HorizontalDivider(color = CardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { calibratedBpm = (calibratedBpm - 5.0).coerceAtLeast(40.0) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("-5", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { calibratedBpm = (calibratedBpm - 1.0).coerceAtLeast(40.0) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("-1", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { calibratedBpm = (calibratedBpm + 1.0).coerceAtMost(240.0) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("+1", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { calibratedBpm = (calibratedBpm + 5.0).coerceAtMost(240.0) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("+5", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(calibratedBpm) },
                colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark)
            ) {
                Text("套用 BPM", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}
