import XCTest
@testable import FitnessRider

final class FitnessRiderTests: XCTestCase {

    func testClassCRUDAndPersistence() throws {
        let repo = ClassRepository.shared

        let testClass = WorkoutClass(
            id: UUID(),
            title: "單元測試爬坡課",
            author: "Tester",
            createdAt: Date(),
            totalDurationMs: 1800000,
            estimatedCalories: 300.0,
            segments: [
                WorkoutSegment(
                    id: UUID(),
                    classId: UUID(),
                    orderIndex: 0,
                    title: "測試衝刺段",
                    musicFileName: "test.mp3",
                    durationMs: 180000,
                    baseBpm: 130.0,
                    playbackRate: 1.04,
                    intensityZone: 5,
                    cues: [
                        WorkoutCue(
                            id: UUID(),
                            offsetMs: 10000,
                            posture: .sprint,
                            targetRpm: 110,
                            resistanceLevel: "LEVEL 5",
                            message: "衝刺！",
                            handPosition: .position3,
                            reminders: ["全力衝刺，跟上最快節奏！", "核心收緊，骨盆保持穩定"]
                        )
                    ]
                )
            ]
        )

        // Save
        repo.saveClass(testClass)

        // Fetch
        let fetched = repo.fetchClass(byId: testClass.id)
        XCTAssertNotNil(fetched)
        XCTAssertEqual(fetched?.title, "單元測試爬坡課")
        XCTAssertEqual(fetched?.segments.count, 1)
        XCTAssertEqual(fetched?.segments.first?.cues.count, 1)
        let cue = fetched?.segments.first?.cues.first
        XCTAssertEqual(cue?.posture, .sprint)
        XCTAssertEqual(cue?.targetRpm, 110)
        XCTAssertEqual(cue?.handPosition, .position3)
        XCTAssertEqual(cue?.reminders.count, 2)
        XCTAssertEqual(cue?.reminders.first, "全力衝刺，跟上最快節奏！")

        // Clean up
        repo.deleteClass(byId: testClass.id)
        XCTAssertNil(repo.fetchClass(byId: testClass.id))
    }

