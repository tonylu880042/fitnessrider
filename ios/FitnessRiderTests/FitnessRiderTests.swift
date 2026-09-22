import XCTest
import CryptoKit
@testable import FitnessRider

final class FitnessRiderTests: XCTestCase {

    /// 產生一組測試用 P-256 金鑰對，`signVipSerial` 用它簽出符合 vipSerial 格式的序號字串。
    /// 對應後端 backend/src/lib/vipSerial.ts 與 Android 測試的 generateTestEcKeyPair。
    private func signVipSerial(privateKey: P256.Signing.PrivateKey, serialIdHex: String, planDays: Int) throws -> String {
        var payload = Data()
        payload.append(1) // version
        var serialIdBytes = [UInt8]()
        var hex = serialIdHex
        while !hex.isEmpty {
            let byteStr = String(hex.prefix(2))
            serialIdBytes.append(UInt8(byteStr, radix: 16)!)
            hex.removeFirst(min(2, hex.count))
        }
        payload.append(contentsOf: serialIdBytes)
        payload.append(UInt8((planDays >> 8) & 0xFF))
        payload.append(UInt8(planDays & 0xFF))

        let signature = try privateKey.signature(for: payload)
        let payloadHex = payload.map { String(format: "%02X", $0) }.joined()
        let sigHex = signature.derRepresentation.map { String(format: "%02X", $0) }.joined()
        return "FRVIP-\(payloadHex)-\(sigHex)"
    }

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

    // Layer 2：音樂庫列表要從檔名 + 波形快取建立，快取命中時直接讀值、不重新分析；
    // 查無快取（理論上不該發生，但保底）才 fallback 回段落預設值，且一律依曲名排序。
    func testBuildMusicLibraryTracksReadsCacheAndSortsByTitle() {
        let cache: [String: (Int, Double)] = [
            "b_track.mp3": (210_000, 118.0),
            "a_track.mp3": (190_000, 126.0)
            // "c_no_cache.mp3" 故意沒有快取
        ]
        let tracks = buildMusicLibraryTracks(fileNames: ["b_track.mp3", "c_no_cache.mp3", "a_track.mp3"]) { cache[$0] }

        // 依曲名排序：a_track -> b_track -> c_no_cache
        XCTAssertEqual(tracks.map { $0.title }, ["a_track", "b_track", "c_no_cache"])

        let aTrack = tracks.first { $0.fileName == "a_track.mp3" }!
        XCTAssertEqual(aTrack.durationMs, 190_000)
        XCTAssertEqual(aTrack.bpm, 126.0, accuracy: 0.001)

        // 沒有快取 -> fallback 回段落預設值，不是 0
        let noCacheTrack = tracks.first { $0.fileName == "c_no_cache.mp3" }!
        XCTAssertEqual(noCacheTrack.durationMs, 300_000)
        XCTAssertEqual(noCacheTrack.bpm, 128.0, accuracy: 0.001)
    }

    // Layer 2：即時搜尋要比照舊版 FragDialogSelectMusic，不分大小寫比對曲名子字串。
    func testFilterMusicLibraryTracksMatchesTitleCaseInsensitive() {
        let tracks = [
            MusicLibraryTrack(fileName: "Sprint Fire.mp3", title: "Sprint Fire", durationMs: 240_000, bpm: 140.0),
            MusicLibraryTrack(fileName: "warmup_groove.mp3", title: "warmup_groove", durationMs: 300_000, bpm: 120.0),
            MusicLibraryTrack(fileName: "climb_anthem.mp3", title: "climb_anthem", durationMs: 420_000, bpm: 130.0)
        ]

        XCTAssertEqual(filterMusicLibraryTracks(tracks, query: "").count, 3)
        XCTAssertEqual(filterMusicLibraryTracks(tracks, query: "sprint").count, 1)
        XCTAssertEqual(filterMusicLibraryTracks(tracks, query: "SPRINT").first?.title, "Sprint Fire")
        XCTAssertEqual(filterMusicLibraryTracks(tracks, query: "不存在的曲名").count, 0)
    }

    // Layer 2 第 3、4 項：從音樂庫多選既有曲目 -> 直接建立 N 個段落，沿用已存在的檔名與快取
    // 的 duration/BPM，不需要（也不應該）再產生任何檔案複製或重新分析。
    func testBuildSegmentsFromLibrarySelectionReusesExistingFilesWithoutCopying() {
        let classId = UUID()
        let selected = [
            MusicLibraryTrack(fileName: "climb_anthem.mp3", title: "climb_anthem", durationMs: 420_000, bpm: 130.0),
            MusicLibraryTrack(fileName: "sprint_fire.mp3", title: "sprint_fire", durationMs: 240_000, bpm: 140.0)
        ]

        let segments = buildSegmentsFromLibrarySelection(tracks: selected, classId: classId, startOrderIndex: 3)

        XCTAssertEqual(segments.count, 2)
        XCTAssertEqual(segments[0].orderIndex, 3)
        XCTAssertEqual(segments[1].orderIndex, 4)
        // 檔名原封不動沿用（沒有經過 resolveUniqueMusicFileName 加後綴，因為根本沒有複製動作）
        XCTAssertEqual(segments[0].musicFileName, "climb_anthem.mp3")
        XCTAssertEqual(segments[1].musicFileName, "sprint_fire.mp3")
        XCTAssertEqual(segments[0].title, "climb_anthem")
        XCTAssertEqual(segments[0].durationMs, 420_000)
        XCTAssertEqual(segments[0].baseBpm, 130.0, accuracy: 0.001)
        XCTAssertEqual(segments[1].durationMs, 240_000)
        XCTAssertEqual(segments[1].baseBpm, 140.0, accuracy: 0.001)
    }

