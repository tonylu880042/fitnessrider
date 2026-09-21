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

    // MARK: - Trial & VIP Persistent Accessors

    var trialStartTimestamp: Long?
        get() {
            val ts = prefs.getLong("trial_start_timestamp", -1L)
            return if (ts > 0L) ts else null
        }
        set(value) {
            if (value != null) {
                prefs.edit().putLong("trial_start_timestamp", value).apply()
            } else {
                prefs.edit().remove("trial_start_timestamp").apply()
            }
        }

    var isTrialPermanentlyLocked: Boolean
        get() = prefs.getBoolean("trial_permanently_locked", false)
        set(value) = prefs.edit().putBoolean("trial_permanently_locked", value).apply()

    var vipLicenseKey: String?
        get() = prefs.getString("vip_license_key", null)
        set(value) {
            if (value != null) {
                prefs.edit().putString("vip_license_key", value).apply()
            } else {
                prefs.edit().remove("vip_license_key").apply()
            }
        }

    var vipExpiresTimestamp: Long?
        get() {
            val ts = prefs.getLong("vip_expires_timestamp", -1L)
            return if (ts > 0L) ts else null
        }
        set(value) {
            if (value != null) {
                prefs.edit().putLong("vip_expires_timestamp", value).apply()
            } else {
                prefs.edit().remove("vip_expires_timestamp").apply()
            }
        }
}
