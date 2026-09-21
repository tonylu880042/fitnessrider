import Foundation

public final class VersionLifecycleManager: ObservableObject, @unchecked Sendable {
    public static let shared = VersionLifecycleManager()

    public static let lifecycleDays: Int = 30
    public static let secondsPerDay: TimeInterval = 86_400.0
    public static let lifecycleDuration: TimeInterval = Double(lifecycleDays) * secondsPerDay

    public static let updateURL = URL(string: "https://appdistribution.firebase.dev/i/d740076f27b77ab0")!

    private let userDefaultsLastLaunchKey = "fitness_rider_last_launch_timestamp"
    private let userDefaultsExpiredKey = "fitness_rider_is_expired"
    private let userDefaultsFirstLaunchKey = "fitness_rider_first_launch_timestamp"

    /// The build / compile date of the application bundle (kept for diagnostic/backward compatibility).
    public let buildDate: Date

    /// The anchor date for the 30-day full-featured free trial (device first launch date).
    public let firstLaunchDate: Date

    /// The calculated expiration date (firstLaunchDate + fixed 30 * 86400s, immune to DST shifts).
    public var expirationDate: Date {
        firstLaunchDate.addingTimeInterval(Self.lifecycleDuration)
    }

    /// Evaluated on app launch or updated upon license activation.
    @Published public private(set) var isExpiredOnLaunch: Bool = false

    /// VIP activation status.
    @Published public private(set) var isVIP: Bool = false
    @Published public private(set) var vipPlanName: String = "專業年繳版 (VIP)"

    public init(buildDate: Date? = nil, explicitFirstLaunchDate: Date? = nil) {
        // 1. Resolve build date
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

        // 2. Resolve first launch date (priority: explicit test date -> Keychain -> UserDefaults -> now)
        if let explicitTrial = explicitFirstLaunchDate {
            self.firstLaunchDate = explicitTrial
        } else if let explicitBuild = buildDate {
            // For unit tests that initialize with buildDate only, default firstLaunchDate to buildDate
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

        // 3. Evaluate VIP status
        self.isVIP = evaluateVipStatus()
    }

    /// Check if device has an active VIP license.
    public func evaluateVipStatus(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        if defaults.bool(forKey: "fitness_rider_vip_active") {
            let expires = defaults.double(forKey: "fitness_rider_vip_expires")
            if expires == 0 || expires > currentTime.timeIntervalSince1970 {
                return true
            }
        }
        if let _ = DeviceIdentifierService.shared.vipLicenseKey,
           let expires = DeviceIdentifierService.shared.vipExpiresTimestamp {
            if expires > currentTime.timeIntervalSince1970 {
                return true
            }
        }
        return false
    }

    /// Call once at App initialization to evaluate expiration and record launch outside SwiftUI body.
    @discardableResult
    public func evaluateExpirationOnLaunch(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        self.isVIP = evaluateVipStatus(currentTime: currentTime, defaults: defaults)
        let expired = isExpired(currentTime: currentTime, defaults: defaults)
        self.isExpiredOnLaunch = expired
        return expired
    }

    /// Check if the 30-day full-featured free trial has expired or clock was rolled back.
    /// When expired, persists lock so subsequent clock rollbacks cannot unlock.
    public func isExpired(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        // VIP bypasses trial expiration completely
        if evaluateVipStatus(currentTime: currentTime, defaults: defaults) {
            return false
        }

        // 1. Permanent lock check (Keychain or UserDefaults)
        if defaults.bool(forKey: userDefaultsExpiredKey) {
            return true
        }
        // Only query Keychain if not running under a custom test defaults suite
        if defaults == UserDefaults.standard && DeviceIdentifierService.shared.isTrialPermanentlyLocked {
            return true
        }

        let lastLaunch = defaults.double(forKey: userDefaultsLastLaunchKey)
        let currentSeconds = currentTime.timeIntervalSince1970

        // 2. Anti-clock rollback check (> 1 hour backwards from last launch)
        if lastLaunch > 0 && currentSeconds < (lastLaunch - 3600.0) {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = true
            }
            return true
        }

        // 3. Expiration date check (30 days from first launch)
        if currentTime >= expirationDate {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            defaults.set(max(currentSeconds, lastLaunch), forKey: userDefaultsLastLaunchKey)
            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = true
            }
            return true
        }

        // 4. Record current launch time
        if currentSeconds > lastLaunch {
            defaults.set(currentSeconds, forKey: userDefaultsLastLaunchKey)
        }

        return false
    }

    /// Remaining days of 30-day trial validity (0 if expired).
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

    /// Activate app via license code (Offline algorithmic check + persistence).
    @discardableResult
    public func activateLicenseCode(_ rawCode: String, defaults: UserDefaults = .standard) -> (success: Bool, message: String) {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !code.isEmpty else {
            return (false, "授權碼不能為空")
        }

        // Algorithmic & Pre-shared keys verification
        let isValidVIP = code == "RIDER-VIP-2026-PASS" ||
                         code == "FITNESS-PRO-ANNUAL-KEY" ||
                         (code.hasPrefix("RIDER-VIP-") && code.count >= 14)

        if isValidVIP {
            let oneYearSec: TimeInterval = 365.0 * 86_400.0
            let expiresTs = Date().addingTimeInterval(oneYearSec).timeIntervalSince1970

            // Save to defaults
            defaults.set(true, forKey: "fitness_rider_vip_active")
            defaults.set(expiresTs, forKey: "fitness_rider_vip_expires")
            defaults.set(code, forKey: "fitness_rider_vip_code")
            defaults.set(false, forKey: userDefaultsExpiredKey)

            // Save to Keychain if standard
            if defaults == UserDefaults.standard {
                DeviceIdentifierService.shared.vipLicenseKey = code
                DeviceIdentifierService.shared.vipExpiresTimestamp = expiresTs
                DeviceIdentifierService.shared.isTrialPermanentlyLocked = false
            }

            self.isVIP = true
            self.isExpiredOnLaunch = false
            return (true, "授權開通成功！已升級為「專業年繳版 (VIP)」")
        } else {
            return (false, "無效的授權序號，請檢查格式或聯絡客服")
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
            return "專業年繳版 (VIP)"
        }
        let days = remainingDays()
        if days > 0 {
            return "全功能免費試用版 (剩餘 \(days) 天)"
        } else {
            return "免費試用已結束"
        }
    }
}
