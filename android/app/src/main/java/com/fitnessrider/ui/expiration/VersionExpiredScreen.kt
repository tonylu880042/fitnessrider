package com.fitnessrider.ui.expiration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var showBrowserlessDialog by remember { mutableStateOf(false) }

    fun copyUpdateUrlToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FitnessRider Update URL", VersionLifecycleManager.updateUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已複製更新網址至剪貼簿", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(VersionLifecycleManager.updateUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                copyUpdateUrlToClipboard()
                                showBrowserlessDialog = true
                            } catch (e: Exception) {
                                copyUpdateUrlToClipboard()
                                showBrowserlessDialog = true
                            }
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

                // Copy URL Action for Kiosk/Restricted Tablets
                TextButton(
                    onClick = { copyUpdateUrlToClipboard() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "複製更新網址 (若無瀏覽器)",
                        fontSize = 13.sp,
                        color = TextSecondary
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

    if (showBrowserlessDialog) {
        AlertDialog(
            onDismissRequest = { showBrowserlessDialog = false },
            title = {
                Text(
                    text = "無法開啟系統瀏覽器",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "此設備未安裝瀏覽器或處於受限 Kiosk 模式。更新網址已複製至剪貼簿，請使用手機或其他設備下載安裝：",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Surface(
                        color = CardHeaderBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = VersionLifecycleManager.updateUrl,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TopBarGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyUpdateUrlToClipboard()
                    showBrowserlessDialog = false
                }) {
                    Text("再次複製網址並確定", color = TopBarGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
