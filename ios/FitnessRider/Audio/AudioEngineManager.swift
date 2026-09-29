import Foundation
import AVFoundation
import MediaPlayer

public struct CrossfadeCalculator: Sendable {
    public static func equalPowerVolumes(progress: Double) -> (fadeOut: Float, fadeIn: Float) {
        let clamped = max(0.0, min(1.0, progress))
        let angle = clamped * (.pi / 2.0)
        let fadeOut = Float(cos(angle))
        let fadeIn = Float(sin(angle))
        return (fadeOut, fadeIn)
    }

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

    public static func manualJumpFadeDuration(
        requestedDuration: Double,
        targetSegmentDuration: Double,
        isAutoPauseEnabled: Bool,
        isPlaying: Bool
    ) -> Double {
        guard isPlaying else { return 0.0 }
        return effectiveDuration(
            requestedDuration: requestedDuration,
            segmentDuration: targetSegmentDuration,
            isAutoPauseEnabled: isAutoPauseEnabled
        )
    }

    public static func manualTailVolume(startVolume: Float, progress: Double) -> Float {
        let (fadeOut, _) = equalPowerVolumes(progress: progress)
        return startVolume * fadeOut
    }
}

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
    var scheduleGeneration: Int = 0

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
        scheduleGeneration += 1
        playerNode.stop()
        currentAudioFile = nil
        audioLengthSamples = 0
        currentDurationSeconds = 0.0
        currentOffsetSeconds = 0.0
        segmentIndex = -1
        setVolume(1.0)
    }
}

@MainActor
public final class AudioEngineManager: ObservableObject {
    public static let shared = AudioEngineManager()

    private let audioEngine = AVAudioEngine()
    private let deckA = AudioDeck(id: "DeckA")
    private let deckB = AudioDeck(id: "DeckB")
    private var activeDeckIndex: Int = 0

    private var activeDeck: AudioDeck {
        activeDeckIndex == 0 ? deckA : deckB
    }

    private var incomingDeck: AudioDeck {
        activeDeckIndex == 0 ? deckB : deckA
    }

    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var isCrossfading: Bool = false
    @Published public private(set) var currentRate: Double = 1.0
    @Published public private(set) var currentOffsetSeconds: Double = 0.0
    @Published public private(set) var currentDurationSeconds: Double = 0.0
    @Published public private(set) var currentSegmentIndex: Int = 0

    public private(set) var currentClass: WorkoutClass?
    public private(set) var currentSegment: WorkoutSegment?

    private var displayLinkTimer: Timer?

    private var lastTriggeredCueId: UUID?
    private var lastBeepSecond: Int = -1

    private var manualTailDeckIndex: Int?
    private var manualTailDuration: Double = 0.0
    private var manualTailElapsed: Double = 0.0
    private var manualTailStartVolume: Float = 1.0

    private var manualTailDeck: AudioDeck? {
        guard let idx = manualTailDeckIndex else { return nil }
        return idx == 0 ? deckA : deckB
    }

    private init() {
        setupAudioSession()
        setupEngine()
        setupRemoteCommands()
    }

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

