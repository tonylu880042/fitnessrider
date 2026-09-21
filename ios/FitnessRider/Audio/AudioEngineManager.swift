import Foundation
import AVFoundation
import MediaPlayer

// MARK: - Crossfade Calculator

public struct CrossfadeCalculator: Sendable {
    /// Equal-Power Crossfade volumes: fadeOut = cos(t * π / 2), fadeIn = sin(t * π / 2)
    /// where progress is in [0.0, 1.0].
    /// Maintains constant acoustic power: (fadeOut^2 + fadeIn^2 = 1.0)
    public static func equalPowerVolumes(progress: Double) -> (fadeOut: Float, fadeIn: Float) {
        let clamped = max(0.0, min(1.0, progress))
        let angle = clamped * (.pi / 2.0)
        let fadeOut = Float(cos(angle))
        let fadeIn = Float(sin(angle))
        return (fadeOut, fadeIn)
    }

    /// Determines effective crossfade duration based on settings, remaining track time, and auto-pause.
    public static func effectiveDuration(
        requestedDuration: Double,
        segmentDuration: Double,
        isAutoPauseEnabled: Bool
    ) -> Double {
        if isAutoPauseEnabled || requestedDuration <= 0.0 || segmentDuration <= 0.0 {
            return 0.0
        }
        return min(requestedDuration, segmentDuration * 0.5)
    }
}

// MARK: - Audio Deck

@MainActor
private final class AudioDeck {
    let id: String
    let playerNode = AVAudioPlayerNode()
    let timePitchUnit = AVAudioUnitTimePitch()

    var currentAudioFile: AVAudioFile?
    var audioLengthSamples: AVAudioFramePosition = 0
    var sampleRate: Double = 44100.0
    var currentDurationSeconds: Double = 0.0
    var currentOffsetSeconds: Double = 0.0
    var segmentIndex: Int = -1

    init(id: String) {
        self.id = id
    }

    func attach(to engine: AVAudioEngine) {
        engine.attach(playerNode)
        engine.attach(timePitchUnit)

        timePitchUnit.pitch = 0.0
        timePitchUnit.rate = 1.0

        let mainMixer = engine.mainMixerNode
        let format = mainMixer.outputFormat(forBus: 0)

        engine.connect(playerNode, to: timePitchUnit, format: format)
        engine.connect(timePitchUnit, to: mainMixer, format: format)
    }

    func setVolume(_ volume: Float) {
        playerNode.volume = max(0.0, min(1.0, volume))
    }

    func setRate(_ rate: Double) {
        timePitchUnit.rate = Float(rate)
        timePitchUnit.pitch = 0.0
    }

    func stop() {
        playerNode.stop()
        currentAudioFile = nil
        audioLengthSamples = 0
        currentDurationSeconds = 0.0
        currentOffsetSeconds = 0.0
        segmentIndex = -1
        setVolume(1.0)
    }
}

// MARK: - AudioEngineManager

@MainActor
public final class AudioEngineManager: ObservableObject {
    public static let shared = AudioEngineManager()

    // AVFoundation Engine & Dual Decks
    private let audioEngine = AVAudioEngine()
    private let deckA = AudioDeck(id: "DeckA")
    private let deckB = AudioDeck(id: "DeckB")
    private var activeDeckIndex: Int = 0 // 0 = DeckA, 1 = DeckB

    private var activeDeck: AudioDeck {
        activeDeckIndex == 0 ? deckA : deckB
    }

    private var incomingDeck: AudioDeck {
        activeDeckIndex == 0 ? deckB : deckA
    }

    // State properties
    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var isCrossfading: Bool = false
    @Published public private(set) var currentRate: Double = 1.0 // 0.85 ~ 1.15
    @Published public private(set) var currentOffsetSeconds: Double = 0.0
    @Published public private(set) var currentDurationSeconds: Double = 0.0
    @Published public private(set) var currentSegmentIndex: Int = 0

    // Currently playing class & segment
    public private(set) var currentClass: WorkoutClass?
    public private(set) var currentSegment: WorkoutSegment?

    private var displayLinkTimer: Timer?

    // Countdown beeps tracker
    private var lastTriggeredCueId: UUID?
    private var lastBeepSecond: Int = -1

    private init() {
        setupAudioSession()
        setupEngine()
        setupRemoteCommands()
    }

