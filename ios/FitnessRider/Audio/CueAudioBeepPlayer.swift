import Foundation
import AudioToolbox
import UIKit

public final class CueAudioBeepPlayer: Sendable {
    public static let shared = CueAudioBeepPlayer()

    private init() {}

    public func playCountdownBeep(secondsLeft: Int) {
        AudioServicesPlaySystemSound(1104)
    }

    public func playActionStartBeep() {
        AudioServicesPlaySystemSound(1025)
    }
}

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
