import Foundation

@MainActor
public final class AppSettings: ObservableObject {
    public static let shared = AppSettings()

    public static let crossfadeOptionsSeconds: [Double] = [0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 15.0]

    private enum Keys {
        static let isCountdownBeepEnabled = "isCountdownBeepEnabled"
        static let isHapticFeedbackEnabled = "isHapticFeedbackEnabled"
        static let isAutoPauseBetweenSegmentsEnabled = "isAutoPauseBetweenSegmentsEnabled"
        static let crossfadeDurationSeconds = "crossfadeDurationSeconds"
        static let keepScreenAwakeInHUD = "keepScreenAwakeInHUD"
        static let followStreamingPlayback = "followStreamingPlayback"
        static let isRider3DEnabled = "isRider3DEnabled"
        static let rider3dHost = "rider3dHost"
        static let rider3dLastSceneId = "rider3dLastSceneId"
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

    @Published public var followStreamingPlayback: Bool {
        didSet { UserDefaults.standard.set(followStreamingPlayback, forKey: Keys.followStreamingPlayback) }
    }

    @Published public var isRider3DEnabled: Bool {
        didSet { UserDefaults.standard.set(isRider3DEnabled, forKey: Keys.isRider3DEnabled) }
    }

    @Published public var rider3dHost: String {
        didSet { UserDefaults.standard.set(rider3dHost, forKey: Keys.rider3dHost) }
    }

    @Published public var rider3dLastSceneId: String {
        didSet { UserDefaults.standard.set(rider3dLastSceneId, forKey: Keys.rider3dLastSceneId) }
    }

    private init() {
        UserDefaults.standard.register(defaults: [
            Keys.isCountdownBeepEnabled: true,
            Keys.isHapticFeedbackEnabled: true,
            Keys.isAutoPauseBetweenSegmentsEnabled: false,
            Keys.crossfadeDurationSeconds: 2.0,
            Keys.keepScreenAwakeInHUD: true,
            Keys.followStreamingPlayback: true,
            Keys.isRider3DEnabled: true,
            Keys.rider3dHost: "",
            Keys.rider3dLastSceneId: ""
        ])

        self.isCountdownBeepEnabled = UserDefaults.standard.bool(forKey: Keys.isCountdownBeepEnabled)
        self.isHapticFeedbackEnabled = UserDefaults.standard.bool(forKey: Keys.isHapticFeedbackEnabled)
        self.isAutoPauseBetweenSegmentsEnabled = UserDefaults.standard.bool(forKey: Keys.isAutoPauseBetweenSegmentsEnabled)
        self.crossfadeDurationSeconds = UserDefaults.standard.double(forKey: Keys.crossfadeDurationSeconds)
        self.keepScreenAwakeInHUD = UserDefaults.standard.bool(forKey: Keys.keepScreenAwakeInHUD)
        self.followStreamingPlayback = UserDefaults.standard.bool(forKey: Keys.followStreamingPlayback)
        self.isRider3DEnabled = UserDefaults.standard.bool(forKey: Keys.isRider3DEnabled)
        self.rider3dHost = UserDefaults.standard.string(forKey: Keys.rider3dHost) ?? ""
        self.rider3dLastSceneId = UserDefaults.standard.string(forKey: Keys.rider3dLastSceneId) ?? ""
    }
}
