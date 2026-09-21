package com.fitnessrider.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LicenseVerificationService(private val context: Context) {
    private val deviceService = DeviceIdentifierService(context)

    private val _isLicensed = MutableStateFlow(true)
    val isLicensed: StateFlow<Boolean> = _isLicensed.asStateFlow()

    private val _planType = MutableStateFlow("全功能免費試用版")
    val planType: StateFlow<String> = _planType.asStateFlow()

    private val _remainingDays = MutableStateFlow(30)
    val remainingDays: StateFlow<Int> = _remainingDays.asStateFlow()

    private val serverUrl = "https://fitnessrider.vercel.app"

    init {
        refreshLicenseState()
    }

    fun refreshLicenseState() {
        val isVip = com.fitnessrider.util.VersionLifecycleManager.isVipActive(context)
        val expired = com.fitnessrider.util.VersionLifecycleManager.isExpired(context)
        val days = com.fitnessrider.util.VersionLifecycleManager.getRemainingDays(context)

        if (isVip) {
            _planType.value = "專業年繳版 (VIP)"
            _isLicensed.value = true
            _remainingDays.value = days
        } else {
            _planType.value = "全功能免費試用版"
            _isLicensed.value = !expired
            _remainingDays.value = days
        }
    }

    suspend fun activateCode(code: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 1. Try online first to respect single-device limit & database audit
        try {
            val url = URL("$serverUrl/api/license/activate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val jsonBody = JSONObject().apply {
                put("device_fingerprint", deviceService.deviceFingerprint)
                put("license_code", code)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val respJson = if (responseText.isNotEmpty()) JSONObject(responseText) else JSONObject()

            if (conn.responseCode == 200 && respJson.optBoolean("success", false)) {
                com.fitnessrider.util.VersionLifecycleManager.activateLicenseCode(context, code)
                refreshLicenseState()
                val msg = respJson.optString("message", "開通成功！")
                return@withContext Pair(true, msg)
            } else if (respJson.has("error")) {
                val errorMsg = respJson.getString("error")
                if (errorMsg.contains("已兌換") || errorMsg.contains("限領一次")) {
                    return@withContext Pair(false, errorMsg)
                }
            }
        } catch (e: Exception) {
            // Fall back to offline
        }

        // 2. Offline algorithmic check fallback
        val localRes = com.fitnessrider.util.VersionLifecycleManager.activateLicenseCode(context, code)
        if (localRes.first) {
            refreshLicenseState()
        }
        return@withContext localRes
    }

    suspend fun verifyLicenseOnline() = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/license/verify")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val jsonBody = JSONObject().apply {
                put("device_fingerprint", deviceService.deviceFingerprint)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(responseText)
                val valid = respJson.optBoolean("is_valid", respJson.optBoolean("valid", true))
                val days = respJson.optInt("days_remaining", respJson.optInt("remaining_days", 365))
                val plan = respJson.optString("plan_type", "專業年繳版 (VIP)")

                _isLicensed.value = valid
                _remainingDays.value = days
                _planType.value = if (plan == "trial") "全功能免費試用版" else "專業年繳版 (VIP)"
            }
        } catch (e: Exception) {
            // Network failure / offline in studio: keep cached license active
            e.printStackTrace()
        }
    }

    // MARK: - Device Transfer (M6.3)

    suspend fun transferDeviceWithAccount(email: String, password: String): DeviceTransferResult = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
            put("new_device_fingerprint", deviceService.deviceFingerprint)
            put("device_model", deviceService.deviceModel)
            put("platform", "android")
        }
        executeDeviceTransfer(json)
    }

    suspend fun transferDeviceWithLicenseCode(code: String): DeviceTransferResult = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        val json = JSONObject().apply {
            put("license_code", cleanCode)
            put("new_device_fingerprint", deviceService.deviceFingerprint)
            put("device_model", deviceService.deviceModel)
            put("platform", "android")
        }
        executeDeviceTransfer(json, cleanCode)
    }

    private suspend fun executeDeviceTransfer(bodyJson: JSONObject, fallbackCode: String? = null): DeviceTransferResult {
        return try {
            val url = URL("$serverUrl/api/device/transfer")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(bodyJson.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            val respJson = if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()

            if (statusCode == 403) {
                val cooldownDays = if (respJson.has("remaining_cooldown_days")) respJson.getInt("remaining_cooldown_days") else null
                val errorMsg = respJson.optString("error", "換機次數受限，每 30 天僅允許更換綁定一次設備。")
                return DeviceTransferResult(
                    success = false,
                    message = errorMsg,
                    remainingCooldownDays = cooldownDays
                )
            }

            if (statusCode == 200 && respJson.optBoolean("success", false)) {
                val msg = respJson.optString("message", "設備轉移成功！")
                val licenseData = respJson.optJSONObject("license")
                val plan = licenseData?.optString("plan_type", "yearly") ?: "yearly"
                val days = licenseData?.optInt("days_remaining", 365) ?: 365

                val codeToUnlock = fallbackCode ?: "RIDER-VIP-2026-PASS"
                com.fitnessrider.util.VersionLifecycleManager.activateLicenseCode(context, codeToUnlock)
                refreshLicenseState()

                DeviceTransferResult(
                    success = true,
                    message = msg,
                    planType = if (plan == "trial") "全功能免費試用版" else "專業年繳版 (VIP)",
                    remainingDays = days
                )
            } else {
                val errorMsg = respJson.optString("error", "轉移設備失敗，請檢查帳號密碼或授權碼")
                DeviceTransferResult(success = false, message = errorMsg)
            }
        } catch (e: Exception) {
            DeviceTransferResult(success = false, message = "網路連線失敗，請確認網路後再試")
        }
    }
}

data class DeviceTransferResult(
    val success: Boolean,
    val message: String,
    val remainingCooldownDays: Int? = null,
    val planType: String? = null,
    val remainingDays: Int? = null
)
