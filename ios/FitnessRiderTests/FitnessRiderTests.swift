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

    // Layer 1 第 1 項延伸：匯入音樂後 WorkoutClass.totalDurationMs / estimatedCalories 要能從
    // segments 正確重算，不能停在匯入前的舊值（或 0）。公式必須與 Android
    // WorkoutClass.withRecalculatedTotals()（WorkoutModels.kt）算出同一個數字：
    // 用同一組輸入（118000/93000/206000ms、zone 1/3/5）在 FitnessRiderAndroidTest.kt 也驗證了
    // 417000ms 總時長、85.8 kcal，兩邊算出來要一致。
    func testRecalculateTotalsMatchesSegmentsAndAndroidFormula() {
        var workoutClass = WorkoutClass(
            title: "測試課表",
            // 故意帶入跟 segments 對不上的舊值，模擬匯入前的 stale 狀態
            totalDurationMs: 999,
            estimatedCalories: 999.0,
            segments: [
                WorkoutSegment(title: "暖身", durationMs: 118_000, intensityZone: 1),
                WorkoutSegment(title: "提速", durationMs: 93_000, intensityZone: 3),
                WorkoutSegment(title: "全力衝刺", durationMs: 206_000, intensityZone: 5)
            ]
        )

        workoutClass.recalculateTotals()

        XCTAssertEqual(workoutClass.totalDurationMs, 417_000)
        XCTAssertEqual(workoutClass.estimatedCalories, 85.8, accuracy: 0.001)

        // 沒有段落時要歸零，不能維持舊值
        var empty = WorkoutClass(totalDurationMs: 999, estimatedCalories: 999.0, segments: [])
        empty.recalculateTotals()
        XCTAssertEqual(empty.totalDurationMs, 0)
        XCTAssertEqual(empty.estimatedCalories, 0.0, accuracy: 0.001)

        // 未知 intensityZone（例如 0 或超出 1...5）要 fallback 到預設 10 kcal/min，跟 Android 的 else 分支一致
        var unknownZone = WorkoutClass(segments: [WorkoutSegment(durationMs: 60_000, intensityZone: 0)])
        unknownZone.recalculateTotals()
        XCTAssertEqual(unknownZone.totalDurationMs, 60_000)
        XCTAssertEqual(unknownZone.estimatedCalories, 10.0, accuracy: 0.001)
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

    func testM4RealtimeCalorieAccumulationAndBounds() {
        let totalClassSec = 3000 // 50 minutes
        let totalCalories = 500.0

        // At start (0s)
        var elapsedSec = 0
        var ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 0)

        // At midpoint (1500s)
        elapsedSec = 1500
        ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 250)

        // At end (3000s)
        elapsedSec = 3000
        ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 500)

        // Overtime clamp (3200s)
        elapsedSec = 3200
        ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 500)
    }

    func testM4IntensityZoneColorMapping() {
        let z1 = FitnessRiderTheme.colorForZone(1)
        let z2 = FitnessRiderTheme.colorForZone(2)
        let z3 = FitnessRiderTheme.colorForZone(3)
        let z4 = FitnessRiderTheme.colorForZone(4)
        let z5 = FitnessRiderTheme.colorForZone(5)

        XCTAssertEqual(z1, FitnessRiderTheme.zone1)
        XCTAssertEqual(z2, FitnessRiderTheme.zone2)
        XCTAssertEqual(z3, FitnessRiderTheme.zone3)
        XCTAssertEqual(z4, FitnessRiderTheme.zone4)
        XCTAssertEqual(z5, FitnessRiderTheme.zone5)
    }

    func testM4AudioSeekingBounds() {
        let duration = 180.0 // 3 minutes

        // Seek -10 from 5s -> clamped to 0.0
        var currentOffset = 5.0
        var targetOffset = min(duration, max(0.0, currentOffset - 10.0))
        XCTAssertEqual(targetOffset, 0.0, accuracy: 0.001)

        // Seek +10 from 30s -> 40s
        currentOffset = 30.0
        targetOffset = min(duration, max(0.0, currentOffset + 10.0))
        XCTAssertEqual(targetOffset, 40.0, accuracy: 0.001)

        // Seek +10 from 175s -> clamped to 180s
        currentOffset = 175.0
        targetOffset = min(duration, max(0.0, currentOffset + 10.0))
        XCTAssertEqual(targetOffset, 180.0, accuracy: 0.001)
    }

    // Layer 1 第 6 項：檔名碰撞時要加 _1、_2... 後綴，不能互相覆寫。
    func testResolveUniqueMusicFileNameAppendsSuffixOnCollision() {
        // 沒有碰撞，原樣傳回
        XCTAssertEqual(resolveUniqueMusicFileName("track.mp3", existingNames: []), "track.mp3")

        // 撞名一次 -> _1
        XCTAssertEqual(
            resolveUniqueMusicFileName("track.mp3", existingNames: ["track.mp3"]),
            "track_1.mp3"
        )

        // 撞名兩次（_1 也已存在）-> _2
        XCTAssertEqual(
            resolveUniqueMusicFileName("track.mp3", existingNames: ["track.mp3", "track_1.mp3"]),
            "track_2.mp3"
        )

        // 沒有副檔名的檔案也要能正確加後綴
        XCTAssertEqual(resolveUniqueMusicFileName("track", existingNames: ["track"]), "track_1")
    }

    func testMusicTitleFromFileNameStripsExtension() {
        XCTAssertEqual(musicTitleFromFileName("我的歌曲.mp3"), "我的歌曲")
        XCTAssertEqual(musicTitleFromFileName("no_extension"), "no_extension")
    }

    // Layer 1 第 3 項：選 N 首歌就要建立 N 個段落，標題＝曲名、長度＝曲長，依序對應不覆蓋。
    func testBuildSegmentsForImportedTracksMapsEachUriToOneSegment() {
        let classId = UUID()
        let tracks = [
            ImportedTrackInfo(fileName: "track_1.mp3", durationMs: 0, bpm: 0.0),
            ImportedTrackInfo(fileName: "曲目二.mp3", durationMs: 245_000, bpm: 132.5),
            ImportedTrackInfo(fileName: "track_1_1.mp3", durationMs: 180_000, bpm: 0.0)
        ]

        let segments = buildSegmentsForImportedTracks(tracks: tracks, classId: classId, startOrderIndex: 2)

        // N 個檔案 -> N 個段落
        XCTAssertEqual(segments.count, 3)

        // 依序對應、orderIndex 接續現有段落數量往後排
        XCTAssertEqual(segments[0].orderIndex, 2)
        XCTAssertEqual(segments[1].orderIndex, 3)
        XCTAssertEqual(segments[2].orderIndex, 4)
        segments.forEach { XCTAssertEqual($0.classId, classId) }

        // 標題＝曲名（去副檔名），長度＝曲長
        XCTAssertEqual(segments[0].title, "track_1")
        XCTAssertEqual(segments[1].title, "曲目二")
        XCTAssertEqual(segments[1].durationMs, 245_000)

        // 分析失敗（durationMs/bpm 為 0）時 fallback 回預設值，而不是寫入 0
        XCTAssertEqual(segments[0].durationMs, 300_000)
        XCTAssertEqual(segments[0].baseBpm, 128.0, accuracy: 0.001)
        XCTAssertEqual(segments[1].baseBpm, 132.5, accuracy: 0.001)
        XCTAssertEqual(segments[2].durationMs, 180_000)
        XCTAssertEqual(segments[2].baseBpm, 128.0, accuracy: 0.001)

        // 每個新段落都要有預設 cue，維持既有行為
        segments.forEach { XCTAssertFalse($0.cues.isEmpty) }
    }

    func testVersionLifecycleExpiration() {
        let baseDate = Date(timeIntervalSince1970: 1774000000) // Fixed point in time
        let manager = VersionLifecycleManager(buildDate: baseDate)
        let testDefaults = UserDefaults(suiteName: "FitnessRiderTestDefaults_\(UUID().uuidString)")!

        let oneDay: TimeInterval = 86400.0

        // 1. Same day as build -> not expired, 30 days remaining
        let day0 = baseDate
        XCTAssertFalse(manager.isExpired(currentTime: day0, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day0, defaults: testDefaults), 30)

        // 2. Day 15 -> not expired, 15 days remaining
        let day15 = baseDate.addingTimeInterval(15 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day15, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day15, defaults: testDefaults), 15)

        // 3. Advance warning range on Day 25 (5 days remaining, falls in 1...7 range)
        let day25 = baseDate.addingTimeInterval(25 * oneDay)
        let rem25 = manager.remainingDays(currentTime: day25, defaults: testDefaults)
        XCTAssertEqual(rem25, 5)
        XCTAssertTrue((1...7).contains(rem25))

        // 4. Day 29 -> not expired, 1 day remaining
        let day29 = baseDate.addingTimeInterval(29 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day29, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day29, defaults: testDefaults), 1)

        // 5. Day 30 -> expired, 0 days remaining
        let day30 = baseDate.addingTimeInterval(30 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day30, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day30, defaults: testDefaults), 0)

        // 6. Day 35 -> expired
        let day35 = baseDate.addingTimeInterval(35 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day35, defaults: testDefaults), 0)

        // 7. Anti-clock rollback and post-expiration persistence test
        let rollbackDefaults = UserDefaults(suiteName: "FitnessRiderRollback_\(UUID().uuidString)")!
        let day10 = baseDate.addingTimeInterval(10 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day10, defaults: rollbackDefaults))
        // Rollback clock by 2 days (< last recorded launch - 1 hour)
        let day8 = baseDate.addingTimeInterval(8 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day8, defaults: rollbackDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day8, defaults: rollbackDefaults), 0)

        // Expired on Day 35 in a fresh store
        let persistenceDefaults = UserDefaults(suiteName: "FitnessRiderPersistence_\(UUID().uuidString)")!
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: persistenceDefaults))
        // Clock rolled back to Day 5 after having expired -> must remain expired!
        let day5 = baseDate.addingTimeInterval(5 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day5, defaults: persistenceDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day5, defaults: persistenceDefaults), 0)

        // 8. Fixed duration (immune to DST / Calendar shifts)
        XCTAssertEqual(manager.expirationDate.timeIntervalSince(baseDate), 30.0 * 86400.0, accuracy: 0.001)

        // 9. Formatted strings and URL
        XCTAssertFalse(manager.buildDateFormatted.isEmpty)
        XCTAssertFalse(manager.expirationDateFormatted.isEmpty)
        XCTAssertTrue(VersionLifecycleManager.updateURL.absoluteString.hasPrefix("https://"))
    }
}

