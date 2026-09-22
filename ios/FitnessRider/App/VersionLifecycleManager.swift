import Foundation

public final class VersionLifecycleManager: ObservableObject, @unchecked Sendable {
    public static let shared = VersionLifecycleManager()

    public static let lifecycleDays: Int = 7
    public static let promoTotalTrialDays: Int = 30
    public static let secondsPerDay: TimeInterval = 86_400.0
    public static let lifecycleDuration: TimeInterval = Double(lifecycleDays) * secondsPerDay

    public static let updateURL = URL(string: "https://appdistribution.firebase.dev/i/d740076f27b77ab0")!

    private let userDefaultsLastLaunchKey = "fitness_rider_last_launch_timestamp"
    private let userDefaultsExpiredKey = "fitness_rider_is_expired"
    private let userDefaultsFirstLaunchKey = "fitness_rider_first_launch_timestamp"
    private let userDefaultsRedeemedPromosKey = "fitness_rider_redeemed_promos"

    public let buildDate: Date

    public private(set) var firstLaunchDate: Date

    public var expirationDate: Date {
        firstLaunchDate.addingTimeInterval(Self.lifecycleDuration)
    }

    @Published public private(set) var isExpiredOnLaunch: Bool = false

    @Published public private(set) var isVIP: Bool = false
    @Published public private(set) var vipPlanName: String = "專業年繳版 (VIP)"

    public init(buildDate: Date? = nil, explicitFirstLaunchDate: Date? = nil) {
        if let explicit = buildDate {
            self.buildDate = explicit
        } else {
            var resolvedDate: Date? = nil
            if let tsString = Bundle.main.object(forInfoDictionaryKey: "CFBundleBuildTimestamp") as? String,
               let ts = Double(tsString), ts > 0 {
                resolvedDate = Date(timeIntervalSince1970: ts)
            } else if let tsNumber = Bundle.main.object(forInfoDictionaryKey: "CFBundleBuildTimestamp") as? Double,
                      tsNumber > 0 {
                resolvedDate = Date(timeIntervalSince1970: tsNumber)
            }
            self.buildDate = resolvedDate ?? Date(timeIntervalSince1970: 1789994982)
        }

        if let explicitTrial = explicitFirstLaunchDate {
            self.firstLaunchDate = explicitTrial
        } else if let explicitBuild = buildDate {
            self.firstLaunchDate = explicitBuild
        } else if let keychainTs = DeviceIdentifierService.shared.trialStartTimestamp, keychainTs > 0 {
            self.firstLaunchDate = Date(timeIntervalSince1970: keychainTs)
        } else {
            let userDefaultsTs = UserDefaults.standard.double(forKey: "fitness_rider_first_launch_timestamp")
            if userDefaultsTs > 0 {
                self.firstLaunchDate = Date(timeIntervalSince1970: userDefaultsTs)
                DeviceIdentifierService.shared.trialStartTimestamp = userDefaultsTs
            } else {
                let now = Date()
                self.firstLaunchDate = now
                DeviceIdentifierService.shared.trialStartTimestamp = now.timeIntervalSince1970
                UserDefaults.standard.set(now.timeIntervalSince1970, forKey: "fitness_rider_first_launch_timestamp")
            }
        }

        self.isVIP = evaluateVipStatus()
        self.vipPlanName = Self.planName(isPromo: Self.isPromoVipCode(Self.storedVipCode(defaults: .standard)))
    }

    static func planName(isPromo: Bool) -> String {
        isPromo ? "推廣課程專屬版 (\(promoTotalTrialDays)天免費)" : "專業年繳版 (VIP)"
    }

    static func storedVipCode(defaults: UserDefaults) -> String? {
        if let code = defaults.string(forKey: "fitness_rider_vip_code") { return code }
        return defaults == UserDefaults.standard ? DeviceIdentifierService.shared.vipLicenseKey : nil
    }

    public func evaluateVipStatus(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        if defaults.bool(forKey: "fitness_rider_vip_active") {
            let expires = defaults.double(forKey: "fitness_rider_vip_expires")
            if expires > currentTime.timeIntervalSince1970 {
                return true
            }
        }
        if defaults == UserDefaults.standard,
           let _ = DeviceIdentifierService.shared.vipLicenseKey,
           let expires = DeviceIdentifierService.shared.vipExpiresTimestamp {
            if expires > currentTime.timeIntervalSince1970 {
                return true
            }
        }
        return false
    }

