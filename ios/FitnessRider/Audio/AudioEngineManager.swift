import Foundation
import AVFoundation
import MediaPlayer

@MainActor
public final class AudioEngineManager: ObservableObject {
    public static let shared = AudioEngineManager()

    // AVFoundation Engine & Nodes
    private let audioEngine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private let timePitchUnit = AVAudioUnitTimePitch()

    // State properties
    @Published public private(set) var isPlaying: Bool = false
    @Published public private(set) var currentRate: Double = 1.0 // 0.85 ~ 1.15
    @Published public private(set) var currentOffsetSeconds: Double = 0.0
    @Published public private(set) var currentDurationSeconds: Double = 0.0
    @Published public private(set) var currentSegmentIndex: Int = 0

    // Currently playing class & segment
    public private(set) var currentClass: WorkoutClass?
    public private(set) var currentSegment: WorkoutSegment?

    // Audio file info
    private var currentAudioFile: AVAudioFile?
    private var audioLengthSamples: AVAudioFramePosition = 0
    private var sampleRate: Double = 44100.0
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
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers, .allowBluetooth, .allowBluetoothA2DP])
            try session.setActive(true)
        } catch {
            print("Failed to activate AVAudioSession: \(error)")
        }
    }

    private func setupEngine() {
        audioEngine.attach(playerNode)
        audioEngine.attach(timePitchUnit)

        // Lock pitch to 0 cents (Pitch-preserving)
        timePitchUnit.pitch = 0.0
        timePitchUnit.rate = 1.0

        let mainMixer = audioEngine.mainMixerNode
        let format = mainMixer.outputFormat(forBus: 0)

        audioEngine.connect(playerNode, to: timePitchUnit, format: format)
        audioEngine.connect(timePitchUnit, to: mainMixer, format: format)

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
        loadCurrentSegment()
    }

    private func loadCurrentSegment() {
        guard let currentClass = currentClass,
              currentSegmentIndex < currentClass.segments.count else { return }

        let segment = currentClass.segments[currentSegmentIndex]
        self.currentSegment = segment
        self.currentRate = segment.playbackRate
        timePitchUnit.rate = Float(segment.playbackRate)
        timePitchUnit.pitch = 0.0 // Strictly lock pitch

        stopTimer()
        playerNode.stop()

        // Layer 3：segment.musicFileName 可能是 Music/ 目錄下的檔名，也可能是外部資料夾的
        // "extfolder://" 相對路徑，一律交給 MusicSource 判斷來源與存在性；AVAudioFile 的初始化
        // 要在 security-scoped 存取視窗裡完成，之後系統的檔案描述子仍可繼續讀取。
        let file: AVAudioFile? = MusicSource.withResolvedFileURL(for: segment.musicFileName) { url in
            try? AVAudioFile(forReading: url)
        }.flatMap { $0 }

        if let file = file {
            self.currentAudioFile = file
            self.audioLengthSamples = file.length
            self.sampleRate = file.processingFormat.sampleRate
            self.currentDurationSeconds = Double(file.length) / sampleRate
            self.currentOffsetSeconds = 0.0
            scheduleBuffer(fromSample: 0)
        } else {
            // Simulated duration from segment if file not present locally
            self.currentAudioFile = nil
            self.currentDurationSeconds = Double(segment.durationMs) / 1000.0
            self.currentOffsetSeconds = 0.0
        }

        updateNowPlayingInfo()
    }

    private func scheduleBuffer(fromSample sample: AVAudioFramePosition) {
        guard let file = currentAudioFile else { return }
        playerNode.stop()

        let remainingSamples = audioLengthSamples - sample
        guard remainingSamples > 0 else { return }

        playerNode.scheduleSegment(
            file,
            startingFrame: sample,
            frameCount: AVAudioFrameCount(remainingSamples),
            at: nil
        ) { [weak self] in
            DispatchQueue.main.async {
                self?.handleTrackCompletion()
            }
        }
    }

    public func play() {
        if !audioEngine.isRunning {
            try? audioEngine.start()
        }
        playerNode.play()
        isPlaying = true
        startTimer()
        updateNowPlayingInfo()
    }

    public func pause() {
        playerNode.pause()
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
        let clampedSeconds = max(0, min(seconds, currentDurationSeconds))
        self.currentOffsetSeconds = clampedSeconds

        if let _ = currentAudioFile {
            let targetSample = AVAudioFramePosition(clampedSeconds * sampleRate)
            let wasPlaying = isPlaying
            scheduleBuffer(fromSample: targetSample)
            if wasPlaying {
                playerNode.play()
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
        timePitchUnit.rate = Float(clampedRate)
        timePitchUnit.pitch = 0.0 // Ensure pitch is never shifted

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
            currentSegmentIndex += 1
            loadCurrentSegment()
            if isPlaying { play() }
        }
    }

    public func previousSegment() {
        if currentOffsetSeconds > 3.0 {
            seek(to: 0)
        } else if currentSegmentIndex > 0 {
            currentSegmentIndex -= 1
            loadCurrentSegment()
            if isPlaying { play() }
        } else {
            seek(to: 0)
        }
    }

    private func handleTrackCompletion() {
        guard let currentClass = currentClass else { return }

        if AppSettings.shared.isAutoPauseBetweenSegmentsEnabled {
            pause()
            if currentSegmentIndex < currentClass.segments.count - 1 {
                currentSegmentIndex += 1
                loadCurrentSegment()
            }
        } else {
            // Auto advance
            if currentSegmentIndex < currentClass.segments.count - 1 {
                currentSegmentIndex += 1
                loadCurrentSegment()
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
            self?.tick()
        }
    }

    private func stopTimer() {
        displayLinkTimer?.invalidate()
        displayLinkTimer = nil
    }

    private func tick() {
        guard isPlaying else { return }
        self.currentOffsetSeconds += 0.1 * currentRate

        // Check if track completed in simulation mode
        if currentAudioFile == nil && currentOffsetSeconds >= currentDurationSeconds {
            handleTrackCompletion()
            return
        }

        // Check next cue for countdown beeps
        checkUpcomingCueCountdown()
    }

    private func checkUpcomingCueCountdown() {
        guard AppSettings.shared.isCountdownBeepEnabled,
              let segment = currentSegment else { return }

        let currentMs = Int(currentOffsetSeconds * 1000)

        for cue in segment.cues {
            let diffMs = cue.offsetMs - currentMs
            if diffMs > 0 && diffMs <= 3100 {
                let secondsLeft = (diffMs + 500) / 1000 // 3, 2, 1
                if secondsLeft != lastBeepSecond && secondsLeft >= 1 && secondsLeft <= 3 {
                    lastBeepSecond = secondsLeft
                    CueAudioBeepPlayer.shared.playCountdownBeep(secondsLeft: secondsLeft)
                }
                break
            } else if diffMs <= 0 && diffMs >= -500 {
                if lastBeepSecond != 0 {
                    lastBeepSecond = 0
                    CueAudioBeepPlayer.shared.playActionStartBeep()
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
