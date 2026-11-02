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

public func rider3DStageUrl(_ hostInput: String) -> String? {
    guard let base = normalizedRider3DBaseUrl(hostInput) else { return nil }
    return "\(base)/api/v1/fitnessrider3d/GetUGymBikeStage"
}

public func parseRider3DStageResponse(_ body: String) -> Rider3DCallResult<Rider3DStage> {
    guard let data = body.data(using: .utf8),
          let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
          let errPairs = root["ErrPairs"] as? [[String: Any]],
          let firstErr = errPairs.first,
          let errCode = firstErr["ErrCode"] as? Int else {
        return Rider3DCallResult(errorMessage: "主機回應格式錯誤")
    }
    if errCode != 0 {
        let errMsg = (firstErr["ErrMsg"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let message = !errMsg.isEmpty ? "主機錯誤：\(errMsg) (ErrCode \(errCode))" : "主機錯誤 (ErrCode \(errCode))"
        return Rider3DCallResult(errorMessage: message)
    }
    guard let obj = root["Obj"] as? [String: Any] else {
        return Rider3DCallResult(errorMessage: "主機未回傳狀態資料")
    }
    let stage = (obj["stage"] as? String) ?? ""
    let status = (obj["status"] as? String) ?? ""
    return Rider3DCallResult(data: Rider3DStage(stage: stage, status: status))
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
