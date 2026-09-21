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

    private val _isLicensed = MutableStateFlow(true) // Default true with offline grace
    val isLicensed: StateFlow<Boolean> = _isLicensed.asStateFlow()

    private val _planType = MutableStateFlow("專業年繳版 (VIP)")
    val planType: StateFlow<String> = _planType.asStateFlow()

    private val _remainingDays = MutableStateFlow(365)
    val remainingDays: StateFlow<Int> = _remainingDays.asStateFlow()

    private val serverUrl = "https://fitnessrider.app/api/license/verify"

    suspend fun verifyLicenseOnline() = withContext(Dispatchers.IO) {
        try {
            val url = URL(serverUrl)
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
                _isLicensed.value = respJson.optBoolean("valid", true)
                _remainingDays.value = respJson.optInt("remaining_days", 365)
            }
        } catch (e: Exception) {
            // Network failure / offline in studio: keep cached license active
            e.printStackTrace()
        }
    }
}
