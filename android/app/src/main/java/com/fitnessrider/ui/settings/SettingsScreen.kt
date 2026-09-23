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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.fitnessrider.ui.paywall.PaywallScreen
import com.fitnessrider.util.VersionLifecycleManager
import kotlinx.coroutines.launch

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
    var isHapticEnabled by remember { mutableStateOf(settings.isHapticFeedbackEnabled) }
    var isAutoPauseEnabled by remember { mutableStateOf(settings.isAutoPauseBetweenSegmentsEnabled) }
    var crossfadeDuration by remember { mutableStateOf(settings.crossfadeDurationSeconds) }
    var isKeepAwakeEnabled by remember { mutableStateOf(settings.keepScreenAwakeInHUD) }

    var notificationMessage by remember { mutableStateOf<String?>(null) }
    var showActivationDialog by remember { mutableStateOf(false) }
    var showDeviceTransferDialog by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var enteredLicenseCode by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }

    val currentPlanType by licenseService.planType.collectAsState()
    val remainingDays by licenseService.remainingDays.collectAsState()
    val isLicensed by licenseService.isLicensed.collectAsState()

    fun copyDeviceIdToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("FitnessRider Device ID", deviceService.deviceFingerprint)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "已複製設備識別碼", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasWhite)
    ) {
        TopNavBar(
            title = "系統設定與備份",
            leading = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
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
                        Text(text = "動作切換車把觸覺震動回饋", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isHapticEnabled,
                            onCheckedChange = {
                                isHapticEnabled = it
                                settings.isHapticFeedbackEnabled = it
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "曲目平滑切換 (Crossfade)",
                            fontSize = 14.sp,
                            color = if (isAutoPauseEnabled) TextSecondary else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppSettings.CROSSFADE_OPTIONS_SECONDS.chunked(3).forEach { rowOptions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowOptions.forEach { sec ->
                                        val selected = crossfadeDuration == sec
                                        OutlinedButton(
                                            onClick = {
                                                crossfadeDuration = sec
                                                settings.crossfadeDurationSeconds = sec
                                            },
                                            enabled = !isAutoPauseEnabled,
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (selected) TopBarGreen.copy(alpha = 0.15f) else Color.Transparent,
                                                contentColor = if (selected) TopBarGreenDark else TextPrimary
                                            ),
                                            border = ButtonDefaults.outlinedButtonBorder(enabled = !isAutoPauseEnabled)
                                        ) {
                                            Text(
                                                text = crossfadeOptionLabel(sec),
                                                fontSize = 10.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (isAutoPauseEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "啟用「段落結束自動暫停」時，將自動停用 Crossfade",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

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
                        Text(text = currentPlanType, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TopBarGreenDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "授權狀態:", fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isLicensed) "有效 (剩餘 $remainingDays 天)" else "試用已結束",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLicensed) TopBarGreenDark else AccentRed
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showPaywall = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text(text = "💳 查看 VIP 付費方案與價格", fontSize = 13.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            enteredLicenseCode = ""
                            activationError = null
                            showActivationDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text(text = "🔑 輸入授權序號 / 課程代碼 (兌換 30 天試用)", fontSize = 13.sp, color = TopBarGreenDark)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showDeviceTransferDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text(text = "🔄 轉移設備授權 (換新機)", fontSize = 13.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "設備唯一識別碼 (Device ID):", fontSize = 12.sp, color = TextSecondary)
                        TextButton(
                            onClick = { copyDeviceIdToClipboard() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "複製", fontSize = 12.sp, color = TopBarGreenDark, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = deviceService.deviceFingerprint,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextPrimary
                    )

                    if (notificationMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
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
    }

    if (showActivationDialog) {
        val coroutineScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = {
                Text(text = "輸入授權序號或推廣代碼", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "輸入推廣培訓專屬代碼（如 26FR-NR）享 30 天免費體驗，或輸入 VIP 授權序號：", fontSize = 13.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = enteredLicenseCode,
                        onValueChange = { enteredLicenseCode = it.uppercase() },
                        placeholder = { Text("例如: 26FR-NR 或 FRVIP-...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (activationError != null) {
                        Text(
                            text = activationError!!,
                            fontSize = 12.sp,
                            color = AccentRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = licenseService.activateCode(enteredLicenseCode)
                            if (res.first) {
                                licenseService.refreshLicenseState()
                                notificationMessage = res.second
                                showActivationDialog = false
                            } else {
                                activationError = res.second
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen)
                ) {
                    Text("開通 / 兌換", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    if (showDeviceTransferDialog) {
        DeviceTransferDialog(
            onDismissRequest = { showDeviceTransferDialog = false },
            licenseService = licenseService,
            onTransferSuccess = { msg ->
                notificationMessage = msg
            }
        )
    }

    if (showPaywall) {
        PaywallScreen(
            onDismiss = { showPaywall = false },
            onActivateClick = {
                showPaywall = false
                enteredLicenseCode = ""
                activationError = null
                showActivationDialog = true
            }
        )
    }
}

private fun crossfadeOptionLabel(seconds: Double): String {
    val whole = seconds.toInt()
    return when (seconds) {
        0.0 -> "關閉 (0s)"
        2.0 -> "2 秒 (預設)"
        else -> "$whole 秒"
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
