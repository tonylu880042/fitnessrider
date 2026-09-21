import Foundation
import AudioToolbox

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
