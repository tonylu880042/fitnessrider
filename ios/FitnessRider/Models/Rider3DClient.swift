import Foundation

public let rider3DDefaultPort: Int = 8000

public struct Rider3DStage: Equatable, Sendable {
    public let stage: String
    public let status: String

    public init(stage: String, status: String) {
        self.stage = stage
        self.status = status
    }
}

public struct Rider3DScene: Equatable, Identifiable, Sendable {
    public let id: String
    public let name: String
    public let description: String

    public init(id: String, name: String, description: String = "") {
        self.id = id
        self.name = name
        self.description = description
    }
}

public struct Rider3DCueSnapshot: Equatable, Sendable {
    public let segmentIndex: Int
    public let cueId: String
    public let targetRpm: Int
    public let intensityZone: Int
    public let posture: PostureType

    public init(segmentIndex: Int, cueId: String, targetRpm: Int, intensityZone: Int, posture: PostureType) {
        self.segmentIndex = segmentIndex
        self.cueId = cueId
        self.targetRpm = targetRpm
        self.intensityZone = intensityZone
        self.posture = posture
    }
}

public struct Rider3DSyncState: Equatable, Sendable {
    public var hasStartedOnHost: Bool
    public var hasStoppedOnHost: Bool
    public var lastIsPlaying: Bool
    public var lastSentSnapshot: Rider3DCueSnapshot?
    public var lastOffsetSeconds: Double

    public init(
        hasStartedOnHost: Bool = false,
        hasStoppedOnHost: Bool = false,
        lastIsPlaying: Bool = false,
        lastSentSnapshot: Rider3DCueSnapshot? = nil,
        lastOffsetSeconds: Double = 0.0
    ) {
        self.hasStartedOnHost = hasStartedOnHost
        self.hasStoppedOnHost = hasStoppedOnHost
        self.lastIsPlaying = lastIsPlaying
        self.lastSentSnapshot = lastSentSnapshot
        self.lastOffsetSeconds = lastOffsetSeconds
    }
}

public struct Rider3DCommandSpec: Equatable, Sendable {
    public let command: String
    public let targetRpm: Int?
    public let intensityZone: Int?
    public let posture: PostureType?

    public init(
        command: String,
        targetRpm: Int? = nil,
        intensityZone: Int? = nil,
        posture: PostureType? = nil
    ) {
        self.command = command
        self.targetRpm = targetRpm
        self.intensityZone = intensityZone
        self.posture = posture
    }
}

public struct Rider3DCallResult<T: Sendable>: Sendable {
    public let data: T?
    public let errorMessage: String?

    public var isSuccess: Bool {
        data != nil && errorMessage == nil
    }

    public init(data: T? = nil, errorMessage: String? = nil) {
        self.data = data
        self.errorMessage = errorMessage
    }
}

public func normalizedRider3DBaseUrl(_ input: String) -> String? {
    var raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
    if raw.isEmpty { return nil }
    if let range = raw.range(of: "://") {
        let scheme = String(raw[..<range.lowerBound])
        if scheme.lowercased() != "http" { return nil }
        raw = String(raw[range.upperBound...])
    }
    if raw.isEmpty || raw.contains(where: { $0.isWhitespace || $0 == "/" || $0 == "?" || $0 == "#" || $0 == "@" }) {
        return nil
    }
    let parts = raw.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
    if parts.count > 2 { return nil }
    let host = parts[0].lowercased()
    if !isValidRider3DHost(host) { return nil }
    let port: Int
    if parts.count == 2 {
        let portStr = parts[1]
        if portStr.isEmpty || !portStr.allSatisfy({ $0.isASCII && $0.isNumber }) { return nil }
        guard let parsed = Int(portStr), (1...65535).contains(parsed) else { return nil }
        port = parsed
    } else {
        port = rider3DDefaultPort
    }
    return "http://\(host):\(port)"
}

private func isValidRider3DHost(_ host: String) -> Bool {
    if host.isEmpty { return false }
    if host.hasPrefix(".") || host.hasSuffix(".") || host.hasPrefix("-") || host.hasSuffix("-") { return false }
    if host.contains("..") { return false }
    return host.unicodeScalars.allSatisfy { scalar in
        (scalar.value >= 97 && scalar.value <= 122) ||
        (scalar.value >= 48 && scalar.value <= 57) ||
        scalar == "." || scalar == "-"
    }
}

