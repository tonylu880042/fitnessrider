package com.fitnessrider.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

class DeviceIdentifierService(private val context: Context) {
    private val prefs = context.getSharedPreferences("fitness_rider_device_auth", Context.MODE_PRIVATE)

    val deviceFingerprint: String
        @SuppressLint("HardwareIds")
        get() {
            val cached = prefs.getString("device_fingerprint", null)
            if (!cached.isNullOrBlank()) return cached

            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val finalId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                UUID.randomUUID().toString()
            }

            prefs.edit().putString("device_fingerprint", finalId).apply()
            return finalId
        }

    val deviceModel: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    // MARK: - Device Secret (spec 項目 E)
    //
    // 之前這個類別有 trialStartTimestamp / isTrialPermanentlyLocked / vipLicenseKey /
    // vipExpiresTimestamp 四個 accessor，但沒有任何呼叫端在用 —— 它們跟
    // VersionLifecycleManager 自己那份 SharedPreferences 狀態完全重複，且都存在同一種
    // SharedPreferences（解除安裝就清空），並不像 iOS 對應版本用 Keychain 那樣有「重灌後仍存在」
    // 的實質保護，留著只是「假裝有保護」，因此直接移除，改為只保留這裡真正有被使用、
    // 且真的有新功能的一個欄位：deviceSecret。
    //
    // deviceSecret 是裝置第一次成功開通授權/推廣代碼時，後端 /api/license/activate（或
    // /api/device/transfer）回傳的裝置專屬密鑰，之後呼叫 /api/license/verify 時要用它
    // 簽章請求，避免任何人只憑猜到的 device_fingerprint（ANDROID_ID 並非秘密）
    // 就能查詢這台裝置的真實授權狀態。見 LicenseVerificationService。

    var deviceSecret: String?
        get() = prefs.getString("device_secret", null)
        set(value) {
            if (value != null) {
                prefs.edit().putString("device_secret", value).apply()
            } else {
                prefs.edit().remove("device_secret").apply()
            }
        }
}
