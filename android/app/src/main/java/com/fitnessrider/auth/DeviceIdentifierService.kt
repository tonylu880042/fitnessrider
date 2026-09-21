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
}