    public func loadClass(_ workoutClass: WorkoutClass, startSegmentIndex: Int = 0) {
        self.currentClass = workoutClass
        self.currentSegmentIndex = max(0, min(startSegmentIndex, workoutClass.segments.count - 1))
        cancelCrossfade()
        clearManualTail()
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
        deck.scheduleGeneration += 1
        let generation = deck.scheduleGeneration

        deck.playerNode.scheduleSegment(
            file,
            startingFrame: sample,
            frameCount: AVAudioFrameCount(remainingSamples),
            at: nil
        ) { [weak self] in
            DispatchQueue.main.async {
                self?.handleTrackBufferFinished(deckId: deckId, segmentIndex: segmentIndex, generation: generation)
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
        if let tailDeck = manualTailDeck, tailDeck.currentAudioFile != nil {
            tailDeck.playerNode.play()
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
        manualTailDeck?.playerNode.pause()
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
        clearManualTail()
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

    public func adjustRatePercent(by deltaPercent: Double) {
        let newRate = currentRate + (deltaPercent / 100.0)
        setRate(newRate)
    }

    public func setRate(_ rate: Double) {
        let clampedRate = max(0.85, min(1.15, (rate * 100.0).rounded() / 100.0))
        self.currentRate = clampedRate
        activeDeck.setRate(clampedRate)

        currentSegment?.playbackRate = clampedRate
        updateNowPlayingInfo()
    }

    public func resetRate() {
        setRate(1.0)
    }

    public func nextSegment() {
        guard let currentClass = currentClass else { return }
        if currentSegmentIndex < currentClass.segments.count - 1 {
            jumpToSegment(currentSegmentIndex + 1)
        }
    }

    public func previousSegment() {
        if currentOffsetSeconds > 3.0 {
            seek(to: 0)
        } else if currentSegmentIndex > 0 {
            jumpToSegment(currentSegmentIndex - 1)
        } else {
            seek(to: 0)
        }
    }

    public func jumpToSegment(_ index: Int) {
        guard let currentClass = currentClass, index >= 0, index < currentClass.segments.count else { return }
        cancelCrossfade()

        let targetSegment = currentClass.segments[index]
        let fadeDuration = CrossfadeCalculator.manualJumpFadeDuration(
            requestedDuration: AppSettings.shared.crossfadeDurationSeconds,
            targetSegmentDuration: Double(targetSegment.durationMs) / 1000.0,
            isAutoPauseEnabled: AppSettings.shared.isAutoPauseBetweenSegmentsEnabled,
            isPlaying: isPlaying
        )

        if fadeDuration <= 0.0 {
            clearManualTail()
            currentSegmentIndex = index
            loadSegment(on: activeDeck, segmentIndex: index)
            incomingDeck.stop()
            if isPlaying { play() }
            return
        }

        let tailStartVolume = activeDeck.playerNode.volume
        clearManualTail()
        let tailDeckIndex = activeDeckIndex
        let tailDeck = tailDeckIndex == 0 ? deckA : deckB
        activeDeckIndex = 1 - activeDeckIndex
        currentSegmentIndex = index
        loadSegment(on: activeDeck, segmentIndex: index)
        activeDeck.setVolume(0.0)

        manualTailDeckIndex = tailDeckIndex
        manualTailDuration = fadeDuration
        manualTailElapsed = 0.0
        manualTailStartVolume = tailStartVolume
        tailDeck.setVolume(tailStartVolume)

        if activeDeck.currentAudioFile != nil {
            activeDeck.playerNode.play()
        }
        updateNowPlayingInfo()
    }

    private func clearManualTail() {
        manualTailDeck?.stop()
        manualTailDeckIndex = nil
        manualTailDuration = 0.0
        manualTailElapsed = 0.0
        manualTailStartVolume = 1.0
        activeDeck.setVolume(1.0)
    }

    private func updateManualTail() {
        guard let tailDeck = manualTailDeck else { return }
        manualTailElapsed += 0.1
        let progress = manualTailDuration > 0 ? manualTailElapsed / manualTailDuration : 1.0
        if progress >= 1.0 {
            clearManualTail()
            return
        }
        let (_, fadeIn) = CrossfadeCalculator.equalPowerVolumes(progress: progress)
        tailDeck.setVolume(CrossfadeCalculator.manualTailVolume(startVolume: manualTailStartVolume, progress: progress))
        activeDeck.setVolume(fadeIn)
    }

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

    private func handleTrackBufferFinished(deckId: String, segmentIndex: Int, generation: Int) {
        guard deckId == activeDeck.id, segmentIndex == currentSegmentIndex, generation == activeDeck.scheduleGeneration else { return }
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

        if manualTailDeckIndex != nil {
            updateManualTail()
        }

        let effectiveCrossfade = CrossfadeCalculator.effectiveDuration(
            requestedDuration: AppSettings.shared.crossfadeDurationSeconds,
            segmentDuration: currentDurationSeconds,
            isAutoPauseEnabled: AppSettings.shared.isAutoPauseBetweenSegmentsEnabled
        )

        let remaining = currentDurationSeconds - currentOffsetSeconds

        guard let currentClass = currentClass else { return }
        let hasNextSegment = currentSegmentIndex < currentClass.segments.count - 1

        if !isCrossfading {
            if effectiveCrossfade > 0.0 && hasNextSegment && remaining <= effectiveCrossfade {
                if manualTailDeckIndex != nil {
                    clearManualTail()
                }
                startCrossfade(effectiveDuration: effectiveCrossfade)
            } else if remaining <= 0.0 {
                handleTrackCompletion()
                return
            }
        } else {
            updateCrossfade(effectiveDuration: effectiveCrossfade, remaining: remaining)
        }

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
                let secondsLeft = (diffMs + 500) / 1000
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
