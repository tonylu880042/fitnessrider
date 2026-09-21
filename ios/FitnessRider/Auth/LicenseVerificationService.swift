import Foundation

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
    @Published public private(set) var expirationDate: Date = Date().addingTimeInterval(30 * 86400)
    @Published public private(set) var remainingDays: Int = 30

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
        let localResult = VersionLifecycleManager.shared.activateLicenseCode(code)
        if localResult.success {
            refreshLicenseState()

            // Best-effort async online registration
            Task {
                await reportActivationOnline(code: code)
            }

            return localResult
        }

        // Try online activation if local check was not recognized
        let onlineResult = await activateLicenseOnline(code: code)
        if onlineResult.0 {
            refreshLicenseState()
        }
        return onlineResult
    }

    private func reportActivationOnline(code: String) async {
        guard let url = URL(string: "\(serverURL)/api/license/activate") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "device_fingerprint": DeviceIdentifierService.shared.deviceFingerprint,
            "license_code": code
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        _ = try? await URLSession.shared.data(for: request)
    }

    private func activateLicenseOnline(code: String) async -> (Bool, String) {
        guard let url = URL(string: "\(serverURL)/api/license/activate") else {
            return (false, "無效的伺服器網址")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "device_fingerprint": DeviceIdentifierService.shared.deviceFingerprint,
            "license_code": code
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let success = json["success"] as? Bool, success {
                    _ = VersionLifecycleManager.shared.activateLicenseCode(code)
                    refreshLicenseState()
                    return (true, "線上開通成功！已升級為專業版。")
                }
            }
            return (false, "授權碼無效或驗證失敗")
        } catch {
            return (false, "網路連線失敗，請確認網路或使用離線授權碼")
        }
    }

    public func verifyLicenseOnline() async {
        let fingerprint = DeviceIdentifierService.shared.deviceFingerprint

        guard let url = URL(string: "\(serverURL)/api/license/verify") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = ["device_fingerprint": fingerprint]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    let valid = json["is_valid"] as? Bool ?? json["valid"] as? Bool ?? false
                    let days = json["days_remaining"] as? Int ?? json["remaining_days"] as? Int ?? 365
                    let plan = json["plan_type"] as? String ?? "專業年繳版 (VIP)"

                    await MainActor.run {
                        if valid {
                            self.isLicensed = true
                            self.planType = plan == "trial" ? "全功能免費試用版" : "專業年繳版 (VIP)"
                            self.remainingDays = days
                        }
                    }
                }
            }
        } catch {
            print("License offline verification: keeping cached status")
        }
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
        return await executeDeviceTransfer(body: body, fallbackCode: cleanCode)
    }

    private func executeDeviceTransfer(body: [String: Any], fallbackCode: String? = nil) async -> DeviceTransferResult {
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

                // Local VIP unlock
                let codeToUnlock = fallbackCode ?? "RIDER-VIP-2026-PASS"
                _ = VersionLifecycleManager.shared.activateLicenseCode(codeToUnlock)
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