public func isRider3DActive(isRider3DApp: Bool, isEnabled: Bool, hostInput: String) -> Bool {
    isRider3DApp && isEnabled && normalizedRider3DBaseUrl(hostInput) != nil
}

public enum Rider3DStartAction: Equatable, Sendable {
    case plainStart
    case pickScene
    case needsHost
}

public func rider3DStartAction(isRider3DApp: Bool, isEnabled: Bool, hostInput: String) -> Rider3DStartAction {
    if !isRider3DApp || !isEnabled { return .plainStart }
    return normalizedRider3DBaseUrl(hostInput) != nil ? .pickScene : .needsHost
}

public func rider3DStageUrl(_ hostInput: String) -> String? {
    guard let base = normalizedRider3DBaseUrl(hostInput) else { return nil }
    return "\(base)/api/v1/fitnessrider3d/GetUGymBikeStage"
}

public func rider3DScenesUrl(_ hostInput: String) -> String? {
    guard let base = normalizedRider3DBaseUrl(hostInput) else { return nil }
    return "\(base)/api/v1/fitnessrider3d/GetSceneInfos"
}

public func rider3DPostureCode(_ posture: PostureType) -> String {
    switch posture {
    case .seatedFlat: return "0"
    case .seatedClimb: return "1"
    case .standingFlat: return "2"
    case .standingClimb: return "3"
    case .recovery: return "0"
    case .sprint: return "0"
    case .jumps: return "2"
    }
}

public func rider3DIntensityPercent(_ intensityZone: Int) -> String {
    switch intensityZone {
    case 1: return "55"
    case 2: return "65"
    case 3: return "75"
    case 4: return "85"
    case 5: return "90"
    default: return "65"
    }
}

public func rider3DTargetRpmString(_ targetRpm: Int) -> String {
    String(max(1, min(250, targetRpm)))
}

public func selectInitialRider3DSceneId(scenes: [Rider3DScene], lastSceneId: String) -> String {
    let trimmed = lastSceneId.trimmingCharacters(in: .whitespacesAndNewlines)
    if !trimmed.isEmpty, scenes.contains(where: { $0.id == trimmed }) {
        return trimmed
    }
    if let firstId = scenes.first?.id, !firstId.isEmpty {
        return firstId
    }
    return "1"
}

private func encodeRider3DQueryJson(_ json: String) -> String {
    var allowed = CharacterSet.alphanumerics
    allowed.insert(charactersIn: "-._~")
    return json.addingPercentEncoding(withAllowedCharacters: allowed) ?? json
}

private func escapeJsonString(_ s: String) -> String {
    var out = ""
    for ch in s {
        switch ch {
        case "\"": out += "\\\""
        case "\\": out += "\\\\"
        case "\n": out += "\\n"
        case "\r": out += "\\r"
        case "\t": out += "\\t"
        default: out.append(ch)
        }
    }
    return out
}

public func buildRider3DEnterTrainingJson(
    classId: String,
    classTitle: String,
    classDurationMs: Int,
    sceneId: String
) -> String {
    let trimmedScene = sceneId.trimmingCharacters(in: .whitespacesAndNewlines)
    let resolvedSceneId = trimmedScene.isEmpty ? "1" : trimmedScene
    let durationStr = String(max(0, classDurationMs))
    return #"{"classId":"\#(escapeJsonString(classId))","classTitle":"\#(escapeJsonString(classTitle))","classDuration":"\#(durationStr)","sceneId":"\#(escapeJsonString(resolvedSceneId))"}"#
}

public func rider3DEnterTrainingUrl(
    hostInput: String,
    classId: String,
    classTitle: String,
    classDurationMs: Int,
    sceneId: String
) -> String? {
    guard let base = normalizedRider3DBaseUrl(hostInput) else { return nil }
    let json = buildRider3DEnterTrainingJson(
        classId: classId,
        classTitle: classTitle,
        classDurationMs: classDurationMs,
        sceneId: sceneId
    )
    return "\(base)/api/v1/fitnessrider3d/EnterTraining?json=\(encodeRider3DQueryJson(json))"
}

