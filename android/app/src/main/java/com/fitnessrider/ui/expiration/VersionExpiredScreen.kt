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
import com.fitnessrider.ui.paywall.PaywallScreen
import com.fitnessrider.util.VersionLifecycleManager
import kotlinx.coroutines.launch

enum class ExpirationReason {
    TRIAL_ENDED,
    MUST_UPDATE
}

@Composable
fun VersionExpiredScreen(
    reason: ExpirationReason = ExpirationReason.TRIAL_ENDED,
    onUpdateClick: (() -> Unit)? = null,
    onUnlocked: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val deviceService = remember { com.fitnessrider.auth.DeviceIdentifierService(context) }
    val licenseService = remember { com.fitnessrider.auth.LicenseVerificationService(context) }
    var showBrowserlessDialog by remember { mutableStateOf(false) }
    var showActivationDialog by remember { mutableStateOf(false) }
    var showDeviceTransferDialog by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var enteredLicenseCode by remember { mutableStateOf("") }
    var activationStatusMessage by remember { mutableStateOf<String?>(null) }

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

    fun copyDeviceIdToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FitnessRider Device ID", deviceService.deviceFingerprint)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已複製設備識別碼", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    BackHandler(enabled = true) {
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
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassDisabled,
                        contentDescription = "試用到期",
                        tint = AccentRed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = if (reason == ExpirationReason.MUST_UPDATE) "需要更新版本" else "免費試用已結束",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Text(
                    text = if (reason == ExpirationReason.MUST_UPDATE)
                        "偵測到有新版本可用，這個版本已不再支援使用。\n\n請更新至最新版本後繼續使用；您的授權與課表資料都不會受影響。"
                    else
                        "感謝體驗 FitnessRider！您的免費試用期已結束。\n\n如需繼續在課堂中使用專業中控與變速音樂播放，請輸入授權碼開通 VIP，或更新至最新版本。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 2.dp))

                Surface(
                    color = CardHeaderBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "授權狀態", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = if (reason == ExpirationReason.MUST_UPDATE) "版本已不支援" else "試用期滿",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "試用起算日", fontSize = 12.sp, color = TextSecondary)
                            Text(text = VersionLifecycleManager.getFormattedTrialStartDate(context), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "目前版本", fontSize = 12.sp, color = TextSecondary)
                            Text(text = "v${VersionLifecycleManager.versionName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "設備識別碼 (Device ID)", fontSize = 11.sp, color = TextSecondary)
                            TextButton(
                                onClick = { copyDeviceIdToClipboard() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = TopBarGreenDark)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "複製", fontSize = 11.sp, color = TopBarGreenDark, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = deviceService.deviceFingerprint,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (reason == ExpirationReason.TRIAL_ENDED) {
                    Button(
                        onClick = { showActivationDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "🔑 輸入授權序號 / 課程代碼 (兌換 30 天)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = { showPaywall = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(
                            text = "💳 查看 VIP 付費方案與價格",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TopBarGreenDark
                        )
                    }

                    TextButton(
                        onClick = { showDeviceTransferDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(
                            text = "🔄 舊機換新機？轉移既有授權",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TopBarGreenDark
                        )
                    }
                }

                val downloadClick: () -> Unit = {
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
                }

                if (reason == ExpirationReason.MUST_UPDATE) {
                    Button(
                        onClick = downloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "前往更新最新版本",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = downloadClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "前往更新最新版本",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }

                TextButton(
                    onClick = { copyUpdateUrlToClipboard() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "複製更新網址 (若無瀏覽器)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                TextButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "結束應用程式",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
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
                    if (activationStatusMessage != null) {
                        Text(
                            text = activationStatusMessage!!,
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
                                Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                                showActivationDialog = false
                                onUnlocked?.invoke()
                            } else {
                                activationStatusMessage = res.second
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

    if (showDeviceTransferDialog) {
        com.fitnessrider.ui.settings.DeviceTransferDialog(
            onDismissRequest = { showDeviceTransferDialog = false },
            licenseService = licenseService,
            onTransferSuccess = { msg ->
                activationStatusMessage = msg
                onUnlocked?.invoke()
            }
        )
    }

    if (showPaywall) {
        PaywallScreen(
            onDismiss = { showPaywall = false },
            onActivateClick = {
                showPaywall = false
                showActivationDialog = true
            }
        )
    }
}
