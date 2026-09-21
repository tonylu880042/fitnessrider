import Foundation

public final class VersionLifecycleManager: ObservableObject, @unchecked Sendable {
    public static let shared = VersionLifecycleManager()

    public static let lifecycleDays: Int = 30
    public static let updateURL = URL(string: "https://appdistribution.firebase.dev/i/d740076f27b77ab0")!

    private let userDefaultsKey = "fitness_rider_last_launch_timestamp"

    /// The build / compile date of the application bundle.
    public let buildDate: Date

    /// The calculated expiration date (buildDate + 30 days).
    public var expirationDate: Date {
        Calendar.current.date(byAdding: .day, value: Self.lifecycleDays, to: buildDate)
            ?? buildDate.addingTimeInterval(TimeInterval(Self.lifecycleDays * 86400))
    }

    public init(buildDate: Date? = nil) {
        if let explicit = buildDate {
            self.buildDate = explicit
        } else {
            // Attempt to read executable binary creation/modification date
            var resolvedDate: Date? = nil
            if let execURL = Bundle.main.executableURL,
               let attrs = try? FileManager.default.attributesOfItem(atPath: execURL.path) {
                resolvedDate = attrs[.creationDate] as? Date ?? attrs[.modificationDate] as? Date
            }
            if resolvedDate == nil,
               let infoPath = Bundle.main.path(forResource: "Info", ofType: "plist"),
               let attrs = try? FileManager.default.attributesOfItem(atPath: infoPath) {
                resolvedDate = attrs[.creationDate] as? Date ?? attrs[.modificationDate] as? Date
            }
            // Fallback: compile time or current date
            self.buildDate = resolvedDate ?? Date()
        }
    }

    /// Check if the build has expired (exceeded 30 days) or clock was rolled back.
    public func isExpired(currentTime: Date = Date(), defaults: UserDefaults = .standard) -> Bool {
        // 1. Expiration check (30-day lifecycle)
        if currentTime >= expirationDate {
            return true
        }

        // 2. Anti-clock rollback check
        let lastLaunch = defaults.double(forKey: userDefaultsKey)
        let currentSeconds = currentTime.timeIntervalSince1970

        // If system clock rolled back more than 1 hour behind last recorded launch
        if lastLaunch > 0 && currentSeconds < (lastLaunch - 3600.0) {
            return true
        }

        // If last launch was already past expiration, stay expired
        if lastLaunch > 0 && lastLaunch >= expirationDate.timeIntervalSince1970 {
            return true
        }

        // Record current launch time
        if currentSeconds > lastLaunch {
            defaults.set(currentSeconds, forKey: userDefaultsKey)
        }

        return false
    }

    /// Remaining days of validity (0 if expired).
    public func remainingDays(currentTime: Date = Date()) -> Int {
        let remaining = expirationDate.timeIntervalSince(currentTime)
        guard remaining > 0 else { return 0 }
        return Int(ceil(remaining / 86400.0))
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
