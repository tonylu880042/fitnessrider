package com.fitnessrider.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val RIDER3D_DEFAULT_PORT = 8000

data class Rider3DStage(
    val stage: String,
    val status: String
)

data class Rider3DCallResult<T>(
    val data: T? = null,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = data != null && errorMessage == null
}

fun normalizedRider3DBaseUrl(input: String): String? {
    var raw = input.trim()
    if (raw.isEmpty()) return null
    val schemeIndex = raw.indexOf("://")
    if (schemeIndex >= 0) {
        val scheme = raw.substring(0, schemeIndex)
        if (!scheme.equals("http", ignoreCase = true)) return null
        raw = raw.substring(schemeIndex + 3)
    }
    if (raw.isEmpty() || raw.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '@' }) {
        return null
    }
    val parts = raw.split(':')
    if (parts.size > 2) return null
    val host = parts[0].lowercase()
    if (!isValidRider3DHost(host)) return null
    val port = if (parts.size == 2) {
        val portStr = parts[1]
        if (portStr.isEmpty() || !portStr.all { it.isDigit() }) return null
        val parsed = portStr.toIntOrNull() ?: return null
        if (parsed !in 1..65535) return null
        parsed
    } else {
        RIDER3D_DEFAULT_PORT
    }
    return "http://$host:$port"
}

private fun isValidRider3DHost(host: String): Boolean {
    if (host.isEmpty()) return false
    if (host.startsWith('.') || host.endsWith('.') || host.startsWith('-') || host.endsWith('-')) return false
    if (host.contains("..")) return false
    return host.all { (it in 'a'..'z') || (it in '0'..'9') || it == '.' || it == '-' }
}

fun isRider3DActive(isRider3DApp: Boolean, isEnabled: Boolean, hostInput: String): Boolean =
    isRider3DApp && isEnabled && normalizedRider3DBaseUrl(hostInput) != null

fun rider3DStageUrl(hostInput: String): String? {
    val base = normalizedRider3DBaseUrl(hostInput) ?: return null
    return "$base/api/v1/fitnessrider3d/GetUGymBikeStage"
}

fun parseRider3DStageResponse(body: String): Rider3DCallResult<Rider3DStage> {
    return try {
        val root = JSONObject(body)
        val errPairs = root.optJSONArray("ErrPairs")
            ?: return Rider3DCallResult(errorMessage = "主機回應格式錯誤")
        if (errPairs.length() == 0) {
            return Rider3DCallResult(errorMessage = "主機回應格式錯誤")
        }
        val firstErr = errPairs.optJSONObject(0)
            ?: return Rider3DCallResult(errorMessage = "主機回應格式錯誤")
        if (!firstErr.has("ErrCode")) {
            return Rider3DCallResult(errorMessage = "主機回應格式錯誤")
        }
        val errCode = firstErr.optInt("ErrCode", -1)
        if (errCode != 0) {
            val errMsg = if (firstErr.isNull("ErrMsg")) "" else firstErr.optString("ErrMsg", "").trim()
            val message = if (errMsg.isNotEmpty()) "主機錯誤：$errMsg (ErrCode $errCode)" else "主機錯誤 (ErrCode $errCode)"
            return Rider3DCallResult(errorMessage = message)
        }
        val obj = root.optJSONObject("Obj")
            ?: return Rider3DCallResult(errorMessage = "主機未回傳狀態資料")
        val stage = if (obj.isNull("stage")) "" else obj.optString("stage", "")
        val status = if (obj.isNull("status")) "" else obj.optString("status", "")
        Rider3DCallResult(data = Rider3DStage(stage = stage, status = status))
    } catch (e: Exception) {
        Rider3DCallResult(errorMessage = "主機回應格式錯誤")
    }
}

fun rider3DStageDisplayText(stage: String, status: String): String {
    return when (status) {
        "" -> "已連線（主機待命）"
        "waitUnity" -> "已連線（正在啟動 3D 場景）"
        "prepare" -> "已連線（3D 場景準備完成）"
        "start" -> "已連線（3D 訓練進行中）"
        "result" -> "已連線（顯示訓練結果）"
        else -> "已連線（狀態：$status）"
    }
}

object Rider3DClient {
    suspend fun fetchStage(hostInput: String, timeoutMs: Int = 2500): Rider3DCallResult<Rider3DStage> {
        val urlString = rider3DStageUrl(hostInput)
            ?: return Rider3DCallResult(errorMessage = "請輸入有效的主機 IP 或 IP:Port")
        val rawResult = httpGet(urlString, timeoutMs)
        val body = rawResult.data ?: return Rider3DCallResult(errorMessage = rawResult.errorMessage)
        return parseRider3DStageResponse(body)
    }

    internal suspend fun httpGet(urlString: String, timeoutMs: Int): Rider3DCallResult<String> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    useCaches = false
                }
                connection = conn
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext Rider3DCallResult(errorMessage = "連線失敗 (HTTP $code)")
                }
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                Rider3DCallResult(data = text)
            } catch (e: Exception) {
                Rider3DCallResult(errorMessage = "無法連線至 3D 主機")
            } finally {
                connection?.disconnect()
            }
        }
}