public func buildRider3DUpdateWorkoutJson(
    command: String,
    targetRpm: Int? = nil,
    intensityZone: Int? = nil,
    posture: PostureType? = nil,
    trap: Bool = false,
    groupRide: Bool = false
) -> String {
    let cmd = command.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    switch cmd {
    case "start":
        return #"{"command":"start","trap":\#(trap),"groupRide":\#(groupRide)}"#
    case "update":
        let rpm = rider3DTargetRpmString(targetRpm ?? 85)
        let intensity = rider3DIntensityPercent(intensityZone ?? 2)
        let postureCode = rider3DPostureCode(posture ?? .seatedFlat)
        return #"{"command":"update","rpm":"\#(rpm)","intensity":"\#(intensity)","posture":"\#(postureCode)"}"#
    default:
        return #"{"command":"\#(escapeJsonString(cmd))"}"#
    }
}

public func rider3DUpdateWorkoutUrl(hostInput: String, payloadJson: String) -> String? {
    guard let base = normalizedRider3DBaseUrl(hostInput) else { return nil }
    return "\(base)/api/v1/fitnessrider3d/UpdateWorkoutInfo?json=\(encodeRider3DQueryJson(payloadJson))"
}

public func isRider3DClassCompleted(
    totalSegments: Int,
    currentSegmentIndex: Int,
    isPlaying: Bool,
    wasPlaying: Bool,
    currentOffsetSeconds: Double,
    previousOffsetSeconds: Double,
    currentDurationSeconds: Double
) -> Bool {
    if totalSegments <= 0 || currentSegmentIndex != totalSegments - 1 { return false }
    if !wasPlaying || isPlaying || currentDurationSeconds <= 0.0 { return false }
    let threshold = max(0.5, currentDurationSeconds - 1.5)
    return currentOffsetSeconds <= 0.2 && previousOffsetSeconds >= threshold
}

public func rider3DStepCommands(
    state: Rider3DSyncState,
    hostStatus: String,
    isPlaying: Bool,
    totalSegments: Int,
    currentSegmentIndex: Int,
    currentOffsetSeconds: Double,
    currentDurationSeconds: Double,
    snapshot: Rider3DCueSnapshot?
) -> (state: Rider3DSyncState, commands: [Rider3DCommandSpec]) {
    if state.hasStoppedOnHost {
        return (state, [])
    }
    if isRider3DClassCompleted(
        totalSegments: totalSegments,
        currentSegmentIndex: currentSegmentIndex,
        isPlaying: isPlaying,
        wasPlaying: state.lastIsPlaying,
        currentOffsetSeconds: currentOffsetSeconds,
        previousOffsetSeconds: state.lastOffsetSeconds,
        currentDurationSeconds: currentDurationSeconds
    ) {
        var nextState = state
        nextState.hasStoppedOnHost = true
        nextState.lastIsPlaying = false
        nextState.lastOffsetSeconds = currentOffsetSeconds
        return (nextState, [Rider3DCommandSpec(command: "stop")])
    }

    if !state.hasStartedOnHost {
        if isPlaying && (hostStatus == "prepare" || hostStatus == "start") {
            var cmds = [Rider3DCommandSpec(command: "start")]
            let validSnapshot = (snapshot?.targetRpm ?? 0) > 0 ? snapshot : nil
            if let validSnapshot = validSnapshot {
                cmds.append(
                    Rider3DCommandSpec(
                        command: "update",
                        targetRpm: validSnapshot.targetRpm,
                        intensityZone: validSnapshot.intensityZone,
                        posture: validSnapshot.posture
                    )
                )
            }
            var nextState = state
            nextState.hasStartedOnHost = true
            nextState.lastIsPlaying = true
            nextState.lastSentSnapshot = validSnapshot
            nextState.lastOffsetSeconds = currentOffsetSeconds
            return (nextState, cmds)
        }
        var nextState = state
        nextState.lastIsPlaying = isPlaying
        nextState.lastOffsetSeconds = currentOffsetSeconds
        return (nextState, [])
    }

    var cmds: [Rider3DCommandSpec] = []
    if state.lastIsPlaying && !isPlaying {
        cmds.append(Rider3DCommandSpec(command: "pause"))
    }
    let validSnapshot = (snapshot?.targetRpm ?? 0) > 0 ? snapshot : nil
    let updatedSnapshot: Rider3DCueSnapshot?
    if let validSnapshot = validSnapshot, validSnapshot != state.lastSentSnapshot {
        cmds.append(
            Rider3DCommandSpec(
                command: "update",
                targetRpm: validSnapshot.targetRpm,
                intensityZone: validSnapshot.intensityZone,
                posture: validSnapshot.posture
            )
        )
        updatedSnapshot = validSnapshot
    } else {
        updatedSnapshot = state.lastSentSnapshot
    }
    var nextState = state
    nextState.lastIsPlaying = isPlaying
    nextState.lastSentSnapshot = updatedSnapshot
    nextState.lastOffsetSeconds = currentOffsetSeconds
    return (nextState, cmds)
}