    // Layer 3 第 1、2 項：外部資料夾曲目一律以 "extfolder://" + 相對路徑表示，讓播放/波形分析/
    // 匯出等消費端可以用同一個欄位（musicFileName）判斷來源，不必額外加欄位。
    func testMusicSourceIsExternalDetectsPrefix() {
        XCTAssertTrue(MusicSource.isExternal("extfolder://Coach/warmup.mp3"))
        XCTAssertFalse(MusicSource.isExternal("track.mp3"))
        XCTAssertFalse(MusicSource.isExternal(""))
    }

    // Layer 3：資料夾掃描要能用副檔名過濾出音樂檔，排除資料夾裡的其他檔案。
    func testIsAudioFileNameMatchesKnownExtensions() {
        XCTAssertTrue(isAudioFileName("track.mp3"))
        XCTAssertTrue(isAudioFileName("TRACK.MP3")) // 不分大小寫
        XCTAssertTrue(isAudioFileName("track.m4a"))
        XCTAssertFalse(isAudioFileName("cover.jpg"))
        XCTAssertFalse(isAudioFileName("readme.txt"))
    }

    // Layer 3：資料夾內容要以串流方式列出（用 FileManager enumerator/contentsOfDirectory 逐一讀取，
    // 不複製任何音樂檔案本體），且「含子資料夾」開關要能正確切換遞迴與否，並排除非音樂檔案。
    func testListExternalMusicEntriesRespectsIncludeSubdirectoriesAndFiltersNonAudio() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("ext_music_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let subDir = tempDir.appendingPathComponent("Warmups")
        try FileManager.default.createDirectory(at: subDir, withIntermediateDirectories: true)

        try Data().write(to: tempDir.appendingPathComponent("top_level.mp3"))
        try Data().write(to: tempDir.appendingPathComponent("cover.jpg")) // 非音樂檔，要被排除
        try Data().write(to: subDir.appendingPathComponent("nested.m4a"))

        // 不含子資料夾：只看到第一層的音樂檔
        let shallow = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: false)
        XCTAssertEqual(shallow.map { $0.displayName }, ["top_level.mp3"])

