import Foundation

public final class VersionLifecycleManager: ObservableObject, @unchecked Sendable {
    public static let shared = VersionLifecycleManager()

    public static let lifecycleDays: Int = 30
    public static let secondsPerDay: TimeInterval = 86_400.0
    public static let lifecycleDuration: TimeInterval = Double(lifecycleDays) * secondsPerDay

    public static let updateURL = URL(string: "https://appdistribution.firebase.dev/i/d740076f27b77ab0")!

    private let userDefaultsLastLaunchKey = "fitness_rider_last_launch_timestamp"
    private let userDefaultsExpiredKey = "fitness_rider_is_expired"

    /// The build / compile date of the application bundle.
    public let buildDate: Date

    /// The calculated expiration date (buildDate + fixed 30 * 86400s, immune to DST shifts).
    public var expirationDate: Date {
        buildDate.addingTimeInterval(Self.lifecycleDuration)
    }

    /// Evaluated once on app launch.
    @Published public private(set) var isExpiredOnLaunch: Bool = false

    public init(buildDate: Date? = nil) {
        if let explicit = buildDate {
            self.buildDate = explicit
        } else {
            // Read build timestamp from bundle Info.plist (never installation filesystem date)
            var resolvedDate: Date? = nil
            if let tsString = Bundle.main.object(forInfoDictionaryKey: "CFBundleBuildTimestamp") as? String,
               let ts = Double(tsString), ts > 0 {
                resolvedDate = Date(timeIntervalSince1970: ts)
            } else if let tsNumber = Bundle.main.object(forInfoDictionaryKey: "CFBundleBuildTimestamp") as? Double,
                      tsNumber > 0 {
                resolvedDate = Date(timeIntervalSince1970: tsNumber)
            }
            // Fallback: fixed release epoch (2026-09-21)
            self.buildDate = resolvedDate ?? Date(timeIntervalSince1970: 1789994982)
        }
    }

    /// Call once at App initialization to evaluate expiration and record launch outside SwiftUI body.
    @discardableResult
    public func evaluateExpirationOnLaunch(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        let expired = isExpired(currentTime: currentTime, defaults: defaults)
        self.isExpiredOnLaunch = expired
        return expired
    }

    /// Check if the build has expired (exceeded 30 days) or clock was rolled back.
    /// When expired, persists userDefaultsExpiredKey = true so subsequent clock rollbacks cannot unlock.
    public func isExpired(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        // 1. Permanent lock check: once expired, stays expired
        if defaults.bool(forKey: userDefaultsExpiredKey) {
            return true
        }

        let lastLaunch = defaults.double(forKey: userDefaultsLastLaunchKey)
        let currentSeconds = currentTime.timeIntervalSince1970

        // 2. Anti-clock rollback check (> 1 hour backwards from last launch)
        if lastLaunch > 0 && currentSeconds < (lastLaunch - 3600.0) {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            return true
        }

        // 3. Expiration date check (fixed 30-day duration)
        if currentTime >= expirationDate {
            defaults.set(true, forKey: userDefaultsExpiredKey)
            defaults.set(max(currentSeconds, lastLaunch), forKey: userDefaultsLastLaunchKey)
            return true
        }

        // 4. Record current launch time
        if currentSeconds > lastLaunch {
            defaults.set(currentSeconds, forKey: userDefaultsLastLaunchKey)
        }

        return false
    }

    /// Remaining days of validity (0 if expired).
    public func remainingDays(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Int {
        if isExpired(currentTime: currentTime, defaults: defaults) {
            return 0
        }
        let remaining = expirationDate.timeIntervalSince(currentTime)
        guard remaining > 0 else { return 0 }
        return Int(ceil(remaining / Self.secondsPerDay))
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
}
