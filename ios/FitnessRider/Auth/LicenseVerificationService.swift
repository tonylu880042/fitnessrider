import Foundation
import CryptoKit

public struct DeviceTransferResult: Equatable {
    public let success: Bool
    public let message: String
    public let remainingCooldownDays: Int?
    public let planType: String?
    public let remainingDays: Int?

    public init(
        success: Bool,
        message: String,
        remainingCooldownDays: Int? = nil,
        planType: String? = nil,
        remainingDays: Int? = nil
    ) {
        self.success = success
        self.message = message
        self.remainingCooldownDays = remainingCooldownDays
        self.planType = planType
        self.remainingDays = remainingDays
    }
}

@MainActor
public final class LicenseVerificationService: ObservableObject {
    public static let shared = LicenseVerificationService()

    @Published public private(set) var isLicensed: Bool = true
    @Published public private(set) var planType: String = "全功能免費試用版"
    @Published public private(set) var expirationDate: Date = Date().addingTimeInterval(Double(VersionLifecycleManager.lifecycleDays) * 86400)
    @Published public private(set) var remainingDays: Int = VersionLifecycleManager.lifecycleDays
    /// 有新版可拿、且目前這支建置版本已低於伺服器門檻時才會是 true；離線時永遠不會被設成 true。
    @Published public private(set) var mustUpdate: Bool = false

    private let serverURL = "https://fitnessrider.vercel.app"

    private init() {
        refreshLicenseState()
    }

    public func refreshLicenseState() {
        let isVIP = VersionLifecycleManager.shared.isVIP
        let expired = VersionLifecycleManager.shared.isExpired()
        let days = VersionLifecycleManager.shared.remainingDays()

        if isVIP {
            self.planType = "專業年繳版 (VIP)"
            self.isLicensed = true
            self.remainingDays = days
            self.expirationDate = Date().addingTimeInterval(Double(days) * 86400)
        } else {
            self.planType = "全功能免費試用版"
            self.isLicensed = !expired
            self.remainingDays = days
            self.expirationDate = VersionLifecycleManager.shared.expirationDate
        }
    }

    public func activateCode(code: String) async -> (Bool, String) {
        // 1. Try online activation first to respect single-device limit & database audit
        let (onlineSuccess, onlineMsg, errorCode) = await activateLicenseOnline(code: code)
        if onlineSuccess {
            refreshLicenseState()
            return (true, onlineMsg)
        }

        // If the server explicitly rejected the activation (e.g. 400 "本設備已兌換過..."),
        // return the rejection immediately to prevent duplicate abuse (spec 項目 6).
        let antiAbuseCodes: Set<String> = ["PROMO_EXPIRED", "PROMO_ALREADY_REDEEMED", "VIP_SERIAL_ALREADY_CLAIMED", "DEVICE_SECRET_REQUIRED"]
        let isAntiAbuse = (errorCode != nil && antiAbuseCodes.contains(errorCode!))
            || onlineMsg.contains("已兌換") || onlineMsg.contains("限領一次") || onlineMsg.contains("超過") || onlineMsg.contains("已在其他設備開通過")
        if isAntiAbuse {
            return (false, onlineMsg)
        }

        // 2. Offline algorithmic check fallback (e.g., in basement gym without network)
        let localResult = VersionLifecycleManager.shared.activateLicenseCode(code)
        if localResult.success {
            refreshLicenseState()
            Task {
                await reportActivationOnline(code: code)
            }
            return localResult
        }

        return (false, onlineMsg.isEmpty ? localResult.message : onlineMsg)
    }

