import Foundation
import AudioToolbox
import UIKit

public final class CueAudioBeepPlayer: Sendable {
    public static let shared = CueAudioBeepPlayer()

    private init() {}

    /// Plays a short beep for countdown seconds: 3, 2, 1
    public func playCountdownBeep(secondsLeft: Int) {
        // System Sound 1104 / 1057 (standard short crisp tick/beep)
        AudioServicesPlaySystemSound(1104)
    }

    /// Plays a higher pitch action trigger sound (Cue activated)
    public func playActionStartBeep() {
        // System Sound 1025 (whistle / notification start chime)
        AudioServicesPlaySystemSound(1025)
    }
}

// MARK: - Haptic Feedback Manager

@MainActor
public final class HapticFeedbackManager {
    public static let shared = HapticFeedbackManager()

    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)

    private init() {
        lightImpact.prepare()
        heavyImpact.prepare()
    }

    public func playCountdownTick() {
        guard AppSettings.shared.isHapticFeedbackEnabled else { return }
        lightImpact.impactOccurred()
        lightImpact.prepare()
    }

    public func playActionStartImpact() {
        guard AppSettings.shared.isHapticFeedbackEnabled else { return }
        heavyImpact.impactOccurred()
        heavyImpact.prepare()
    }
}