    @discardableResult
    public func evaluateExpirationOnLaunch(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        self.isVIP = evaluateVipStatus(currentTime: currentTime, defaults: defaults)
        let expired = isExpired(currentTime: currentTime, defaults: defaults)
        self.isExpiredOnLaunch = expired
        return expired
    }

    public func isExpired(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        if evaluateVipStatus(currentTime: currentTime, defaults: defaults) {
            return false
        }

        if defaults.bool(forKey: userDefaultsExpiredKey) {
            return true
        }
        if defaults == UserDefaults.standard && DeviceIdentifierService.shared.isTrialPermanentlyLocked {
            return true
        }

        let lastLaunch = defaults.double(forKey: userDefaultsLastLaunchKey)
        let currentSeconds = currentTime.timeIntervalSince1970

        if lastLaunch > 0 && currentSeconds < (lastLaunch - 3600.0) {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = true
            }
            return true
        }

        if currentTime >= expirationDate {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            defaults.set(max(currentSeconds, lastLaunch), forKey: userDefaultsLastLaunchKey)
            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = true
            }
            return true
        }

        if currentSeconds > lastLaunch {
            defaults.set(currentSeconds, forKey: userDefaultsLastLaunchKey)
        }

        return false
    }

    public func remainingDays(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Int {
        if evaluateVipStatus(currentTime: currentTime, defaults: defaults) {
            return 365
        }
        if isExpired(currentTime: currentTime, defaults: defaults) {
            return 0
        }
        let remaining = expirationDate.timeIntervalSince(currentTime)
        guard remaining > 0 else { return 0 }
        return Int(ceil(remaining / Self.secondsPerDay))
    }

    public func promoBlockedByPaidVipMessage(
        _ rawCode: String,
        defaults: UserDefaults = .standard,
        currentTime: Date = Date()
    ) -> String? {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard Self.isPromoCode(code) else { return nil }
        guard evaluateVipStatus(currentTime: currentTime, defaults: defaults) else { return nil }
        guard !Self.isPromoVipCode(Self.storedVipCode(defaults: defaults)) else { return nil }
        return "此設備已有生效中的專業年繳版 VIP 授權，無需使用體驗推廣代碼。"
    }

    public static func isPromoVipCode(_ rawCode: String?) -> Bool {
        guard let code = rawCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else { return false }
        if code == "PROMO_VERIFIED" { return true }
        return code.range(of: "^\\d{2}FR-NR$", options: .regularExpression) != nil
    }

    public static func isPromoCode(_ rawCode: String) -> Bool {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        let pattern = "^(\\d{2})FR-NR$"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(location: 0, length: code.utf16.count)
        guard let match = regex.firstMatch(in: code, options: [], range: range),
              let yearRange = Range(match.range(at: 1), in: code),
              let codeYear = Int(code[yearRange]) else {
            return false
        }
        let currentYear = Calendar.current.component(.year, from: Date()) % 100
        return codeYear == currentYear
    }

    @discardableResult
    public func activateLicenseCode(_ rawCode: String, defaults: UserDefaults = .standard, testVipPublicKeyOverride: String? = nil, overrideCurrentDate: Date? = nil) -> (success: Bool, message: String) {
        let now = overrideCurrentDate ?? Date()
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !code.isEmpty else {
            return (false, "授權碼不能為空")
        }

        let isPromo = Self.isPromoCode(code)
        let vipSerialInfo = isPromo ? nil : VipSerialVerifier.verify(code, publicKeySPKIBase64Override: testVipPublicKeyOverride)

        if !isPromo && vipSerialInfo == nil {
            return (false, "無效的授權序號或推廣代碼")
        }

        if isPromo {
            if let blocked = promoBlockedByPaidVipMessage(code, defaults: defaults, currentTime: now) {
                return (false, blocked)
            }

            var redeemedList = defaults.stringArray(forKey: userDefaultsRedeemedPromosKey) ?? []
            if redeemedList.contains(code) {
                return (false, "本設備已兌換過此年度推廣代碼（\(code)），無法重複領取。")
            }

            let expiresTs = firstLaunchDate.addingTimeInterval(Double(Self.promoTotalTrialDays) * Self.secondsPerDay).timeIntervalSince1970
            if expiresTs <= now.timeIntervalSince1970 {
                return (false, "此推廣代碼體驗期限為首次啟用起算 \(Self.promoTotalTrialDays) 天。本設備首次啟用已超過 \(Self.promoTotalTrialDays) 天，無法再使用此代碼，請升級專業年繳版。")
            }

            redeemedList.append(code)
            defaults.set(redeemedList, forKey: userDefaultsRedeemedPromosKey)
            defaults.set(true, forKey: "fitness_rider_vip_active")
            defaults.set(expiresTs, forKey: "fitness_rider_vip_expires")
            defaults.set(code, forKey: "fitness_rider_vip_code")
            defaults.set(false, forKey: userDefaultsExpiredKey)

            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.vipLicenseKey = code
                DeviceIdentifierService.shared.vipExpiresTimestamp = expiresTs
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = false
            }

            self.isVIP = true
            self.isExpiredOnLaunch = false
            self.vipPlanName = Self.planName(isPromo: true)
            return (true, "推廣課程專屬代碼兌換成功！已為此設備啟用 \(Self.promoTotalTrialDays) 天全功能免費 VIP 體驗。")
        }

        if let vipSerialInfo {
            let planSec: TimeInterval = Double(vipSerialInfo.planDays) * 86_400.0
            let expiresTs = now.addingTimeInterval(planSec).timeIntervalSince1970

            defaults.set(true, forKey: "fitness_rider_vip_active")
            defaults.set(expiresTs, forKey: "fitness_rider_vip_expires")
            defaults.set(code, forKey: "fitness_rider_vip_code")
            defaults.set(false, forKey: userDefaultsExpiredKey)

            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.vipLicenseKey = code
                DeviceIdentifierService.shared.vipExpiresTimestamp = expiresTs
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = false
            }

            self.isVIP = true
            self.isExpiredOnLaunch = false
            self.vipPlanName = Self.planName(isPromo: false)
            return (true, "授權開通成功！已升級為「專業年繳版 (VIP)」")
        }

        return (false, "無效的授權序號或推廣代碼")
    }

    public func activateVipFromServer(expiresAt: Date, isPromo: Bool = false, code: String? = nil, defaults: UserDefaults = .standard) {
        let expiresTs = expiresAt.timeIntervalSince1970
        let effectiveCode = code ?? (isPromo ? "promo_verified" : "server_verified")
        defaults.set(true, forKey: "fitness_rider_vip_active")
        defaults.set(expiresTs, forKey: "fitness_rider_vip_expires")
        defaults.set(effectiveCode, forKey: "fitness_rider_vip_code")
        defaults.set(false, forKey: userDefaultsExpiredKey)
        if defaults == UserDefaults.standard {
            DeviceIdentifierService.shared.vipLicenseKey = effectiveCode
            DeviceIdentifierService.shared.vipExpiresTimestamp = expiresTs
            DeviceIdentifierService.shared.isTrialPermanentlyLocked = false
        }
        self.isVIP = true
        self.isExpiredOnLaunch = false
        self.vipPlanName = Self.planName(isPromo: isPromo)
    }

    public func reconcileFirstLaunchAnchor(serverAnchor: Date) {
        let serverTs = serverAnchor.timeIntervalSince1970
        guard serverTs > 0 else { return }
        let localTs = firstLaunchDate.timeIntervalSince1970
        if serverTs < localTs {
            DeviceIdentifierService.shared.trialStartTimestamp = serverTs
            UserDefaults.standard.set(serverTs, forKey: userDefaultsFirstLaunchKey)
            self.firstLaunchDate = serverAnchor
        }
    }

    public var trialStartDateFormatted: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: firstLaunchDate)
    }

    public var buildDateFormatted: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: buildDate)
    }

    public var expirationDateFormatted: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: expirationDate)
    }

    public var appVersionString: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "v\(version) (\(build))"
    }

    public var planStatusDescription: String {
        if isVIP {
            return vipPlanName
        }
        let days = remainingDays()
        if days > 0 {
            return "全功能免費試用版 (剩餘 \(days) 天)"
        } else {
            return "免費試用已結束"
        }
    }
}
