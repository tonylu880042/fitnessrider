import Foundation

@MainActor
public final class AppSettings: ObservableObject {
    public static let shared = AppSettings()

    private enum Keys {
        static let isCountdownBeepEnabled = "isCountdownBeepEnabled"
        static let isAutoPauseBetweenSegmentsEnabled = "isAutoPauseBetweenSegmentsEnabled"
        static let crossfadeDurationSeconds = "crossfadeDurationSeconds"
        static let keepScreenAwakeInHUD = "keepScreenAwakeInHUD"
    }

    @Published public var isCountdownBeepEnabled: Bool {
        didSet { UserDefaults.standard.set(isCountdownBeepEnabled, forKey: Keys.isCountdownBeepEnabled) }
    }

    @Published public var isAutoPauseBetweenSegmentsEnabled: Bool {
        didSet { UserDefaults.standard.set(isAutoPauseBetweenSegmentsEnabled, forKey: Keys.isAutoPauseBetweenSegmentsEnabled) }
    }

    @Published public var crossfadeDurationSeconds: Double {
        didSet { UserDefaults.standard.set(crossfadeDurationSeconds, forKey: Keys.crossfadeDurationSeconds) }
    }

    @Published public var keepScreenAwakeInHUD: Bool {
        didSet { UserDefaults.standard.set(keepScreenAwakeInHUD, forKey: Keys.keepScreenAwakeInHUD) }
    }

    private init() {
        UserDefaults.standard.register(defaults: [
            Keys.isCountdownBeepEnabled: true,
            Keys.isAutoPauseBetweenSegmentsEnabled: false,
            Keys.crossfadeDurationSeconds: 2.0,
            Keys.keepScreenAwakeInHUD: true
        ])

        self.isCountdownBeepEnabled = UserDefaults.standard.bool(forKey: Keys.isCountdownBeepEnabled)
        self.isAutoPauseBetweenSegmentsEnabled = UserDefaults.standard.bool(forKey: Keys.isAutoPauseBetweenSegmentsEnabled)
        self.crossfadeDurationSeconds = UserDefaults.standard.double(forKey: Keys.crossfadeDurationSeconds)
        self.keepScreenAwakeInHUD = UserDefaults.standard.bool(forKey: Keys.keepScreenAwakeInHUD)
    }
}
