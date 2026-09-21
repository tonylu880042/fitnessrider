package com.fitnessrider.ui.expiration

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessrider.theme.*
import com.fitnessrider.util.VersionLifecycleManager

@Composable
fun VersionExpiredScreen(
    onUpdateClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Block back button and predictive back gesture completely
    BackHandler(enabled = true) {
        // Do nothing, preventing bypass
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning Badge Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassDisabled,
                        contentDescription = "版本到期",
                        tint = AccentRed,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "版本已過期",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Text(
                    text = "為確保課堂中控穩定度、最新音樂分析演算法與各項功能體驗，每個發行版本的有效使用期限固定為 30 天。\n\n此版本已超過使用期限，請更新至最新版本後繼續使用。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

                // Version telemetry detail box
                Surface(
                    color = CardHeaderBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "目前版本", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "v${VersionLifecycleManager.versionName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "建置時間", fontSize = 12.sp, color = TextSecondary)
                            Text(text = VersionLifecycleManager.getFormattedBuildDate(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "到期日期", fontSize = 12.sp, color = TextSecondary)
                            Text(text = VersionLifecycleManager.getFormattedExpirationDate(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = AccentRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Action: Download Latest Version
                Button(
                    onClick = {
                        if (onUpdateClick != null) {
                            onUpdateClick()
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(VersionLifecycleManager.updateUrl))
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "前往更新最新版本",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Secondary Action: Exit Application
                TextButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "結束應用程式",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