    func testSQLiteHotBackupAndRestore() throws {
        let backupService = SQLiteBackupService.shared
        guard let backupURL = backupService.exportBackupFile() else {
            XCTFail("Backup export returned nil")
            return
        }

        XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))
        XCTAssertTrue(backupURL.lastPathComponent.hasPrefix("FitnessRider_Backup_"))
        XCTAssertTrue(backupURL.pathExtension == "sqlite")

        // Test restoring
        XCTAssertNoThrow(try backupService.restoreFromFile(at: backupURL))

        // Clean up temp backup
        try? FileManager.default.removeItem(at: backupURL)
    }

    func testRiderClassPackagingAndExtraction() throws {
        let testClass = WorkoutClass(
            id: UUID(),
            title: "教練分享課表",
            author: "Master Coach",
            createdAt: Date(),
            totalDurationMs: 600000,
            estimatedCalories: 100.0,
            segments: [
                WorkoutSegment(
                    id: UUID(),
                    title: "分享段落",
                    musicFileName: "track1.mp3",
                    durationMs: 300000,
                    baseBpm: 125.0,
                    playbackRate: 1.0,
                    intensityZone: 3,
                    cues: [
                        WorkoutCue(
                            id: UUID(),
                            offsetMs: 5000,
                            posture: .standingClimb,
                            targetRpm: 60,
                            resistanceLevel: "LEVEL 7",
                            message: "站姿爬坡！",
                            handPosition: .position3,
                            reminders: ["站立姿勢來爬坡，鍛鍊股四頭肌力量", "開始爬斜坡"]
                        )
                    ]
                )
            ]
        )

        let archiveService = RiderClassArchiveService.shared
        let archiveURL = try archiveService.exportRiderClass(for: testClass)

        XCTAssertTrue(FileManager.default.fileExists(atPath: archiveURL.path))
        XCTAssertEqual(archiveURL.pathExtension, "riderclass")

        // Test unpacking
        let unpacked = try archiveService.importRiderClass(from: archiveURL)
        XCTAssertEqual(unpacked.title, testClass.title)
        XCTAssertEqual(unpacked.segments.count, testClass.segments.count)
        let unpackedCue = unpacked.segments.first?.cues.first
        XCTAssertEqual(unpackedCue?.handPosition, .position3)
        XCTAssertEqual(unpackedCue?.reminders.count, 2)
        XCTAssertEqual(unpackedCue?.reminders.first, "站立姿勢來爬坡，鍛鍊股四頭肌力量")

        // Clean up
        try? FileManager.default.removeItem(at: archiveURL)
        ClassRepository.shared.deleteClass(byId: unpacked.id)
    }

    @MainActor
    func testTempoClampingAndPercentageStepping() {
        let audio = AudioEngineManager.shared

        // Default
        audio.resetRate()
        XCTAssertEqual(audio.currentRate, 1.0)

        // +2% step
        audio.adjustRatePercent(by: 2.0)
        XCTAssertEqual(audio.currentRate, 1.02)

        // -4% step
        audio.adjustRatePercent(by: -4.0)
        XCTAssertEqual(audio.currentRate, 0.98)

        // Over high limit (+15% -> 1.15)
        audio.setRate(1.50)
        XCTAssertEqual(audio.currentRate, 1.15)

        // Under low limit (-15% -> 0.85)
        audio.setRate(0.50)
        XCTAssertEqual(audio.currentRate, 0.85)

        // Reset
        audio.resetRate()
        XCTAssertEqual(audio.currentRate, 1.0)
    }

    func testTapTempoDetectorAccuracy() {
        let detector = TapTempoDetector()

        // 1. Initial state
        XCTAssertNil(detector.calculateCurrentBpm())
        XCTAssertEqual(detector.tapCount, 0)

        // 2. Simulate 120 BPM taps (interval 0.5s)
        let startTime: TimeInterval = 1000.0
        detector.recordTap(at: startTime)
        XCTAssertNil(detector.calculateCurrentBpm()) // 1 tap is not enough

        detector.recordTap(at: startTime + 0.5)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)

        detector.recordTap(at: startTime + 1.0)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)

        detector.recordTap(at: startTime + 1.5)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)
        XCTAssertEqual(detector.tapCount, 4)

        // 3. Simulate 140 BPM taps (~0.4286s)
        detector.reset()
        XCTAssertEqual(detector.tapCount, 0)
        var t = startTime
        detector.recordTap(at: t)
        for _ in 1...4 {
            t += 0.4286
            detector.recordTap(at: t)
        }
        let calculated140 = detector.calculateCurrentBpm()
        XCTAssertNotNil(calculated140)
        XCTAssertEqual(calculated140!, 140.0, accuracy: 1.0)

        // 4. Test auto-reset on gap > 2.5s
        detector.recordTap(at: t + 3.0)
        XCTAssertEqual(detector.tapCount, 1)
        XCTAssertNil(detector.calculateCurrentBpm())

        // 5. Test static helper
        let bpm120 = TapTempoDetector.calculateBpm(from: [0.5, 0.5, 0.5])
        XCTAssertEqual(bpm120!, 120.0, accuracy: 0.1)
    }

    func testSyntheticWaveformAndDatabaseCache() {
        let analyzer = WaveformAnalyzer.shared
        let samples = analyzer.generateSyntheticWaveform(sampleCount: 800)
        XCTAssertEqual(samples.count, 800)

        for sample in samples {
            XCTAssertTrue(sample >= 0.0 && sample <= 1.0, "Sample \(sample) should be in [0.0, 1.0]")
        }

        // Test SQLite waveform cache round-trip
        let testFileName = "test_unit_track_\(UUID().uuidString).mp3"
        let repo = ClassRepository.shared
        repo.saveWaveform(for: testFileName, samples: samples, durationMs: 180000, bpm: 132.5)

        let cached = repo.fetchWaveform(for: testFileName)
        XCTAssertNotNil(cached)
        XCTAssertEqual(cached?.samples.count, 800)
        guard let cachedSamples = cached?.samples, let firstCached = cachedSamples.first, let firstSample = samples.first else {
            XCTFail("Samples should not be empty")
            return
        }
        XCTAssertEqual(firstCached, firstSample, accuracy: 0.0001)
    }

    func testBpmEstimationFromWaveformEnvelope() {
        let analyzer = WaveformAnalyzer.shared
        let durationMs = 100_000
        let sampleCount = 800
        let beatsPerSec = 128.0 / 60.0
        let totalBeats = Int(beatsPerSec * (Double(durationMs) / 1000.0))

        var envelope = [Float](repeating: 0.2, count: sampleCount)
        for b in 0..<totalBeats {
            let beatTimeSec = Double(b) / beatsPerSec
            let sampleIdx = Int((beatTimeSec / (Double(durationMs) / 1000.0)) * Double(sampleCount))
            if sampleIdx < envelope.count {
                envelope[sampleIdx] = 0.9
            }
        }

        let estimatedBpm = analyzer.estimateBpm(from: envelope, durationMs: durationMs)
        XCTAssertEqual(estimatedBpm, 128.0, accuracy: 2.0)
    }
}
