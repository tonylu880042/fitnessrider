import Foundation

@MainActor
public final class AppSettings: ObservableObject {
    public static let shared = AppSettings()

    /// 單一來源：Crossfade 秒數選項，Android／iOS 必須完全一致。預設值仍為 2.0 秒。
    public static let crossfadeOptionsSeconds: [Double] = [0.0, 1.0, 2.0, 3.0, 5.0, 8.0]

    private enum Keys {
        static let isCountdownBeepEnabled = "isCountdownBeepEnabled"
        static let isHapticFeedbackEnabled = "isHapticFeedbackEnabled"
        static let isAutoPauseBetweenSegmentsEnabled = "isAutoPauseBetweenSegmentsEnabled"
        static let crossfadeDurationSeconds = "crossfadeDurationSeconds"
        static let keepScreenAwakeInHUD = "keepScreenAwakeInHUD"
    }

    @Published public var isCountdownBeepEnabled: Bool {
        didSet { UserDefaults.standard.set(isCountdownBeepEnabled, forKey: Keys.isCountdownBeepEnabled) }
    }

    @Published public var isHapticFeedbackEnabled: Bool {
        didSet { UserDefaults.standard.set(isHapticFeedbackEnabled, forKey: Keys.isHapticFeedbackEnabled) }
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
            Keys.isHapticFeedbackEnabled: true,
            Keys.isAutoPauseBetweenSegmentsEnabled: false,
            Keys.crossfadeDurationSeconds: 2.0,
            Keys.keepScreenAwakeInHUD: true
        ])

        self.isCountdownBeepEnabled = UserDefaults.standard.bool(forKey: Keys.isCountdownBeepEnabled)
        self.isHapticFeedbackEnabled = UserDefaults.standard.bool(forKey: Keys.isHapticFeedbackEnabled)
        self.isAutoPauseBetweenSegmentsEnabled = UserDefaults.standard.bool(forKey: Keys.isAutoPauseBetweenSegmentsEnabled)
        self.crossfadeDurationSeconds = UserDefaults.standard.double(forKey: Keys.crossfadeDurationSeconds)
        self.keepScreenAwakeInHUD = UserDefaults.standard.bool(forKey: Keys.keepScreenAwakeInHUD)
    }
}
