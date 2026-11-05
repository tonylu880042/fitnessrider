package com.fitnessrider.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val RIDER3D_DEFAULT_PORT = 8000

data class Rider3DStage(
    val stage: String,
    val status: String
)

data class Rider3DScene(
    val id: String,
    val name: String,
    val description: String = ""
)

data class Rider3DCueSnapshot(
    val segmentIndex: Int,
    val cueId: String,
    val targetRpm: Int,
    val intensityZone: Int,
    val posture: PostureType
)

data class Rider3DSyncState(
    val hasStartedOnHost: Boolean = false,
    val hasStoppedOnHost: Boolean = false,
    val lastIsPlaying: Boolean = false,
    val lastSentSnapshot: Rider3DCueSnapshot? = null,
    val lastOffsetSeconds: Double = 0.0
)

data class Rider3DCommandSpec(
    val command: String,
    val targetRpm: Int? = null,
    val intensityZone: Int? = null,
    val posture: PostureType? = null
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

enum class Rider3DStartAction { PlainStart, PickScene, NeedsHost }

fun rider3DStartAction(isRider3DApp: Boolean, isEnabled: Boolean, hostInput: String): Rider3DStartAction = when {
    !isRider3DApp || !isEnabled -> Rider3DStartAction.PlainStart
    normalizedRider3DBaseUrl(hostInput) != null -> Rider3DStartAction.PickScene
    else -> Rider3DStartAction.NeedsHost
}

fun rider3DStageUrl(hostInput: String): String? {
    val base = normalizedRider3DBaseUrl(hostInput) ?: return null
    return "$base/api/v1/fitnessrider3d/GetUGymBikeStage"
}

fun rider3DScenesUrl(hostInput: String): String? {
    val base = normalizedRider3DBaseUrl(hostInput) ?: return null
    return "$base/api/v1/fitnessrider3d/GetSceneInfos"
}

fun rider3DPostureCode(posture: PostureType): String = when (posture) {
    PostureType.SEATED_FLAT -> "0"
    PostureType.SEATED_CLIMB -> "1"
    PostureType.STANDING_FLAT -> "2"
    PostureType.STANDING_CLIMB -> "3"
    PostureType.RECOVERY -> "0"
    PostureType.SPRINT -> "0"
    PostureType.JUMPS -> "2"
}

fun rider3DIntensityPercent(intensityZone: Int): String = when (intensityZone) {
    1 -> "55"
    2 -> "65"
    3 -> "75"
    4 -> "85"
    5 -> "90"
    else -> "65"
}

fun rider3DTargetRpmString(targetRpm: Int): String =
    targetRpm.coerceIn(1, 250).toString()

fun selectInitialRider3DSceneId(scenes: List<Rider3DScene>, lastSceneId: String): String {
    val trimmed = lastSceneId.trim()
    if (trimmed.isNotEmpty() && scenes.any { it.id == trimmed }) {
        return trimmed
    }
    return scenes.firstOrNull()?.id?.takeIf { it.isNotEmpty() } ?: "1"
}

fun buildRider3DEnterTrainingJson(
    classId: String,
    classTitle: String,
    classDurationMs: Int,
    sceneId: String
): String {
    val resolvedSceneId = sceneId.trim().ifEmpty { "1" }
    val obj = JSONObject()
    obj.put("classId", classId)
    obj.put("classTitle", classTitle)
    obj.put("classDuration", classDurationMs.coerceAtLeast(0).toString())
    obj.put("sceneId", resolvedSceneId)
    return obj.toString()
}

fun rider3DEnterTrainingUrl(
    hostInput: String,
    classId: String,
    classTitle: String,
    classDurationMs: Int,
    sceneId: String
): String? {
    val base = normalizedRider3DBaseUrl(hostInput) ?: return null
    val json = buildRider3DEnterTrainingJson(classId, classTitle, classDurationMs, sceneId)
    val encoded = URLEncoder.encode(json, "UTF-8").replace("+", "%20")
    return "$base/api/v1/fitnessrider3d/EnterTraining?json=$encoded"
}

fun buildRider3DUpdateWorkoutJson(
    command: String,
    targetRpm: Int? = null,
    intensityZone: Int? = null,
    posture: PostureType? = null,
    trap: Boolean = false,
    groupRide: Boolean = false
): String {
    val cmd = command.trim().lowercase()
    val obj = JSONObject()
    obj.put("command", cmd)
    when (cmd) {
        "start" -> {
            obj.put("trap", trap)
            obj.put("groupRide", groupRide)
        }
        "update" -> {
            obj.put("rpm", rider3DTargetRpmString(targetRpm ?: 85))
            obj.put("intensity", rider3DIntensityPercent(intensityZone ?: 2))
            obj.put("posture", rider3DPostureCode(posture ?: PostureType.SEATED_FLAT))
        }
    }
    return obj.toString()
}

fun rider3DUpdateWorkoutUrl(hostInput: String, payloadJson: String): String? {
    val base = normalizedRider3DBaseUrl(hostInput) ?: return null
    val encoded = URLEncoder.encode(payloadJson, "UTF-8").replace("+", "%20")
    return "$base/api/v1/fitnessrider3d/UpdateWorkoutInfo?json=$encoded"
}

fun isRider3DClassCompleted(
    totalSegments: Int,
    currentSegmentIndex: Int,
    isPlaying: Boolean,
    wasPlaying: Boolean,
    currentOffsetSeconds: Double,
    previousOffsetSeconds: Double,
    currentDurationSeconds: Double
): Boolean {
    if (totalSegments <= 0 || currentSegmentIndex != totalSegments - 1) return false
    if (!wasPlaying || isPlaying || currentDurationSeconds <= 0.0) return false
    val threshold = maxOf(0.5, currentDurationSeconds - 1.5)
    return currentOffsetSeconds <= 0.2 && previousOffsetSeconds >= threshold
}

fun rider3DStepCommands(
    state: Rider3DSyncState,
    hostStatus: String,
    isPlaying: Boolean,
    totalSegments: Int,
    currentSegmentIndex: Int,
    currentOffsetSeconds: Double,
    currentDurationSeconds: Double,
    snapshot: Rider3DCueSnapshot?
): Pair<Rider3DSyncState, List<Rider3DCommandSpec>> {
    if (state.hasStoppedOnHost) {
        return Pair(state, emptyList())
    }
    if (isRider3DClassCompleted(
            totalSegments = totalSegments,
            currentSegmentIndex = currentSegmentIndex,
            isPlaying = isPlaying,
            wasPlaying = state.lastIsPlaying,
            currentOffsetSeconds = currentOffsetSeconds,
            previousOffsetSeconds = state.lastOffsetSeconds,
            currentDurationSeconds = currentDurationSeconds
        )
    ) {
        val nextState = state.copy(
            hasStoppedOnHost = true,
            lastIsPlaying = false,
            lastOffsetSeconds = currentOffsetSeconds
        )
        return Pair(nextState, listOf(Rider3DCommandSpec(command = "stop")))
    }

    if (!state.hasStartedOnHost) {
        if (isPlaying && (hostStatus == "prepare" || hostStatus == "start")) {
            val cmds = mutableListOf(Rider3DCommandSpec(command = "start"))
            val validSnapshot = snapshot?.takeIf { it.targetRpm > 0 }
            if (validSnapshot != null) {
                cmds.add(
                    Rider3DCommandSpec(
                        command = "update",
                        targetRpm = validSnapshot.targetRpm,
                        intensityZone = validSnapshot.intensityZone,
                        posture = validSnapshot.posture
                    )
                )
            }
            val nextState = state.copy(
                hasStartedOnHost = true,
                lastIsPlaying = true,
                lastSentSnapshot = validSnapshot,
                lastOffsetSeconds = currentOffsetSeconds
            )
            return Pair(nextState, cmds)
        }
        return Pair(
            state.copy(lastIsPlaying = isPlaying, lastOffsetSeconds = currentOffsetSeconds),
            emptyList()
        )
    }

    val cmds = mutableListOf<Rider3DCommandSpec>()
    if (state.lastIsPlaying && !isPlaying) {
        cmds.add(Rider3DCommandSpec(command = "pause"))
    }
    val validSnapshot = snapshot?.takeIf { it.targetRpm > 0 }
    val updatedSnapshot = if (validSnapshot != null && validSnapshot != state.lastSentSnapshot) {
        cmds.add(
            Rider3DCommandSpec(
                command = "update",
                targetRpm = validSnapshot.targetRpm,
                intensityZone = validSnapshot.intensityZone,
                posture = validSnapshot.posture
            )
        )
        validSnapshot
    } else {
        state.lastSentSnapshot
    }
    val nextState = state.copy(
        lastIsPlaying = isPlaying,
        lastSentSnapshot = updatedSnapshot,
        lastOffsetSeconds = currentOffsetSeconds
    )
    return Pair(nextState, cmds)
}

fun rider3DCommandOnHudExit(hasStoppedOnHost: Boolean): String? =
    if (hasStoppedOnHost) null else "exit"

private fun validateRider3DEnvelope(root: JSONObject): String? {
    val errPairs = root.optJSONArray("ErrPairs") ?: return "主機回應格式錯誤"
    if (errPairs.length() == 0) return "主機回應格式錯誤"
    val firstErr = errPairs.optJSONObject(0) ?: return "主機回應格式錯誤"
    if (!firstErr.has("ErrCode")) return "主機回應格式錯誤"
    val errCode = firstErr.optInt("ErrCode", -1)
    if (errCode != 0) {
        val errMsg = if (firstErr.isNull("ErrMsg")) "" else firstErr.optString("ErrMsg", "").trim()
        return if (errMsg.isNotEmpty()) "主機錯誤：$errMsg (ErrCode $errCode)" else "主機錯誤 (ErrCode $errCode)"
    }
    return null
}

fun parseRider3DStageResponse(body: String): Rider3DCallResult<Rider3DStage> {
    return try {
        val root = JSONObject(body)
        val err = validateRider3DEnvelope(root)
        if (err != null) return Rider3DCallResult(errorMessage = err)
        val obj = root.optJSONObject("Obj")
            ?: return Rider3DCallResult(errorMessage = "主機未回傳狀態資料")
        val stage = if (obj.isNull("stage")) "" else obj.optString("stage", "")
        val status = if (obj.isNull("status")) "" else obj.optString("status", "")
        Rider3DCallResult(data = Rider3DStage(stage = stage, status = status))
    } catch (e: Exception) {
        Rider3DCallResult(errorMessage = "主機回應格式錯誤")
    }
}

fun parseRider3DScenesResponse(body: String): Rider3DCallResult<List<Rider3DScene>> {
    return try {
        val root = JSONObject(body)
        val err = validateRider3DEnvelope(root)
        if (err != null) return Rider3DCallResult(errorMessage = err)
        val rawObj = root.opt("Obj") ?: return Rider3DCallResult(errorMessage = "主機未回傳場景清單")
        val arr: JSONArray = when (rawObj) {
            is JSONArray -> rawObj
            is JSONObject -> rawObj.optJSONArray("sceneInfos")
                ?: rawObj.optJSONArray("SceneInfos")
                ?: return Rider3DCallResult(errorMessage = "主機未回傳場景清單")
            else -> return Rider3DCallResult(errorMessage = "主機未回傳場景清單")
        }
        val scenes = mutableListOf<Rider3DScene>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("Id", item.optString("id", "")).trim()
            val name = item.optString("Name", item.optString("name", "")).trim()
            val desc = item.optString("Description", item.optString("description", "")).trim()
            if (id.isNotEmpty()) {
                scenes.add(Rider3DScene(id = id, name = name.ifEmpty { "場景 $id" }, description = desc))
            }
        }
        Rider3DCallResult(data = scenes)
    } catch (e: Exception) {
        Rider3DCallResult(errorMessage = "主機回應格式錯誤")
    }
}

