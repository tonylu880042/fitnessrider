import Foundation

public final class VersionLifecycleManager: ObservableObject, @unchecked Sendable {
    public static let shared = VersionLifecycleManager()

    public static let lifecycleDays: Int = 7
    /// 推廣代碼延長一次到「總共」多少天（不是再加 30 天），對應
    /// backend/src/lib/licenseConfig.ts 的 PROMO_TOTAL_TRIAL_DAYS 與
    /// promo.properties 的 TRIAL_DAYS。三邊數字一致性由
    /// FitnessRiderTests 的 testBusinessConstantsMatchAcrossPlatforms 把關。
    public static let promoTotalTrialDays: Int = 30
    public static let secondsPerDay: TimeInterval = 86_400.0
    public static let lifecycleDuration: TimeInterval = Double(lifecycleDays) * secondsPerDay

    public static let updateURL = URL(string: "https://appdistribution.firebase.dev/i/d740076f27b77ab0")!

    private let userDefaultsLastLaunchKey = "fitness_rider_last_launch_timestamp"
    private let userDefaultsExpiredKey = "fitness_rider_is_expired"
    private let userDefaultsFirstLaunchKey = "fitness_rider_first_launch_timestamp"
    private let userDefaultsRedeemedPromosKey = "fitness_rider_redeemed_promos"

    /// The build / compile date of the application bundle (kept for diagnostic/backward compatibility).
    public let buildDate: Date

    /// The anchor date for the free trial (device first launch date).
    /// `private(set)` rather than `let` so [reconcileFirstLaunchAnchor] can correct it in-session
    /// when the server has an earlier anchor on file (spec 項目 D), not just on next relaunch.
    public private(set) var firstLaunchDate: Date

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
        // 方案名稱要從「存下來的代碼」還原，否則每次重啟 App 都會退回預設的「專業年繳版」，
        // 推廣方案的使用者會看到錯的方案名（Android 的 getVipPlanName 是即時算的，這裡補上對等行為）。
        self.vipPlanName = Self.planName(isPromo: Self.isPromoVipCode(Self.storedVipCode(defaults: .standard)))
    }

    /// 方案顯示名稱的唯一來源，避免四個寫入點各自寫死字串而漂移。
    static func planName(isPromo: Bool) -> String {
        isPromo ? "推廣課程專屬版 (\(promoTotalTrialDays)天免費)" : "專業年繳版 (VIP)"
    }

    /// 目前本機記錄的 VIP 代碼。UserDefaults 優先，正式環境（`.standard`）才回退看 Keychain ——
    /// iOS 重裝會清掉 UserDefaults 但 Keychain 會留著，只看 UserDefaults 會把還在效期內的
    /// 推廣方案誤判成付費年繳版。測試用的自訂 suite 不碰共用 Keychain，保持可重現。
    static func storedVipCode(defaults: UserDefaults) -> String? {
        if let code = defaults.string(forKey: "fitness_rider_vip_code") { return code }
        return defaults == UserDefaults.standard ? DeviceIdentifierService.shared.vipLicenseKey : nil
    }

    /// Check if device has an active VIP license.
    ///
    /// A missing/zero `expires` value must NOT be treated as "no expiry" (that used to let a
    /// license row with a lost/never-set expiry date grant unlimited VIP access forever).
    /// Missing expiry now means "not VIP" (spec 項目 C).
    public func evaluateVipStatus(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        if defaults.bool(forKey: "fitness_rider_vip_active") {
            let expires = defaults.double(forKey: "fitness_rider_vip_expires")
            if expires > currentTime.timeIntervalSince1970 {
                return true
            }
        }
        // 只有正式環境才回退看共用 Keychain；測試用的自訂 suite 不該讀到別的測試留下的 VIP
        // （與 isExpired 的 isTrialPermanentlyLocked 判斷同一個慣例）。
        if defaults == UserDefaults.standard,
           let _ = DeviceIdentifierService.shared.vipLicenseKey,
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

    /// 推廣碼是否該被「生效中的付費年繳 VIP」擋下來：要擋就回傳錯誤訊息，不用擋回傳 nil。
    ///
    /// 本機與線上開通共用同一條規則（對應後端的 VIP_ALREADY_ACTIVE）。線上流程一定要在送出請求
    /// 「之前」先問過這裡 —— 伺服器只認得它自己記錄過的授權，付費序號當初若是離線開通的，
    /// 伺服器查無付費授權就會放行推廣碼，回傳 firstLaunchDate + promoTotalTrialDays，
    /// 把本機的 365 天蓋成 30 天。對應 Android 的 `promoBlockedByPaidVipMessage`。
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

    /// 目前這份 VIP 狀態是不是「推廣代碼換來的」。刻意不看年度 ——
    /// `isPromoCode` 只認當年度代碼，但跨年時 26FR-NR 換來的授權可能還沒到期，
    /// 那時它仍然是推廣方案，不是付費年繳版。對應 Android 的 `isPromoVipCode`。
    public static func isPromoVipCode(_ rawCode: String?) -> Bool {
        guard let code = rawCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else { return false }
        if code == "PROMO_VERIFIED" { return true }
        return code.range(of: "^\\d{2}FR-NR$", options: .regularExpression) != nil
    }

    /// Check if a code is a valid promotional code for the CURRENT year (e.g. 26FR-NR in 2026).
    ///
    /// Only the code for the current year is valid. A future year's code (e.g. entering
    /// `99FR-NR` today) is format-valid but not yet active, and must be treated as invalid —
    /// otherwise it would never expire and would grant an unlimited-lifetime promo trial
    /// (spec 項目 G，backend/src/lib/db.ts 的 checkPromoCodeStatus 有相同修正)。
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

    /// Activate app via license code or promotional code (Offline algorithmic check + persistence).
    ///
    /// 推廣代碼：延長一次到「總共 `promoTotalTrialDays` 天」，不是在現在的時間上再加 30 天，
    /// 因此到期時間 = 裝置試用起算時間（firstLaunchDate）+ promoTotalTrialDays
    /// （與後端 activateLicenseWithCode 的計算方式一致）。
    /// 付費 VIP：改用 [VipSerialVerifier] 做 P-256 驗簽，天數由序號內容決定，
    /// 不再有任何寫死序號或前綴規則。
    /// `testVipPublicKeyOverride` 只給測試用（見 FitnessRiderTests），讓測試可以用自己產生的
    /// 金鑰對走完整條開通流程，不必碰正式私鑰；正式呼叫端一律不傳這個參數。
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

        // Single device check for promo code (anti-abuse)
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

            // Save to defaults
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

    /// 信任伺服器已驗證過身分的授權結果，直接寫入本機 VIP 狀態
    /// （帳號密碼換機、線上兌換推廣代碼或已在伺服器驗過簽章的序號換機成功後呼叫）。
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

    /// 用伺服器回傳的試用起算時間校正本機錨點，取「較早」的一個 —— 這樣即使本機
    /// 快取遺失（理論上 iOS Keychain 通常會存活，但仍以伺服器為最終防線），也不會被重置
    /// 試用（spec 項目 D，與 Android 的 reconcileFirstLaunchAnchor 對應）。
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
