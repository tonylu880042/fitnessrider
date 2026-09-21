package com.fitnessrider.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.fitnessrider.auth.DeviceIdentifierService
import com.fitnessrider.auth.LicenseVerificationService
import com.fitnessrider.data.ClassRepository
import com.fitnessrider.data.SQLiteBackupService
import com.fitnessrider.model.AppSettings
import com.fitnessrider.theme.*
import com.fitnessrider.ui.components.TopNavBar

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.getInstance(context) }
    val deviceService = remember { DeviceIdentifierService(context) }
    val licenseService = remember { LicenseVerificationService(context) }
    val backupService = remember { SQLiteBackupService(context) }

    var isBeepEnabled by remember { mutableStateOf(settings.isCountdownBeepEnabled) }
    var isAutoPauseEnabled by remember { mutableStateOf(settings.isAutoPauseBetweenSegmentsEnabled) }
    var isKeepAwakeEnabled by remember { mutableStateOf(settings.keepScreenAwakeInHUD) }

    var notificationMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        TopNavBar(
            title = "系統設定與備份",
            leading = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Audio & Playback
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "課堂與音訊體驗", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "動作切換 3-2-1 倒數提示音", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isBeepEnabled,
                            onCheckedChange = {
                                isBeepEnabled = it
                                settings.isCountdownBeepEnabled = it
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "曲目段落結束自動暫停", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isAutoPauseEnabled,
                            onCheckedChange = {
                                isAutoPauseEnabled = it
                                settings.isAutoPauseBetweenSegmentsEnabled = it
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "課堂進行中螢幕強制常亮", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isKeepAwakeEnabled,
                            onCheckedChange = {
                                isKeepAwakeEnabled = it
                                settings.keepScreenAwakeInHUD = it
                            }
                        )
                    }
                }
            }

            // Section 2: SQLite Backup
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "資料庫備份與還原 (SQLite)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val backupFile = backupService.exportBackupFile()
                            if (backupFile != null) {
                                shareBackupFile(context, backupFile)
                                notificationMessage = "已產出備份檔: ${backupFile.name}"
                            } else {
                                notificationMessage = "備份產出失敗"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "📦 備份整個資料庫 (.sqlite)")
                    }
                }
            }

            // Section 3: Device Binding & Licensing
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "授權狀態與單機綁定", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "授權方案:", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "專業年繳版 (VIP)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "授權狀態:", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "有效 (剩餘 365 天)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "設備唯一識別碼 (Device ID):", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        text = deviceService.deviceFingerprint,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            }

            if (notificationMessage != null) {
                Text(
                    text = notificationMessage!!,
                    fontSize = 13.sp,
                    color = TopBarGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun shareBackupFile(context: Context, file: java.io.File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享資料庫備份檔"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
