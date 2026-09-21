package com.fitnessrider.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fitnessrider.auth.DeviceIdentifierService
import com.fitnessrider.auth.LicenseVerificationService
import com.fitnessrider.theme.AccentOrange
import com.fitnessrider.theme.AccentRed
import com.fitnessrider.theme.CardHeaderBackground
import com.fitnessrider.theme.TextPrimary
import com.fitnessrider.theme.TextSecondary
import com.fitnessrider.theme.TopBarGreen
import com.fitnessrider.theme.TopBarGreenDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeviceTransferDialog(
    onDismissRequest: () -> Unit,
    licenseService: LicenseVerificationService,
    onTransferSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val deviceService = remember { DeviceIdentifierService(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: VIP 序號, 1: 帳號密碼
    var licenseCodeInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingCooldownDays by remember { mutableStateOf<Int?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isLoading) onDismissRequest() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔄 跨設備授權轉移 (換新機)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Policy Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TopBarGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "單一設備綁定與防共用政策：",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TopBarGreenDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• FitnessRider 授權限定單一設備使用。\n• 當您換新平板時可遷入本設備，舊設備將自動停用。\n• 為防共用作弊，每 30 天內最多僅允許轉移一次設備。",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardHeaderBackground,
                    contentColor = TopBarGreenDark
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            errorMessage = null
                            remainingCooldownDays = null
                        },
                        text = { Text("VIP 序號轉移", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            errorMessage = null
                            remainingCooldownDays = null
                        },
                        text = { Text("會員帳密轉移", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                // Inputs
                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "原 VIP 授權序號：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = licenseCodeInput,
                            onValueChange = { licenseCodeInput = it.uppercase() },
                            placeholder = { Text("例如: RIDER-VIP-2026-PASS", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "請輸入舊設備開通使用中的 VIP 授權碼",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "會員 Email 帳號：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                placeholder = { Text("coach@fitnessrider.app", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "會員密碼：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                placeholder = { Text("請輸入密碼", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Current Device Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "本機 (新設備):", fontSize = 10.sp, color = TextSecondary)
                        Text(text = deviceService.deviceModel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TopBarGreenDark)
                    }
                    Text(
                        text = deviceService.deviceFingerprint,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }

                // Cooldown Banner
                if (remainingCooldownDays != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AccentOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Text(text = "換機次數受限 (30 天冷卻保護)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "距離下次可更換設備尚有 $remainingCooldownDays 天。\n如為硬體損壞或教學突發狀況，請聯繫官方客服專案處理。",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 11.sp,
                        color = AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Success message
                if (successMessage != null) {
                    Text(
                        text = successMessage!!,
                        fontSize = 12.sp,
                        color = TopBarGreenDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onDismissRequest() },
                        enabled = !isLoading
                    ) {
                        Text(text = "取消", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            remainingCooldownDays = null
                            successMessage = null
                            isLoading = true

                            coroutineScope.launch {
                                val result = if (selectedTab == 0) {
                                    val code = licenseCodeInput.trim()
                                    if (code.isBlank()) {
                                        isLoading = false
                                        errorMessage = "請輸入原 VIP 授權序號"
                                        return@launch
                                    }
                                    licenseService.transferDeviceWithLicenseCode(code)
                                } else {
                                    val email = emailInput.trim()
                                    val pass = passwordInput
                                    if (email.isBlank() || pass.isBlank()) {
                                        isLoading = false
                                        errorMessage = "請輸入完整帳號與密碼"
                                        return@launch
                                    }
                                    licenseService.transferDeviceWithAccount(email, pass)
                                }

                                isLoading = false
                                if (result.success) {
                                    successMessage = result.message
                                    licenseService.refreshLicenseState()
                                    delay(1500)
                                    onTransferSuccess(result.message)
                                    onDismissRequest()
                                } else {
                                    if (result.remainingCooldownDays != null) {
                                        remainingCooldownDays = result.remainingCooldownDays
                                    } else {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TopBarGreenDark),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "確認轉移至本設備", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