    private func reportActivationOnline(code: String) async {
        guard let url = URL(string: "\(serverURL)/api/license/activate") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        var body: [String: Any] = [
            "device_fingerprint": DeviceIdentifierService.shared.deviceFingerprint,
            "license_code": code,
            "platform": "ios",
            "device_model": DeviceIdentifierService.shared.deviceModel,
            "client_first_launch_at": ISO8601DateFormatter().string(from: VersionLifecycleManager.shared.firstLaunchDate)
        ]
        if let signature = signRequest(timestampMs: timestamp) {
            body["timestamp"] = timestamp
            body["signature"] = signature
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        _ = try? await URLSession.shared.data(for: request)
    }

    private func activateLicenseOnline(code: String) async -> (success: Bool, message: String, errorCode: String?) {
        guard let url = URL(string: "\(serverURL)/api/license/activate") else {
            return (false, "無效的伺服器位址", nil)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        var body: [String: Any] = [
            "device_fingerprint": DeviceIdentifierService.shared.deviceFingerprint,
            "license_code": code,
            "platform": "ios",
            "device_model": DeviceIdentifierService.shared.deviceModel,
            "client_first_launch_at": ISO8601DateFormatter().string(from: VersionLifecycleManager.shared.firstLaunchDate)
        ]
        if let signature = signRequest(timestampMs: timestamp) {
            body["timestamp"] = timestamp
            body["signature"] = signature
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    if httpRes.statusCode == 200, let success = json["success"] as? Bool, success {
                        let isPromo = (json["is_promo"] as? Bool) ?? VersionLifecycleManager.isPromoCode(code)
                        if let expiresAtIso = json["expires_at"] as? String,
                           let expiresDate = Self.parseISO8601(expiresAtIso) {
                            VersionLifecycleManager.shared.activateVipFromServer(expiresAt: expiresDate, isPromo: isPromo, code: code)
                            if isPromo {
                                var redeemedList = UserDefaults.standard.stringArray(forKey: "fitness_rider_redeemed_promos") ?? []
                                if !redeemedList.contains(code) {
                                    redeemedList.append(code)
                                    UserDefaults.standard.set(redeemedList, forKey: "fitness_rider_redeemed_promos")
                                }
                            }
                        } else {
                            _ = VersionLifecycleManager.shared.activateLicenseCode(code)
                        }
                        if let deviceSecret = json["device_secret"] as? String {
                            DeviceIdentifierService.shared.deviceSecret = deviceSecret
                        }
                        refreshLicenseState()
                        let msg = json["message"] as? String ?? "開通成功！"
                        return (true, msg, nil)
                    } else if let errorMsg = json["error"] as? String {
                        let errCode = json["error_code"] as? String
                        return (false, errorMsg, errCode)
                    }
                }
            }
            return (false, "授權碼無效或驗證失敗", nil)
        } catch {
            return (false, "網路連線失敗，請確認網路或使用離線授權碼", nil)
        }
    }

    /// 後端用 `Date().toISOString()` 產生時間字串，帶毫秒（fractional seconds）；
    /// 標準 `ISO8601DateFormatter()` 預設不解析毫秒，所以要先試帶毫秒的選項，失敗再退回不帶毫秒的。
    private static func parseISO8601(_ raw: String) -> Date? {
        let withFractional = ISO8601DateFormatter()
        withFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFractional.date(from: raw) { return date }
        return ISO8601DateFormatter().date(from: raw)
    }

    /// 純函式，抽出來方便單元測試：min_supported_version_code <= 0 代表「永不強制更新」；
    /// 當伺服器設定了門檻 (> 0) 且用戶端 currentBuildNumber 低於門檻時才需要強制更新（spec 項目 F）。
    public nonisolated static func computeMustUpdate(minSupportedVersionCode: Int, currentBuildNumber: Int) -> Bool {
        return minSupportedVersionCode > 0 && currentBuildNumber < minSupportedVersionCode
    }

    private func signRequest(timestampMs: Int64) -> String? {
        guard let secret = DeviceIdentifierService.shared.deviceSecret,
              let keyData = secret.data(using: .utf8) else { return nil }
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint
        let message = "\(fingerprint).\(timestampMs)"
        let key = SymmetricKey(data: keyData)
        let mac = HMAC<SHA256>.authenticationCode(for: Data(message.utf8), using: key)
        return Data(mac).map { String(format: "%02x", $0) }.joined()
    }

    /// 啟動時呼叫一次：取得伺服器端試用起算錨點（用較早的一個校正本機，spec 項目 D）、
    /// 真正的授權狀態（若這台裝置已經開通過、能簽章），以及強制更新門檻
    /// min_supported_version_code（spec 項目 F）。
    ///
    /// 完全離線或連線失敗時，什麼都不做、保留目前的本機快取狀態 —— 絕對不會因為連不上
    /// 伺服器就把 mustUpdate 設成 true 而把使用者鎖住。
    public func refreshFromServer(currentBuildNumber: Int) async {
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint
        guard let url = URL(string: "\(serverURL)/api/license/verify") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        var body: [String: Any] = ["device_fingerprint": fingerprint, "platform": "ios"]
        if let signature = signRequest(timestampMs: timestamp) {
            body["timestamp"] = timestamp
            body["signature"] = signature
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpRes = response as? HTTPURLResponse else { return }
            guard (200...299).contains(httpRes.statusCode) || httpRes.statusCode == 403 else { return }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

            if let trialStartedAtIso = json["trial_started_at"] as? String,
               let serverAnchor = Self.parseISO8601(trialStartedAtIso) {
                VersionLifecycleManager.shared.reconcileFirstLaunchAnchor(serverAnchor: serverAnchor)
            }

            let minSupportedVersionCode = json["min_supported_version_code"] as? Int ?? 0
            let newMustUpdate = Self.computeMustUpdate(minSupportedVersionCode: minSupportedVersionCode, currentBuildNumber: currentBuildNumber)

            var newIsLicensed: Bool? = nil
            var newPlanType: String? = nil
            var newRemainingDays: Int? = nil

            if (200...299).contains(httpRes.statusCode) {
                let status = json["status"] as? String ?? ""
                if status == "active" || status == "expired" {
                    let valid = json["is_valid"] as? Bool ?? false
                    let days = json["days_remaining"] as? Int ?? 0
                    let plan = json["plan_type"] as? String ?? ""
                    if valid && plan != "trial" && plan != "none" {
                        newIsLicensed = true
                        newRemainingDays = days
                        newPlanType = "專業年繳版 (VIP)"
                    }
                }
            }

            await MainActor.run {
                self.mustUpdate = newMustUpdate
                // 這裡刻意「只加不減」：伺服器驗證通過時才升級本機狀態，絕不因為這次呼叫
                // 就把已經是 VIP 的本機狀態降級 —— 本機活化流程本身已經會呼叫
                // refreshLicenseState() 反映最新狀態，這裡不重複呼叫覆蓋掉剛設定的值。
                if let newIsLicensed { self.isLicensed = newIsLicensed }
                if let newPlanType { self.planType = newPlanType }
                if let newRemainingDays { self.remainingDays = newRemainingDays }
            }
        } catch {
            print("License online verification failed, keeping cached status: \(error)")
        }
    }