    // MARK: - Setup

    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers, .allowBluetoothHFP, .allowBluetoothA2DP])
            try session.setActive(true)
        } catch {
            print("Failed to activate AVAudioSession: \(error)")
        }
    }

    private func setupEngine() {
        deckA.attach(to: audioEngine)
        deckB.attach(to: audioEngine)

        do {
            try audioEngine.start()
        } catch {
            print("Failed to start AVAudioEngine: \(error)")
        }
    }

    // MARK: - Load & Playback

    public func loadClass(_ workoutClass: WorkoutClass, startSegmentIndex: Int = 0) {
        self.currentClass = workoutClass
        self.currentSegmentIndex = max(0, min(startSegmentIndex, workoutClass.segments.count - 1))
        cancelCrossfade()
        loadSegment(on: activeDeck, segmentIndex: currentSegmentIndex)
        incomingDeck.stop()
    }

    private func loadSegment(on deck: AudioDeck, segmentIndex: Int) {
        guard let currentClass = currentClass,
              segmentIndex >= 0 && segmentIndex < currentClass.segments.count else { return }

        let segment = currentClass.segments[segmentIndex]
        deck.segmentIndex = segmentIndex
        deck.setRate(segment.playbackRate)
        deck.setVolume(1.0)
        deck.playerNode.stop()

        // Layer 3：segment.musicFileName 可能是 Music/ 目錄下的檔名，也可能是外部資料夾的
        // "extfolder://" 相對路徑，一律交給 MusicSource 判斷來源與存在性；AVAudioFile 的初始化
        // 要在 security-scoped 存取視窗裡完成，之後系統的檔案描述子仍可繼續讀取。
        let file: AVAudioFile? = MusicSource.withResolvedFileURL(for: segment.musicFileName) { url in
            try? AVAudioFile(forReading: url)
        }.flatMap { $0 }

        if let file = file {
            deck.currentAudioFile = file
            deck.audioLengthSamples = file.length
            deck.sampleRate = file.processingFormat.sampleRate
            deck.currentDurationSeconds = Double(file.length) / deck.sampleRate
            deck.currentOffsetSeconds = 0.0
            scheduleBuffer(on: deck, fromSample: 0)
        } else {
            // Simulated duration from segment if file not present locally
            deck.currentAudioFile = nil
            deck.currentDurationSeconds = Double(segment.durationMs) / 1000.0
            deck.currentOffsetSeconds = 0.0
        }

        if deck === activeDeck {
            self.currentSegment = segment
            self.currentRate = segment.playbackRate
            self.currentDurationSeconds = deck.currentDurationSeconds
            self.currentOffsetSeconds = 0.0
            updateNowPlayingInfo()
        }
    }

    private func scheduleBuffer(on deck: AudioDeck, fromSample sample: AVAudioFramePosition) {
        guard let file = deck.currentAudioFile else { return }
        deck.playerNode.stop()

        let remainingSamples = deck.audioLengthSamples - sample
        guard remainingSamples > 0 else { return }

        let deckId = deck.id
        let segmentIndex = deck.segmentIndex

        deck.playerNode.scheduleSegment(
            file,
            startingFrame: sample,
            frameCount: AVAudioFrameCount(remainingSamples),
            at: nil
        ) { [weak self] in
            DispatchQueue.main.async {
                self?.handleTrackBufferFinished(deckId: deckId, segmentIndex: segmentIndex)
            }
        }
    }

    public func play() {
        if !audioEngine.isRunning {
            try? audioEngine.start()
        }
        if activeDeck.currentAudioFile != nil {
            activeDeck.playerNode.play()
        }
        if isCrossfading && incomingDeck.currentAudioFile != nil {
            incomingDeck.playerNode.play()
        }
        isPlaying = true
        startTimer()
        updateNowPlayingInfo()
    }

    public func pause() {
        activeDeck.playerNode.pause()
        if isCrossfading {
            incomingDeck.playerNode.pause()
        }
        isPlaying = false
        stopTimer()
        updateNowPlayingInfo()
    }

    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    public func seek(to seconds: Double) {
        cancelCrossfade()
        let clampedSeconds = max(0, min(seconds, currentDurationSeconds))
        self.currentOffsetSeconds = clampedSeconds
        activeDeck.currentOffsetSeconds = clampedSeconds

        if let _ = activeDeck.currentAudioFile {
            let targetSample = AVAudioFramePosition(clampedSeconds * activeDeck.sampleRate)
            let wasPlaying = isPlaying
            scheduleBuffer(on: activeDeck, fromSample: targetSample)
            if wasPlaying {
                activeDeck.playerNode.play()
            }
        }
        updateNowPlayingInfo()
    }

    public func seekBy(deltaSeconds: Double) {
        seek(to: currentOffsetSeconds + deltaSeconds)
    }

    // MARK: - Tempo & Pitch Shift (0.85x ~ 1.15x, ±2% steps)

    public func adjustRatePercent(by deltaPercent: Double) {
        let newRate = currentRate + (deltaPercent / 100.0)
        setRate(newRate)
    }

    public func setRate(_ rate: Double) {
        // Clamp strictly between 0.85 and 1.15 (±15%)
        let clampedRate = max(0.85, min(1.15, (rate * 100.0).rounded() / 100.0))
        self.currentRate = clampedRate
        activeDeck.setRate(clampedRate)

        // Sync back to current segment
        currentSegment?.playbackRate = clampedRate
        updateNowPlayingInfo()
    }

    public func resetRate() {
        setRate(1.0)
    }

    // MARK: - Next & Previous Segment

    public func nextSegment() {
        guard let currentClass = currentClass else { return }
        if currentSegmentIndex < currentClass.segments.count - 1 {
            cancelCrossfade()
            currentSegmentIndex += 1
            loadSegment(on: activeDeck, segmentIndex: currentSegmentIndex)
            incomingDeck.stop()
            if isPlaying { play() }
        }
    }

    public func previousSegment() {
        cancelCrossfade()
        if currentOffsetSeconds > 3.0 {
            seek(to: 0)
        } else if currentSegmentIndex > 0 {
            currentSegmentIndex -= 1
            loadSegment(on: activeDeck, segmentIndex: currentSegmentIndex)
            incomingDeck.stop()
            if isPlaying { play() }
        } else {
            seek(to: 0)
        }
    }

    // MARK: - Crossfade State Machine

    private func startCrossfade(effectiveDuration: Double) {
        guard let currentClass = currentClass,
              currentSegmentIndex < currentClass.segments.count - 1 else { return }

        isCrossfading = true
        let nextIndex = currentSegmentIndex + 1
        let nextSegment = currentClass.segments[nextIndex]

        loadSegment(on: incomingDeck, segmentIndex: nextIndex)
        incomingDeck.setRate(nextSegment.playbackRate)
        incomingDeck.setVolume(0.0)

        if incomingDeck.currentAudioFile != nil {
            incomingDeck.playerNode.play()
        }

        let remaining = max(0.0, currentDurationSeconds - currentOffsetSeconds)
        let progress = 1.0 - (remaining / effectiveDuration)
        let (fadeOut, fadeIn) = CrossfadeCalculator.equalPowerVolumes(progress: progress)
        activeDeck.setVolume(fadeOut)
        incomingDeck.setVolume(fadeIn)
    }

    private func updateCrossfade(effectiveDuration: Double, remaining: Double) {
        if remaining <= 0.0 {
            finishCrossfade(effectiveDuration: effectiveDuration)
            return
        }

        let progress = 1.0 - (remaining / effectiveDuration)
        let (fadeOut, fadeIn) = CrossfadeCalculator.equalPowerVolumes(progress: progress)
        activeDeck.setVolume(fadeOut)
        incomingDeck.setVolume(fadeIn)
    }

    private func finishCrossfade(effectiveDuration: Double) {
        guard isCrossfading else { return }
        isCrossfading = false

        let oldDeck = activeDeck
        oldDeck.playerNode.stop()
        oldDeck.setVolume(1.0)

        incomingDeck.setVolume(1.0)
        activeDeckIndex = 1 - activeDeckIndex

        currentSegmentIndex += 1
        if let currentClass = currentClass, currentSegmentIndex < currentClass.segments.count {
            currentSegment = currentClass.segments[currentSegmentIndex]
            currentRate = currentSegment?.playbackRate ?? 1.0
            currentDurationSeconds = activeDeck.currentDurationSeconds
            currentOffsetSeconds = effectiveDuration
            activeDeck.currentOffsetSeconds = effectiveDuration
            updateNowPlayingInfo()
        }
    }

    public func cancelCrossfade() {
        guard isCrossfading else { return }
        isCrossfading = false
        incomingDeck.playerNode.stop()
        incomingDeck.stop()
        activeDeck.setVolume(1.0)
    }

    private func handleTrackBufferFinished(deckId: String, segmentIndex: Int) {
        guard deckId == activeDeck.id, segmentIndex == currentSegmentIndex else { return }
        if isCrossfading {
            let effectiveCrossfade = CrossfadeCalculator.effectiveDuration(
                requestedDuration: AppSettings.shared.crossfadeDurationSeconds,
                segmentDuration: currentDurationSeconds,
                isAutoPauseEnabled: AppSettings.shared.isAutoPauseBetweenSegmentsEnabled
            )
            finishCrossfade(effectiveDuration: effectiveCrossfade)
        } else {
            handleTrackCompletion()
        }
    }

    private func handleTrackCompletion() {
        guard let currentClass = currentClass else { return }

        if AppSettings.shared.isAutoPauseBetweenSegmentsEnabled {
            pause()
            if currentSegmentIndex < currentClass.segments.count - 1 {
                currentSegmentIndex += 1
                loadSegment(on: activeDeck, segmentIndex: currentSegmentIndex)
                incomingDeck.stop()
            }
        } else {
            // Auto advance
            if currentSegmentIndex < currentClass.segments.count - 1 {
                currentSegmentIndex += 1
                loadSegment(on: activeDeck, segmentIndex: currentSegmentIndex)
                incomingDeck.stop()
                play()
            } else {
                pause()
                seek(to: 0)
            }
        }
    }

    // MARK: - Timer & Cue Countdown

    private func startTimer() {
        stopTimer()
        displayLinkTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.tick()
            }
        }
    }

    private func stopTimer() {
        displayLinkTimer?.invalidate()
        displayLinkTimer = nil
    }

    private func tick() {
        guard isPlaying else { return }
        self.currentOffsetSeconds += 0.1 * currentRate
        activeDeck.currentOffsetSeconds = self.currentOffsetSeconds

        let effectiveCrossfade = CrossfadeCalculator.effectiveDuration(
            requestedDuration: AppSettings.shared.crossfadeDurationSeconds,
            segmentDuration: currentDurationSeconds,
            isAutoPauseEnabled: AppSettings.shared.isAutoPauseBetweenSegmentsEnabled
        )

        let remaining = currentDurationSeconds - currentOffsetSeconds

        guard let currentClass = currentClass else { return }
        let hasNextSegment = currentSegmentIndex < currentClass.segments.count - 1

        if !isCrossfading {
            // Check if crossfade should start
            if effectiveCrossfade > 0.0 && hasNextSegment && remaining <= effectiveCrossfade {
                startCrossfade(effectiveDuration: effectiveCrossfade)
            } else if remaining <= 0.0 {
                handleTrackCompletion()
                return
            }
        } else {
            updateCrossfade(effectiveDuration: effectiveCrossfade, remaining: remaining)
        }

        // Check next cue for countdown beeps
        checkUpcomingCueCountdown()
    }

    private func checkUpcomingCueCountdown() {
        guard let segment = currentSegment else { return }

        let isBeepEnabled = AppSettings.shared.isCountdownBeepEnabled
        let isHapticEnabled = AppSettings.shared.isHapticFeedbackEnabled
        guard isBeepEnabled || isHapticEnabled else { return }

        let currentMs = Int(currentOffsetSeconds * 1000)

        for cue in segment.cues {
            let diffMs = cue.offsetMs - currentMs
            if diffMs > 0 && diffMs <= 3100 {
                let secondsLeft = (diffMs + 500) / 1000 // 3, 2, 1
                if secondsLeft != lastBeepSecond && secondsLeft >= 1 && secondsLeft <= 3 {
                    lastBeepSecond = secondsLeft
                    if isBeepEnabled {
                        CueAudioBeepPlayer.shared.playCountdownBeep(secondsLeft: secondsLeft)
                    }
                    if isHapticEnabled {
                        HapticFeedbackManager.shared.playCountdownTick()
                    }
                }
                break
            } else if diffMs <= 0 && diffMs >= -500 {
                if lastBeepSecond != 0 {
                    lastBeepSecond = 0
                    if isBeepEnabled {
                        CueAudioBeepPlayer.shared.playActionStartBeep()
                    }
                    if isHapticEnabled {
                        HapticFeedbackManager.shared.playActionStartImpact()
                    }
                }
            }
        }
    }

    // MARK: - Remote Commands & NowPlaying

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.togglePlayPause()
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.nextSegment()
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.previousSegment()
            return .success
        }
    }

    private func updateNowPlayingInfo() {
        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = currentSegment?.title ?? currentClass?.title ?? "FitnessRider"
        info[MPMediaItemPropertyArtist] = currentClass?.author ?? "FitnessRider Coach"
        info[MPMediaItemPropertyPlaybackDuration] = currentDurationSeconds
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentOffsetSeconds
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? currentRate : 0.0

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
