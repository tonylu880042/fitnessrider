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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LicenseVerificationService(private val context: Context) {
    companion object {
        /**
         * 純函式，抽出來方便單元測試：min_supported_version_code <= 0 代表「永不強制更新」；
         * 當伺服器設定了門檻 (> 0) 且用戶端 currentVersionCode 低於門檻時才需要強制更新（spec 項目 F）。
         */
        fun computeMustUpdate(minSupportedVersionCode: Int, currentVersionCode: Int): Boolean {
            return minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode
        }
    }

    private val deviceService = DeviceIdentifierService(context)

    private val _isLicensed = MutableStateFlow(true)
    val isLicensed: StateFlow<Boolean> = _isLicensed.asStateFlow()

    private val _planType = MutableStateFlow("全功能免費試用版")
    val planType: StateFlow<String> = _planType.asStateFlow()

    private val _remainingDays = MutableStateFlow(com.fitnessrider.util.VersionLifecycleManager.lifecycleDays)
    val remainingDays: StateFlow<Int> = _remainingDays.asStateFlow()

    /** 有新版可拿、且目前這支建置版本碼已低於伺服器門檻時才會是 true；離線時永遠不會被設成 true。 */
    private val _mustUpdate = MutableStateFlow(false)
    val mustUpdate: StateFlow<Boolean> = _mustUpdate.asStateFlow()

    private val serverUrl = "https://fitnessrider.vercel.app"

    init {
        refreshLicenseState()
    }

    fun refreshLicenseState() {
        val isVip = com.fitnessrider.util.VersionLifecycleManager.isVipActive(context)
        val expired = com.fitnessrider.util.VersionLifecycleManager.isExpired(context)
        val days = com.fitnessrider.util.VersionLifecycleManager.getRemainingDays(context)

        if (isVip) {
            _planType.value = com.fitnessrider.util.VersionLifecycleManager.getVipPlanName(context)
            _isLicensed.value = true
            _remainingDays.value = days
        } else {
            _planType.value = "全功能免費試用版"
            _isLicensed.value = !expired
            _remainingDays.value = days
        }
    }

    private fun signRequest(timestampMs: Long): String? {
        val secret = deviceService.deviceSecret ?: return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val message = "${deviceService.deviceFingerprint}.$timestampMs"
        val raw = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    suspend fun activateCode(code: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 0. 付費年繳 VIP 生效中時，推廣碼連送都不要送：伺服器查不到「離線開通的」付費授權，
        //    會放行推廣碼並回傳 anchor+30 天，成功分支會直接用它蓋掉本機的 365 天。
        com.fitnessrider.util.VersionLifecycleManager
            .promoBlockedByPaidVipMessage(context, code.trim().uppercase())
            ?.let { return@withContext Pair(false, it) }

        // 1. Try online first to respect single-device limit & database audit
        try {
            val url = URL("$serverUrl/api/license/activate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val timestamp = System.currentTimeMillis()
            val signature = signRequest(timestamp)

            val jsonBody = JSONObject().apply {
                put("device_fingerprint", deviceService.deviceFingerprint)
                put("license_code", code)
                // 讓伺服器端 bindDevice() 能記錄真實 platform/device_model，
                // 而不是寫死成 iOS/"Coach Device"（見 backend/src/lib/db.ts）。
                put("platform", "android")
                put("device_model", deviceService.deviceModel)
                if (signature != null) {
                    put("timestamp", timestamp)
                    put("signature", signature)
                }
                val localFirstLaunch = com.fitnessrider.util.VersionLifecycleManager.getFirstLaunchTimeMs(context)
                if (localFirstLaunch > 0) {
                    put("client_first_launch_at", java.time.Instant.ofEpochMilli(localFirstLaunch).toString())
                }
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val respJson = if (responseText.isNotEmpty()) JSONObject(responseText) else JSONObject()

            if (conn.responseCode == 200 && respJson.optBoolean("success", false)) {
                val isPromo = if (respJson.has("is_promo")) respJson.getBoolean("is_promo") else com.fitnessrider.util.VersionLifecycleManager.isPromoCode(code)
                val expiresAt = respJson.optString("expires_at", "")
                if (expiresAt.isNotEmpty()) {
                    com.fitnessrider.util.VersionLifecycleManager.activateVipFromServer(context, expiresAt, isPromo = isPromo, code = code)
                    if (isPromo) {
                        com.fitnessrider.util.VersionLifecycleManager.recordPromoRedemption(context, code)
                    }
                } else {
                    com.fitnessrider.util.VersionLifecycleManager.activateLicenseCode(context, code)
                }
                if (respJson.has("device_secret") && !respJson.isNull("device_secret")) {
                    deviceService.deviceSecret = respJson.getString("device_secret")
                }
                refreshLicenseState()
                val msg = respJson.optString("message", "開通成功！")
                return@withContext Pair(true, msg)
            } else if (respJson.has("error")) {
                val errorMsg = respJson.getString("error")
                val errorCode = respJson.optString("error_code", "")
                // 若伺服器明確回傳防濫用拒絕（結構化 error_code），直接返回拒絕，避免重複刷碼
                // VIP_ALREADY_ACTIVE 一定要在名單內：伺服器拒絕正是為了不讓推廣碼蓋掉付費年繳授權，
                // 若落到離線 fallback，本機會把 VIP 到期日改寫成推廣碼的 anchor+30 天。
                val antiAbuseCodes = setOf(
                    "PROMO_EXPIRED",
                    "PROMO_ALREADY_REDEEMED",
                    "VIP_SERIAL_ALREADY_CLAIMED",
                    "VIP_ALREADY_ACTIVE",
                    "PROMO_YEAR_EXPIRED"
                )
                if (errorCode in antiAbuseCodes) {
                    return@withContext Pair(false, errorMsg)
                }
                // 非明確防濫用之伺服器錯誤（例如連線問題、DEVICE_SECRET_REQUIRED 允許本地離線或未知異常），允許進入離線驗證 fallback
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

    /**
     * 啟動時呼叫一次：取得伺服器端試用起算錨點（用較早的一個校正本機，讓 Android 重灌
     * 也不會重置試用，spec 項目 D）、真正的授權狀態（若這台裝置已經開通過、能簽章）、
     * 以及強制更新門檻 min_supported_version_code（spec 項目 F）。
     *
     * 完全離線或連線失敗時，什麼都不做、保留目前的本機快取狀態 —— 絕對不會因為連不上
     * 伺服器就把 mustUpdate 設成 true 而把使用者鎖住。
     */
    suspend fun refreshFromServer(currentVersionCode: Int) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/license/verify")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val timestamp = System.currentTimeMillis()
            val signature = signRequest(timestamp)

            val jsonBody = JSONObject().apply {
                put("device_fingerprint", deviceService.deviceFingerprint)
                put("platform", "android")
                if (signature != null) {
                    put("timestamp", timestamp)
                    put("signature", signature)
                }
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            if (statusCode !in 200..299 && statusCode != 403) return@withContext
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: return@withContext
            val respJson = try { JSONObject(responseText) } catch (e: Exception) { return@withContext }

            val trialStartedAtIso = respJson.optString("trial_started_at", "")
            if (trialStartedAtIso.isNotEmpty()) {
                try {
                    val serverAnchorMs = java.time.Instant.parse(trialStartedAtIso).toEpochMilli()
                    com.fitnessrider.util.VersionLifecycleManager.reconcileFirstLaunchAnchor(context, serverAnchorMs)
                } catch (e: Exception) {
                    // 忽略格式異常，不影響其餘欄位處理
                }
            }

            val minSupportedVersionCode = respJson.optInt("min_supported_version_code", 0)
            _mustUpdate.value = computeMustUpdate(minSupportedVersionCode, currentVersionCode)

            // 這裡刻意「只加不減」：伺服器驗證通過時才升級本機狀態，不呼叫 refreshLicenseState()
            // 覆蓋掉剛設定的值 —— 本機活化流程（activateLicenseCode）本身已經會反映最新狀態，
            // 這個分支存在的目的正是為了在本機快取遺失、但伺服器仍記得這台裝置已開通時，
            // 能把授權狀態復原回來，若又立刻用純本機狀態覆蓋掉就白做了。
            if (statusCode in 200..299) {
                val status = respJson.optString("status", "")
                if (status == "active" || status == "expired") {
                    val valid = respJson.optBoolean("is_valid", false)
                    val days = respJson.optInt("days_remaining", 0)
                    val plan = respJson.optString("plan_type", "")
                    if (valid && plan != "trial" && plan != "none") {
                        _isLicensed.value = true
                        _remainingDays.value = days
                        _planType.value = "專業年繳版 (VIP)"
                    }
                }
            }
        } catch (e: Exception) {
            // Network failure / offline: keep cached state, never lock the user out.
        }
    }

    suspend fun verifyLicenseOnline() = withContext(Dispatchers.IO) {
        refreshFromServer(currentVersionCode = com.fitnessrider.util.VersionLifecycleManager.versionCode)
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
        executeDeviceTransfer(json)
    }

    private suspend fun executeDeviceTransfer(bodyJson: JSONObject): DeviceTransferResult {
        return try {
            // 這台設備若已經開通過就一定有裝置密鑰，簽章證明「轉移的目標設備就是本機」；
            // 伺服器端對已有密鑰的目標設備一律要求簽章（見 backend device/transfer route）。
            val timestamp = System.currentTimeMillis()
            signRequest(timestamp)?.let { signature ->
                bodyJson.put("timestamp", timestamp)
                bodyJson.put("signature", signature)
            }

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
                val expiresAt = licenseData?.optString("expires_at", "") ?: ""

                // 直接信任伺服器已驗證過身分（帳號密碼 / 已在伺服器驗過簽章的序號）的結果，
                // 不再靠寫死的 "RIDER-VIP-2026-PASS" 字串去騙本機的序號驗證邏輯解鎖。
                // plan_type 一定要帶進去：少了它，推廣方案換機後會被記成 "server_verified"，
                // 之後既顯示成「專業年繳版」，也會讓下一年度的推廣碼被防降級檢查誤擋。
                if (expiresAt.isNotEmpty()) {
                    com.fitnessrider.util.VersionLifecycleManager.activateVipFromServer(
                        context,
                        expiresAt,
                        isPromo = plan == "promo_trial_30d"
                    )
                }
                if (respJson.has("device_secret") && !respJson.isNull("device_secret")) {
                    deviceService.deviceSecret = respJson.getString("device_secret")
                }
                refreshLicenseState()

                DeviceTransferResult(
                    success = true,
                    message = msg,
                    planType = if (plan == "trial") "全功能免費試用版" else com.fitnessrider.util.VersionLifecycleManager.getVipPlanName(context),
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