public func rider3DCommandOnHudExit(hasStoppedOnHost: Bool) -> String? {
    hasStoppedOnHost ? nil : "exit"
}

private func validateRider3DEnvelope(_ root: [String: Any]) -> String? {
    guard let errPairs = root["ErrPairs"] as? [[String: Any]],
          let firstErr = errPairs.first,
          let errCode = firstErr["ErrCode"] as? Int else {
        return "主機回應格式錯誤"
    }
    if errCode != 0 {
        let errMsg = (firstErr["ErrMsg"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return !errMsg.isEmpty ? "主機錯誤：\(errMsg) (ErrCode \(errCode))" : "主機錯誤 (ErrCode \(errCode))"
    }
    return nil
}

public func parseRider3DStageResponse(_ body: String) -> Rider3DCallResult<Rider3DStage> {
    guard let data = body.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
        return Rider3DCallResult(errorMessage: "主機回應格式錯誤")
    }
    if let err = validateRider3DEnvelope(root) {
        return Rider3DCallResult(errorMessage: err)
    }
    guard let obj = root["Obj"] as? [String: Any] else {
        return Rider3DCallResult(errorMessage: "主機未回傳狀態資料")
    }
    let stage = (obj["stage"] as? String) ?? ""
    let status = (obj["status"] as? String) ?? ""
    return Rider3DCallResult(data: Rider3DStage(stage: stage, status: status))
}

public func parseRider3DScenesResponse(_ body: String) -> Rider3DCallResult<[Rider3DScene]> {
    guard let data = body.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
        return Rider3DCallResult(errorMessage: "主機回應格式錯誤")
    }
    if let err = validateRider3DEnvelope(root) {
        return Rider3DCallResult(errorMessage: err)
    }
    let rawArray: [[String: Any]]?
    if let arr = root["Obj"] as? [[String: Any]] {
        rawArray = arr
    } else if let objDict = root["Obj"] as? [String: Any] {
        rawArray = (objDict["sceneInfos"] as? [[String: Any]]) ?? (objDict["SceneInfos"] as? [[String: Any]])
    } else {
        rawArray = nil
    }
    guard let arr = rawArray else {
        return Rider3DCallResult(errorMessage: "主機未回傳場景清單")
    }
    var scenes: [Rider3DScene] = []
    for item in arr {
        let id = ((item["Id"] as? String) ?? (item["id"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let name = ((item["Name"] as? String) ?? (item["name"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let desc = ((item["Description"] as? String) ?? (item["description"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !id.isEmpty {
            scenes.append(Rider3DScene(id: id, name: name.isEmpty ? "場景 \(id)" : name, description: desc))
        }
    }
    return Rider3DCallResult(data: scenes)
}

public func parseRider3DAckResponse(_ body: String) -> Rider3DCallResult<Bool> {
    guard let data = body.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
        return Rider3DCallResult(errorMessage: "主機回應格式錯誤")
    }
    if let err = validateRider3DEnvelope(root) {
        return Rider3DCallResult(errorMessage: err)
    }
    return Rider3DCallResult(data: true)
}

public func rider3DStageDisplayText(stage: String, status: String) -> String {
    switch status {
    case "": return "已連線（主機待命）"
    case "waitUnity": return "已連線（正在啟動 3D 場景）"
    case "prepare": return "已連線（3D 場景準備完成）"
    case "start": return "已連線（3D 訓練進行中）"
    case "result": return "已連線（顯示訓練結果）"
    default: return "已連線（狀態：\(status)）"
    }
}

public enum Rider3DClient {
    public static func fetchStage(hostInput: String, timeoutSeconds: TimeInterval = 2.5) async -> Rider3DCallResult<Rider3DStage> {
        guard let urlString = rider3DStageUrl(hostInput) else {
            return Rider3DCallResult(errorMessage: "請輸入有效的主機 IP 或 IP:Port")
        }
        let rawResult = await httpGet(urlString: urlString, timeoutSeconds: timeoutSeconds)
        guard let body = rawResult.data else {
            return Rider3DCallResult(errorMessage: rawResult.errorMessage)
        }
        return parseRider3DStageResponse(body)
    }

    public static func fetchScenes(hostInput: String, timeoutSeconds: TimeInterval = 3.0) async -> Rider3DCallResult<[Rider3DScene]> {
        guard let urlString = rider3DScenesUrl(hostInput) else {
            return Rider3DCallResult(errorMessage: "請輸入有效的主機 IP 或 IP:Port")
        }
        let rawResult = await httpGet(urlString: urlString, timeoutSeconds: timeoutSeconds)
        guard let body = rawResult.data else {
            return Rider3DCallResult(errorMessage: rawResult.errorMessage)
        }
        return parseRider3DScenesResponse(body)
    }

    public static func enterTraining(
        hostInput: String,
        classId: String,
        classTitle: String,
        classDurationMs: Int,
        sceneId: String,
        timeoutSeconds: TimeInterval = 4.0
    ) async -> Rider3DCallResult<Bool> {
        guard let urlString = rider3DEnterTrainingUrl(
            hostInput: hostInput,
            classId: classId,
            classTitle: classTitle,
            classDurationMs: classDurationMs,
            sceneId: sceneId
        ) else {
            return Rider3DCallResult(errorMessage: "請輸入有效的主機 IP 或 IP:Port")
        }
        let rawResult = await httpGet(urlString: urlString, timeoutSeconds: timeoutSeconds)
        guard let body = rawResult.data else {
            return Rider3DCallResult(errorMessage: rawResult.errorMessage)
        }
        return parseRider3DAckResponse(body)
    }

    public static func updateWorkoutInfo(
        hostInput: String,
        spec: Rider3DCommandSpec,
        timeoutSeconds: TimeInterval = 1.5
    ) async -> Rider3DCallResult<Bool> {
        let payload = buildRider3DUpdateWorkoutJson(
            command: spec.command,
            targetRpm: spec.targetRpm,
            intensityZone: spec.intensityZone,
            posture: spec.posture
        )
        guard let urlString = rider3DUpdateWorkoutUrl(hostInput: hostInput, payloadJson: payload) else {
            return Rider3DCallResult(errorMessage: "請輸入有效的主機 IP 或 IP:Port")
        }
        let rawResult = await httpGet(urlString: urlString, timeoutSeconds: timeoutSeconds)
        guard let body = rawResult.data else {
            return Rider3DCallResult(errorMessage: rawResult.errorMessage)
        }
        return parseRider3DAckResponse(body)
    }

    static func httpGet(urlString: String, timeoutSeconds: TimeInterval) async -> Rider3DCallResult<String> {
        guard let url = URL(string: urlString) else {
            return Rider3DCallResult(errorMessage: "請輸入有效的主機 IP 或 IP:Port")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = timeoutSeconds
        request.cachePolicy = .reloadIgnoringLocalCacheData
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResp = response as? HTTPURLResponse, httpResp.statusCode != 200 {
                return Rider3DCallResult(errorMessage: "連線失敗 (HTTP \(httpResp.statusCode))")
            }
            let text = String(data: data, encoding: .utf8) ?? ""
            return Rider3DCallResult(data: text)
        } catch {
            return Rider3DCallResult(errorMessage: "無法連線至 3D 主機")
        }
    }
}