fun parseRider3DAckResponse(body: String): Rider3DCallResult<Boolean> {
    return try {
        val root = JSONObject(body)
        val err = validateRider3DEnvelope(root)
        if (err != null) return Rider3DCallResult(errorMessage = err)
        Rider3DCallResult(data = true)
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

    suspend fun fetchScenes(hostInput: String, timeoutMs: Int = 3000): Rider3DCallResult<List<Rider3DScene>> {
        val urlString = rider3DScenesUrl(hostInput)
            ?: return Rider3DCallResult(errorMessage = "請輸入有效的主機 IP 或 IP:Port")
        val rawResult = httpGet(urlString, timeoutMs)
        val body = rawResult.data ?: return Rider3DCallResult(errorMessage = rawResult.errorMessage)
        return parseRider3DScenesResponse(body)
    }

    suspend fun enterTraining(
        hostInput: String,
        classId: String,
        classTitle: String,
        classDurationMs: Int,
        sceneId: String,
        timeoutMs: Int = 4000
    ): Rider3DCallResult<Boolean> {
        val urlString = rider3DEnterTrainingUrl(hostInput, classId, classTitle, classDurationMs, sceneId)
            ?: return Rider3DCallResult(errorMessage = "請輸入有效的主機 IP 或 IP:Port")
        val rawResult = httpGet(urlString, timeoutMs)
        val body = rawResult.data ?: return Rider3DCallResult(errorMessage = rawResult.errorMessage)
        return parseRider3DAckResponse(body)
    }

    suspend fun updateWorkoutInfo(
        hostInput: String,
        spec: Rider3DCommandSpec,
        timeoutMs: Int = 1500
    ): Rider3DCallResult<Boolean> {
        val payload = buildRider3DUpdateWorkoutJson(
            command = spec.command,
            targetRpm = spec.targetRpm,
            intensityZone = spec.intensityZone,
            posture = spec.posture
        )
        val urlString = rider3DUpdateWorkoutUrl(hostInput, payload)
            ?: return Rider3DCallResult(errorMessage = "請輸入有效的主機 IP 或 IP:Port")
        val rawResult = httpGet(urlString, timeoutMs)
        val body = rawResult.data ?: return Rider3DCallResult(errorMessage = rawResult.errorMessage)
        return parseRider3DAckResponse(body)
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