    public func verifyLicenseOnline() async {
        let buildNumber = Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0") ?? 0
        await refreshFromServer(currentBuildNumber: buildNumber)
    }

    // MARK: - Device Transfer (M6.3)

    public func transferDeviceWithAccount(email: String, password: String) async -> DeviceTransferResult {
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint
        let deviceModel = DeviceIdentifierService.shared.deviceModel
        let body: [String: Any] = [
            "email": email.trimmingCharacters(in: .whitespacesAndNewlines),
            "password": password,
            "new_device_fingerprint": fingerprint,
            "device_model": deviceModel,
            "platform": "ios"
        ]
        return await executeDeviceTransfer(body: body)
    }

    public func transferDeviceWithLicenseCode(code: String) async -> DeviceTransferResult {
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint
        let deviceModel = DeviceIdentifierService.shared.deviceModel
        let body: [String: Any] = [
            "license_code": cleanCode,
            "new_device_fingerprint": fingerprint,
            "device_model": deviceModel,
            "platform": "ios"
        ]
        return await executeDeviceTransfer(body: body)
    }

    private func executeDeviceTransfer(body: [String: Any]) async -> DeviceTransferResult {
        guard let url = URL(string: "\(serverURL)/api/device/transfer") else {
            return DeviceTransferResult(success: false, message: "伺服器網址無效")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpRes = response as? HTTPURLResponse else {
                return DeviceTransferResult(success: false, message: "伺服器無回應")
            }

            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return DeviceTransferResult(success: false, message: "伺服器回應格式異常")
            }

            // Check for 403 TRANSFER_COOLDOWN
            if httpRes.statusCode == 403 {
                let cooldownDays = json["remaining_cooldown_days"] as? Int
                let errorMsg = json["error"] as? String ?? "換機次數受限，請等待冷卻期後再試。"
                return DeviceTransferResult(
                    success: false,
                    message: errorMsg,
                    remainingCooldownDays: cooldownDays
                )
            }

            if httpRes.statusCode == 200, let success = json["success"] as? Bool, success {
                let msg = json["message"] as? String ?? "設備轉移成功！"
                let licenseData = json["license"] as? [String: Any]
                let plan = licenseData?["plan_type"] as? String ?? "yearly"
                let days = licenseData?["days_remaining"] as? Int ?? 365
                let expiresAtIso = licenseData?["expires_at"] as? String

                // 直接信任伺服器已驗證過身分（帳號密碼 / 已在伺服器驗過簽章的序號）的結果，
                // 不再靠寫死的 "RIDER-VIP-2026-PASS" 字串去騙本機的序號驗證邏輯解鎖。
                if let expiresAtIso, let expiresAtDate = Self.parseISO8601(expiresAtIso) {
                    VersionLifecycleManager.shared.activateVipFromServer(expiresAt: expiresAtDate)
                }
                if let deviceSecret = json["device_secret"] as? String {
                    DeviceIdentifierService.shared.deviceSecret = deviceSecret
                }
                refreshLicenseState()

                return DeviceTransferResult(
                    success: true,
                    message: msg,
                    planType: plan == "trial" ? "全功能免費試用版" : "專業年繳版 (VIP)",
                    remainingDays: days
                )
            } else {
                let errorMsg = json["error"] as? String ?? "轉移設備失敗，請檢查帳號密碼或授權碼"
                return DeviceTransferResult(success: false, message: errorMsg)
            }
        } catch {
            return DeviceTransferResult(success: false, message: "網路連線失敗，請檢查網路連線後再試")
        }
    }
}