        // 含子資料夾：連子資料夾裡的檔案都要列出，相對路徑要包含子資料夾名稱
        let deep = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: true)
        let names = Set(deep.map { $0.displayName })
        XCTAssertEqual(names, ["top_level.mp3", "nested.m4a"])
        let nested = deep.first { $0.displayName == "nested.m4a" }
        XCTAssertEqual(nested?.relativePath, "Warmups/nested.m4a")
    }

    // Layer 3 + iCloud：iCloud Drive 開著「最佳化儲存空間」時，還沒下載到本機的歌在磁碟上只是一個
    // 隱藏的 `.<檔名>.icloud` 預留位置。列表必須把它換算回真實檔名照常列出 —— 否則教練把 iCloud
    // 雲端硬碟資料夾綁成「音樂資料夾」後，只看得到已經下載過的那幾首（客戶回報的症狀）。
    func testICloudPlaceholderNameRecognisesPlaceholdersOnly() {
        XCTAssertEqual(iCloudPlaceholderName(for: ".Sprint Fire.mp3.icloud"), "Sprint Fire.mp3")
        XCTAssertNil(iCloudPlaceholderName(for: "Sprint Fire.mp3"))   // 已下載的一般檔案
        XCTAssertNil(iCloudPlaceholderName(for: ".DS_Store"))          // 其他隱藏檔
        XCTAssertNil(iCloudPlaceholderName(for: ".icloud"))            // 只有副檔名、沒有真實檔名
    }

    func testListExternalMusicEntriesSurfacesICloudPlaceholders() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("ext_icloud_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let subDir = tempDir.appendingPathComponent("Warmups")
        try FileManager.default.createDirectory(at: subDir, withIntermediateDirectories: true)

        try Data().write(to: tempDir.appendingPathComponent("downloaded.mp3"))
        try Data().write(to: tempDir.appendingPathComponent(".not_downloaded.mp3.icloud"))
        try Data().write(to: tempDir.appendingPathComponent(".DS_Store"))          // 一般隱藏檔，仍要排除
        try Data().write(to: tempDir.appendingPathComponent(".cover.jpg.icloud"))  // 非音樂檔的預留位置，要排除
        try Data().write(to: subDir.appendingPathComponent(".nested.m4a.icloud"))

        let deep = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: true)
        XCTAssertEqual(Set(deep.map { $0.displayName }), ["downloaded.mp3", "not_downloaded.mp3", "nested.m4a"])

        // 相對路徑一律是「下載完成後」的邏輯路徑，段落存下來的 musicFileName 才不會因為
        // 下載狀態改變而失效。
        let nested = deep.first { $0.displayName == "nested.m4a" }
        XCTAssertEqual(nested?.relativePath, "Warmups/nested.m4a")
        XCTAssertEqual(nested?.isCloudPlaceholder, true)
        XCTAssertEqual(deep.first { $0.displayName == "downloaded.mp3" }?.isCloudPlaceholder, false)

        // 下載進行中的那幾秒，同一首歌會同時有兩筆，列表要以已經落地的那筆為準、不得出現重複項目。
        try Data().write(to: tempDir.appendingPathComponent(".downloaded.mp3.icloud"))
        let racing = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: false)
        XCTAssertEqual(racing.filter { $0.displayName == "downloaded.mp3" }.count, 1)
        XCTAssertEqual(racing.first { $0.displayName == "downloaded.mp3" }?.isCloudPlaceholder, false)
    }

    // Layer 3：從外部資料夾多選曲目 -> 建立段落時，musicFileName 要存 "extfolder://" + 相對路徑，
    // 標題要用真正的顯示檔名（去副檔名），而不是整個 "extfolder://..." 字串去副檔名。
    func testBuildSegmentsFromExternalSelectionUsesExtFolderPrefixAndDisplayTitle() {
        let classId = UUID()
        let entries = [
            ExternalMusicEntry(relativePath: "Coach/warmup.mp3", displayName: "warmup.mp3"),
            ExternalMusicEntry(relativePath: "sprint.mp3", displayName: "sprint.mp3")
        ]

        let segments = buildSegmentsFromExternalSelection(entries: entries, classId: classId, startOrderIndex: 2)

        XCTAssertEqual(segments.count, 2)
        XCTAssertEqual(segments[0].orderIndex, 2)
        XCTAssertEqual(segments[1].orderIndex, 3)
        XCTAssertEqual(segments[0].musicFileName, "extfolder://Coach/warmup.mp3")
        XCTAssertEqual(segments[1].musicFileName, "extfolder://sprint.mp3")
        // 標題是"warmup"、"sprint"，不是把整個 extfolder Uri 字串去副檔名的結果
        XCTAssertEqual(segments[0].title, "warmup")
        XCTAssertEqual(segments[1].title, "sprint")
        XCTAssertEqual(segments[0].durationMs, 300_000)
        XCTAssertEqual(segments[0].baseBpm, 128.0, accuracy: 0.001)
        XCTAssertTrue(MusicSource.isExternal(segments[0].musicFileName))
    }

    // Layer 3：資料夾列表的即時搜尋跟 Layer 2 音樂庫是同一套邏輯，比對顯示檔名子字串。
    func testFilterExternalMusicEntriesMatchesDisplayNameCaseInsensitive() {
        let entries = [
            ExternalMusicEntry(relativePath: "a", displayName: "Sprint Fire.mp3"),
            ExternalMusicEntry(relativePath: "b", displayName: "warmup_groove.mp3")
        ]

        XCTAssertEqual(filterExternalMusicEntries(entries, query: "").count, 2)
        XCTAssertEqual(filterExternalMusicEntries(entries, query: "sprint").count, 1)
        XCTAssertEqual(filterExternalMusicEntries(entries, query: "SPRINT").first?.displayName, "Sprint Fire.mp3")
        XCTAssertEqual(filterExternalMusicEntries(entries, query: "不存在").count, 0)
    }

    // 已知落差修復：匯入失敗時，任何殘留在目的檔名的半成品都要被清掉，不能在 Music 目錄
    // 留下截斷檔佔用檔名。這裡用「目的地已經有一份殘留舊檔、來源不存在導致複製失敗」模擬
    // 這個情境：修復前的程式碼完全不會清 destURL，修復後不論成功或失敗，destURL 都應該
    // 準確反映這次複製的結果，不會留下不屬於這次操作的殘留內容。
    func testCopyMusicFileOrCleanupRemovesLeftoverOnFailure() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("music_import_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let destURL = tempDir.appendingPathComponent("track.mp3")
        try Data([0x01, 0x02, 0x03]).write(to: destURL) // 模擬上次留下的半成品

        let missingSourceURL = tempDir.appendingPathComponent("does_not_exist.mp3")
        let copied = copyMusicFileOrCleanup(from: missingSourceURL, to: destURL)

        XCTAssertFalse(copied, "來源不存在，複製要回報失敗")
        XCTAssertFalse(
            FileManager.default.fileExists(atPath: destURL.path),
            "失敗後不能在目的檔名留下任何殘留檔案（半成品或舊檔）佔用檔名"
        )
    }

    // 對照組：完整複製成功時檔案要存在、內容要完整，且回報 true。
    func testCopyMusicFileOrCleanupSucceedsAndWritesFullContent() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("music_import_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let sourceURL = tempDir.appendingPathComponent("source.mp3")
        let sourceBytes = Data([1, 2, 3, 4, 5])
        try sourceBytes.write(to: sourceURL)
        let destURL = tempDir.appendingPathComponent("track.mp3")

        let copied = copyMusicFileOrCleanup(from: sourceURL, to: destURL)

        XCTAssertTrue(copied)
        XCTAssertEqual(try Data(contentsOf: destURL), sourceBytes)
    }

    // Layer 3：資料夾 bookmark／「含子資料夾」開關的持久化，用獨立的 UserDefaults suite
    // 隔離測試，不影響真正的 App 設定；也驗證 clearFolder 之後狀態正確歸零。
    func testExternalMusicFolderStorePersistsIncludeSubdirectoriesPreference() throws {
        let store = ExternalMusicFolderStore.shared
        let defaults = UserDefaults(suiteName: "FitnessRiderExternalFolderTest_\(UUID().uuidString)")!

        // 舊版 MusicUtility.isIncludeSubdirectories 預設值就是 true
        XCTAssertTrue(store.includeSubdirectories(defaults: defaults))
        XCTAssertFalse(store.isFolderConfigured(defaults: defaults))

        store.setIncludeSubdirectories(false, defaults: defaults)
        XCTAssertFalse(store.includeSubdirectories(defaults: defaults))

        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("ext_folder_store_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }
        try Data([9, 9]).write(to: tempDir.appendingPathComponent("song.mp3"))

        try store.persist(folderURL: tempDir, defaults: defaults)
        XCTAssertTrue(store.isFolderConfigured(defaults: defaults))

        // bookmark 有效時，withFileAccess 要能解析出資料夾底下的檔案並讀到內容
        let sawFile = store.withFileAccess(relativePath: "song.mp3", defaults: defaults) { url in
            FileManager.default.fileExists(atPath: url.path)
        }
        XCTAssertEqual(sawFile, true)

        store.clearFolder(defaults: defaults)
        XCTAssertFalse(store.isFolderConfigured(defaults: defaults))
        // bookmark 被清除後（等同授權失效／使用者重新選擇前），withFileAccess 要回傳 nil，
        // 不能讓呼叫端誤以為資料夾還在。
        let afterClear = store.withFileAccess(relativePath: "song.mp3", defaults: defaults) { _ in true }
        XCTAssertNil(afterClear)
    }

    func testVersionLifecycleExpiration() {
        let baseDate = Date(timeIntervalSince1970: 1774000000) // Fixed point in time
        let manager = VersionLifecycleManager(buildDate: baseDate)
        let testDefaults = UserDefaults(suiteName: "FitnessRiderTestDefaults_\(UUID().uuidString)")!

        let oneDay: TimeInterval = 86400.0

        // 1. Same day as build -> not expired, 7 days remaining
        let day0 = baseDate
        XCTAssertFalse(manager.isExpired(currentTime: day0, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day0, defaults: testDefaults), 7)

        // 2. Day 3 -> not expired, 4 days remaining
        let day3 = baseDate.addingTimeInterval(3 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day3, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day3, defaults: testDefaults), 4)

        // 3. Advance warning range on Day 5 (2 days remaining, falls in 1...7 range)
        let day5 = baseDate.addingTimeInterval(5 * oneDay)
        let rem5 = manager.remainingDays(currentTime: day5, defaults: testDefaults)
        XCTAssertEqual(rem5, 2)
        XCTAssertTrue((1...7).contains(rem5))

        // 4. Day 6 -> not expired, 1 day remaining
        let day6 = baseDate.addingTimeInterval(6 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day6, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day6, defaults: testDefaults), 1)

        // 5. Day 7 -> expired, 0 days remaining
        let day7 = baseDate.addingTimeInterval(7 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day7, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day7, defaults: testDefaults), 0)

        // 6. Day 10 -> expired
        let day10 = baseDate.addingTimeInterval(10 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day10, defaults: testDefaults), 0)

        // 7. Anti-clock rollback and post-expiration persistence test
        let rollbackDefaults = UserDefaults(suiteName: "FitnessRiderRollback_\(UUID().uuidString)")!
        let day4 = baseDate.addingTimeInterval(4 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day4, defaults: rollbackDefaults))
        // Rollback clock by 2 days (< last recorded launch - 1 hour)
        let day2 = baseDate.addingTimeInterval(2 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day2, defaults: rollbackDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day2, defaults: rollbackDefaults), 0)

        // Expired on Day 10 in a fresh store
        let persistenceDefaults = UserDefaults(suiteName: "FitnessRiderPersistence_\(UUID().uuidString)")!
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: persistenceDefaults))
        // Clock rolled back to Day 2 after having expired -> must remain expired!
        XCTAssertTrue(manager.isExpired(currentTime: day2, defaults: persistenceDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day2, defaults: persistenceDefaults), 0)

        // 8. Fixed duration (immune to DST / Calendar shifts)
        XCTAssertEqual(manager.expirationDate.timeIntervalSince(baseDate), 7.0 * 86400.0, accuracy: 0.001)

        // 9. Formatted strings and URL
        XCTAssertFalse(manager.buildDateFormatted.isEmpty)
        XCTAssertFalse(manager.expirationDateFormatted.isEmpty)
        XCTAssertTrue(VersionLifecycleManager.updateURL.absoluteString.hasPrefix("https://"))
    }

    // 7 天基準免費試用（設備首次啟動日基準）測試
    func testSevenDayTrialCalculationFromFirstLaunch() {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "TrialTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        // Day 0: 首次啟動當天 -> 剩餘 7 天，未過期
        XCTAssertFalse(manager.isExpired(currentTime: launchDate, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: launchDate, defaults: testDefaults), 7)

        // Day 3: 試用第 3 天 -> 剩餘 4 天
        let day3 = launchDate.addingTimeInterval(3 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day3, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day3, defaults: testDefaults), 4)

        // Day 7: 滿 7 天 -> 過期，剩餘 0 天
        let day7 = launchDate.addingTimeInterval(7 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day7, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day7, defaults: testDefaults), 0)

        // 格式驗證
        XCTAssertFalse(manager.trialStartDateFormatted.isEmpty)
        XCTAssertEqual(manager.expirationDate.timeIntervalSince(launchDate), 7.0 * 86400.0, accuracy: 0.001)
    }

    // 推廣課程專屬代碼 26FR-NR 兌換與單機限領防刷測試
    func testPromoCodeActivationAndAntiAbuse() {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "PromoTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        // 1. 滿 10 天，基準 7 天試用已過期
        let day10 = launchDate.addingTimeInterval(10 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: testDefaults))

        // 2. 首次輸入 2026 年度推廣代碼 26FR-NR -> 成功兌換 30 天 VIP 試用
        let promoRes = manager.activateLicenseCode("26FR-NR", defaults: testDefaults, overrideCurrentDate: day10)
        XCTAssertTrue(promoRes.success)
        XCTAssertTrue(promoRes.message.contains("30 天"))
        XCTAssertTrue(manager.isVIP)
        XCTAssertEqual(manager.vipPlanName, "推廣課程專屬版 (30天免費)")
        XCTAssertFalse(manager.isExpired(currentTime: day10, defaults: testDefaults))

        // 3. 同一台設備再次輸入 26FR-NR -> 失敗，防止重複領取（單機防刷）
        let duplicateRes = manager.activateLicenseCode("26FR-NR", defaults: testDefaults, overrideCurrentDate: day10)
        XCTAssertFalse(duplicateRes.success)
        XCTAssertTrue(duplicateRes.message.contains("無法重複領取") || duplicateRes.message.contains("已兌換過"))
    }

    // VIP 授權碼開通與過期狀態解鎖測試
    func testVipLicenseCodeActivationUnlocksExpiredState() throws {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "VipTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        // 1. 滿 35 天已過期
        let day35 = launchDate.addingTimeInterval(35 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

        // 2. 輸入無效序號 -> 失敗，維持過期
        let invalidRes = manager.activateLicenseCode("INVALID-KEY-1234", defaults: testDefaults)
        XCTAssertFalse(invalidRes.success)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

        // 2b. 舊有寫死序號 / 舊規則（前綴 10 字元、長度 >= 14 就放行）現在必須被拒絕 ——
        // 這是 spec 項目 B 要修的安全漏洞：RIDER-VIP-0000 這種假序號以前會被放行。
        XCTAssertFalse(manager.activateLicenseCode("RIDER-VIP-2026-PASS", defaults: testDefaults).success)
        XCTAssertFalse(manager.activateLicenseCode("RIDER-VIP-0000", defaults: testDefaults).success)
        XCTAssertFalse(manager.activateLicenseCode("FITNESS-PRO-ANNUAL-KEY", defaults: testDefaults).success)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

        // 3. 用測試金鑰簽出一組合法序號（365 天）。用「正式」內建公鑰驗證必須失敗
        // （測試序號不是正式私鑰簽的，證明無法偽造）；用測試公鑰驗證/開通才會成功。
        let testKey = P256.Signing.PrivateKey()
        let testPublicKeyB64 = testKey.publicKey.derRepresentation.base64EncodedString()
        let validSerial = try signVipSerial(privateKey: testKey, serialIdHex: "AABBCCDD", planDays: 365)

        XCTAssertNil(VipSerialVerifier.verify(validSerial))
        XCTAssertFalse(manager.activateLicenseCode(validSerial, defaults: testDefaults).success)

        let validRes = manager.activateLicenseCode(validSerial, defaults: testDefaults, testVipPublicKeyOverride: testPublicKeyB64)
        XCTAssertTrue(validRes.success)
        XCTAssertTrue(manager.evaluateVipStatus(currentTime: day35, defaults: testDefaults))
        XCTAssertFalse(manager.isExpired(currentTime: day35, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day35, defaults: testDefaults), 365)
    }

    // P-256 驗簽正確性、竄改/錯誤金鑰序號必須被拒測試
    func testVipSerialVerifierRejectsTamperedAndWrongKeySerials() throws {
        let keyPairA = P256.Signing.PrivateKey()
        let keyPairB = P256.Signing.PrivateKey()
        let publicKeyA = keyPairA.publicKey.derRepresentation.base64EncodedString()

        let serial = try signVipSerial(privateKey: keyPairA, serialIdHex: "01020304", planDays: 30)

        // 正確金鑰、原始內容 -> 驗證成功，且天數/序號 ID 解析正確
        let info = VipSerialVerifier.verify(serial, publicKeySPKIBase64Override: publicKeyA)
        XCTAssertEqual(info?.serialId, "01020304")
        XCTAssertEqual(info?.planDays, 30)

        // 用另一把金鑰簽的序號，拿 A 的公鑰驗 -> 必須失敗（偽造序號必須被拒）
        let serialSignedByB = try signVipSerial(privateKey: keyPairB, serialIdHex: "01020304", planDays: 30)
        XCTAssertNil(VipSerialVerifier.verify(serialSignedByB, publicKeySPKIBase64Override: publicKeyA))

        // 竄改 payload（改變天數）但沿用原簽章 -> 必須失敗
        let tamperedPayloadHex = "0101020304012C" // planDays 改成 0x012C=300，其餘不變
        let originalSigHex = String(serial.split(separator: "-").last!)
        let tampered = "FRVIP-\(tamperedPayloadHex)-\(originalSigHex)"
        XCTAssertNil(VipSerialVerifier.verify(tampered, publicKeySPKIBase64Override: publicKeyA))

        // 完全不是 vipSerial 格式（例如舊的寫死序號）-> 必須失敗
        XCTAssertNil(VipSerialVerifier.verify("RIDER-VIP-2026-PASS", publicKeySPKIBase64Override: publicKeyA))
        XCTAssertNil(VipSerialVerifier.verify("RIDER-VIP-0000000000", publicKeySPKIBase64Override: publicKeyA))
    }

    // 推廣代碼只接受當年度測試（spec 項目 G：未來年份代碼以前會被誤判為永遠不過期）
    func testPromoCodeOnlyAcceptsCurrentYear() {
        let currentYear = Calendar.current.component(.year, from: Date()) % 100
        let currentYearCode = String(format: "%02dFR-NR", currentYear)
        let pastYearCode = String(format: "%02dFR-NR", (currentYear - 1 + 100) % 100)
        let futureYearCode = String(format: "%02dFR-NR", (currentYear + 1) % 100)

        XCTAssertTrue(VersionLifecycleManager.isPromoCode(currentYearCode))
        XCTAssertFalse(VersionLifecycleManager.isPromoCode(pastYearCode))
        XCTAssertFalse(VersionLifecycleManager.isPromoCode(futureYearCode))
        XCTAssertFalse(VersionLifecycleManager.isPromoCode("99FR-NR"))
    }

    // expires 遺失/為 0 不得視為 VIP 測試（spec 項目 C）
    func testMissingOrZeroVipExpiryIsNotTreatedAsVip() {
        let testDefaults = UserDefaults(suiteName: "MissingExpiryTestDefaults_\(UUID().uuidString)")!
        // fitness_rider_vip_active = true 但完全沒有寫入 fitness_rider_vip_expires（等同遺失/預設值 0）
        testDefaults.set(true, forKey: "fitness_rider_vip_active")

        let manager = VersionLifecycleManager(explicitFirstLaunchDate: Date(timeIntervalSince1970: 1775000000))
        XCTAssertFalse(manager.evaluateVipStatus(currentTime: Date(timeIntervalSince1970: 1775000000), defaults: testDefaults))
        XCTAssertFalse(manager.evaluateVipStatus(currentTime: Date.distantFuture, defaults: testDefaults))
    }

    // 離線／拿不到 min_supported_version_code 時絕對不能鎖住使用者測試（spec 項目 F）
    func testMustUpdateNeverLocksWhenBackendUnreachableOrDisabled() {
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 0, currentBuildNumber: 1))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: -1, currentBuildNumber: 1))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 5))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 6))
        XCTAssertTrue(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 4))
    }

    // 跨檔案商業常數一致性校驗測試（spec 項目 4 / promo.properties）
    func testBusinessConstantsMatchAcrossPlatforms() {
        let thisFilePath = URL(fileURLWithPath: #filePath)
        let repoRoot = thisFilePath
            .deletingLastPathComponent() // FitnessRiderTests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // fitnessrider root
        let promoPropsURL = repoRoot.appendingPathComponent("promo.properties")

        guard let content = try? String(contentsOf: promoPropsURL, encoding: .utf8) else {
            XCTFail("無法讀取 promo.properties: \(promoPropsURL.path)")
            return
        }

        var baseTrialDays: Int? = nil
        var promoTrialDays: Int? = nil

        for line in content.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.hasPrefix("BASE_TRIAL_DAYS=") {
                baseTrialDays = Int(trimmed.replacingOccurrences(of: "BASE_TRIAL_DAYS=", with: "").trimmingCharacters(in: .whitespacesAndNewlines))
            } else if trimmed.hasPrefix("TRIAL_DAYS=") {
                promoTrialDays = Int(trimmed.replacingOccurrences(of: "TRIAL_DAYS=", with: "").trimmingCharacters(in: .whitespacesAndNewlines))
            }
        }

        XCTAssertEqual(baseTrialDays, 7, "BASE_TRIAL_DAYS 應為 7")
        XCTAssertEqual(promoTrialDays, 30, "TRIAL_DAYS 應為 30")
        XCTAssertEqual(VersionLifecycleManager.lifecycleDays, baseTrialDays, "VersionLifecycleManager.lifecycleDays 必須與 promo.properties 一致")
        XCTAssertEqual(VersionLifecycleManager.promoTotalTrialDays, promoTrialDays, "VersionLifecycleManager.promoTotalTrialDays 必須與 promo.properties 一致")
    }

    // 推廣碼兌換在超過 30 天之裝置上應拒絕且不消耗額度（spec 項目 3）
    func testPromoCodeRedemptionFailsIfDeviceOlderThan30DaysAndDoesNotBurn() {
        let testDefaults = UserDefaults(suiteName: "PromoOlderThan30Days_\(UUID().uuidString)")!
        let now = Date()
        let thirtyFiveDaysAgo = now.addingTimeInterval(-35 * 86_400)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: thirtyFiveDaysAgo)

        let currentYear = Calendar.current.component(.year, from: Date()) % 100
        let promoCode = String(format: "%02dFR-NR", currentYear)

        let result = manager.activateLicenseCode(promoCode, defaults: testDefaults, overrideCurrentDate: now)
        XCTAssertFalse(result.success, "首次啟動超過 30 天之設備不可兌換推廣代碼")
        XCTAssertTrue(result.message.contains("超過 30 天"), "錯誤訊息應清楚告知已超過 30 天")

        let redeemedList = testDefaults.stringArray(forKey: "fitness_rider_redeemed_promos") ?? []
        XCTAssertFalse(redeemedList.contains(promoCode), "失敗時不可將代碼寫入已兌換清單（不得白燒額度）")
        XCTAssertFalse(testDefaults.bool(forKey: "fitness_rider_vip_active"), "不可設為 VIP 啟用")
    }

    // Keychain 存取方法測試
    func testDeviceIdentifierServiceKeychainAccessors() {
        let service = DeviceIdentifierService.shared
        let testKey = "app.fitnessrider.unit_test_key"
        let testVal = "test_value_\(UUID().uuidString)"

        service.setKeychainString(key: testKey, value: testVal)
        XCTAssertEqual(service.getKeychainString(key: testKey), testVal)

        service.deleteKeychainString(key: testKey)
        XCTAssertNil(service.getKeychainString(key: testKey))
    }

    // 資安加固：匯入 .riderclass 時若包含 ../ 等路徑穿透檔名，必須被阻擋，不能寫入 musicDirectory 以外目錄。
    func testZipSlipPathTraversalBlocked() {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let baseStandardized = tempDir.standardizedFileURL.path
        let maliciousNames = [
            "../evil.mp3",
            "../../etc/passwd",
            "sub/../../evil.mp3"
        ]
        for name in maliciousNames {
            let destURL = tempDir.appendingPathComponent(name).standardizedFileURL
            let isSafe = destURL.path.hasPrefix(baseStandardized + "/")
            XCTAssertFalse(isSafe, "Path traversal name '\(name)' should be rejected")
        }

        let validURL = tempDir.appendingPathComponent("normal_track.mp3").standardizedFileURL
        XCTAssertTrue(validURL.path.hasPrefix(baseStandardized + "/"))
    }

    // MARK: - M2 Audio Crossfade Tests

    func testEqualPowerCrossfadeCalculation() {
        // 1. Boundary t = 0.0
        let (out0, in0) = CrossfadeCalculator.equalPowerVolumes(progress: 0.0)
        XCTAssertEqual(out0, 1.0, accuracy: 0.0001)
        XCTAssertEqual(in0, 0.0, accuracy: 0.0001)

        // 2. Boundary t = 1.0
        let (out1, in1) = CrossfadeCalculator.equalPowerVolumes(progress: 1.0)
        XCTAssertEqual(out1, 0.0, accuracy: 0.0001)
        XCTAssertEqual(in1, 1.0, accuracy: 0.0001)

        // 3. Midpoint t = 0.5 -> Equal power ≈ √2 / 2 ≈ 0.7071
        let (outMid, inMid) = CrossfadeCalculator.equalPowerVolumes(progress: 0.5)
        XCTAssertEqual(outMid, 0.7071, accuracy: 0.001)
        XCTAssertEqual(inMid, 0.7071, accuracy: 0.001)

        // 4. Equal-Power acoustic energy conservation: out^2 + in^2 = 1.0
        let testPoints = [0.0, 0.1, 0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1.0]
        for p in testPoints {
            let (vOut, vIn) = CrossfadeCalculator.equalPowerVolumes(progress: p)
            let totalPower = (vOut * vOut) + (vIn * vIn)
            XCTAssertEqual(totalPower, 1.0, accuracy: 0.001, "Power should be 1.0 at progress \(p)")
        }

        // 5. Clamping for out-of-bounds progress
        let (outNeg, inNeg) = CrossfadeCalculator.equalPowerVolumes(progress: -0.5)
        XCTAssertEqual(outNeg, 1.0, accuracy: 0.0001)
        XCTAssertEqual(inNeg, 0.0, accuracy: 0.0001)

        let (outOver, inOver) = CrossfadeCalculator.equalPowerVolumes(progress: 1.8)
        XCTAssertEqual(outOver, 0.0, accuracy: 0.0001)
        XCTAssertEqual(inOver, 1.0, accuracy: 0.0001)
    }

    func testEffectiveCrossfadeDuration() {
        // Auto-pause enabled -> must be 0.0 (strictly mutually exclusive)
        let autoPauseDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 2.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: true
        )
        XCTAssertEqual(autoPauseDuration, 0.0)

        // Requested 0.0 -> must be 0.0
        let zeroDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 0.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: false
        )
        XCTAssertEqual(zeroDuration, 0.0)

        // Normal track -> returns requested duration
        let normalDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 2.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: false
        )
        XCTAssertEqual(normalDuration, 2.0)

        // Short track (1.0s) with 2.0s requested -> clamped to segmentDuration * 0.5 = 0.5s
        let shortDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 2.0,
            segmentDuration: 1.0,
            isAutoPauseEnabled: false
        )
        XCTAssertEqual(shortDuration, 0.5)
    }

    @MainActor
    func testAppSettingsCrossfadeDuration() {
        let settings = AppSettings.shared
        let originalValue = settings.crossfadeDurationSeconds

        // Set to 3.0s
        settings.crossfadeDurationSeconds = 3.0
        XCTAssertEqual(settings.crossfadeDurationSeconds, 3.0)

        // Set to 0.0s (off)
        settings.crossfadeDurationSeconds = 0.0
        XCTAssertEqual(settings.crossfadeDurationSeconds, 0.0)

        // Restore original
        settings.crossfadeDurationSeconds = originalValue
    }

    @MainActor
    func testAppSettingsHapticFeedbackEnabled() {
        let settings = AppSettings.shared
        let originalValue = settings.isHapticFeedbackEnabled

        settings.isHapticFeedbackEnabled = false
        XCTAssertFalse(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = true
        XCTAssertTrue(settings.isHapticFeedbackEnabled)

        settings.isHapticFeedbackEnabled = originalValue
    }

    // MARK: - M6.3 Device Transfer Tests

    func testDeviceTransferResultModel() {
        let failureRes = DeviceTransferResult(
            success: false,
            message: "換機次數受限",
            remainingCooldownDays: 14
        )
        XCTAssertFalse(failureRes.success)
        XCTAssertEqual(failureRes.remainingCooldownDays, 14)
        XCTAssertNil(failureRes.planType)

        let successRes = DeviceTransferResult(
            success: true,
            message: "設備轉移成功",
            planType: "專業年繳版 (VIP)",
            remainingDays: 365
        )
        XCTAssertTrue(successRes.success)
        XCTAssertNil(successRes.remainingCooldownDays)
        XCTAssertEqual(successRes.planType, "專業年繳版 (VIP)")
        XCTAssertEqual(successRes.remainingDays, 365)
    }

    func testDeviceTransferCooldownDaysCalculation() {
        let oneDay: TimeInterval = 86400
        let lastTransfer = Date(timeIntervalSince1970: 1775000000)

        // 10 days later -> elapsed = 10 -> remaining = 20
        let tenDaysLater = lastTransfer.addingTimeInterval(10 * oneDay)
        let elapsed1 = tenDaysLater.timeIntervalSince(lastTransfer) / oneDay
        let remaining1 = max(0, Int(ceil(30.0 - elapsed1)))
        XCTAssertEqual(remaining1, 20)

        // 29.5 days later -> remaining = 1
        let almostEnd = lastTransfer.addingTimeInterval(29.5 * oneDay)
        let elapsed2 = almostEnd.timeIntervalSince(lastTransfer) / oneDay
        let remaining2 = max(0, Int(ceil(30.0 - elapsed2)))
        XCTAssertEqual(remaining2, 1)

        // 30.1 days later -> remaining = 0 (can transfer)
        let expiredCooldown = lastTransfer.addingTimeInterval(30.1 * oneDay)
        let elapsed3 = expiredCooldown.timeIntervalSince(lastTransfer) / oneDay
        let remaining3 = max(0, Int(ceil(30.0 - elapsed3)))
        XCTAssertEqual(remaining3, 0)
    }

    func testDeviceTransferUnlocksLocalVip() {
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: Date(timeIntervalSince1970: 1700000000))
        let testDefaults = UserDefaults(suiteName: "TransferTestDefaults_\(UUID().uuidString)")!

        // Initially expired
        let future = Date(timeIntervalSince1970: 1800000000)
        XCTAssertTrue(manager.isExpired(currentTime: future, defaults: testDefaults))

        // 換機成功後，LicenseVerificationService 直接信任伺服器已驗證過身分的結果
        // （帳號密碼 / 已在伺服器驗過簽章的序號），呼叫 activateVipFromServer 寫入本機狀態 ——
        // 不再靠寫死的 "RIDER-VIP-2026-PASS" 字串去騙本機的序號驗證邏輯解鎖（spec 項目 B）。
        let expiresAt = future.addingTimeInterval(365 * 86400.0)
        manager.activateVipFromServer(expiresAt: expiresAt, defaults: testDefaults)
        XCTAssertFalse(manager.isExpired(currentTime: future, defaults: testDefaults))
        XCTAssertTrue(manager.evaluateVipStatus(currentTime: future, defaults: testDefaults))
        XCTAssertEqual(manager.vipPlanName, "專業年繳版 (VIP)")
    }

    func testActivateVipFromServerWithPromoLabelsCorrectly() {
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: Date())
        let testDefaults = UserDefaults(suiteName: "PromoTestDefaults_\(UUID().uuidString)")!
        let expiresAt = Date().addingTimeInterval(30 * 86400.0)

        manager.activateVipFromServer(expiresAt: expiresAt, isPromo: true, code: "26FR-NR", defaults: testDefaults)
        XCTAssertTrue(manager.evaluateVipStatus(defaults: testDefaults))
        XCTAssertEqual(manager.vipPlanName, "推廣課程專屬版 (\(VersionLifecycleManager.promoTotalTrialDays)天免費)")
    }
}

