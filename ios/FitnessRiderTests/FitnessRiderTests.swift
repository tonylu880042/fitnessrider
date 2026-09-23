import XCTest
import CryptoKit
@testable import FitnessRider

final class FitnessRiderTests: XCTestCase {

    private func signVipSerial(privateKey: P256.Signing.PrivateKey, serialIdHex: String, planDays: Int) throws -> String {
        var payload = Data()
        payload.append(1)
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

        repo.saveClass(testClass)

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

        XCTAssertNoThrow(try backupService.restoreFromFile(at: backupURL))

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

        let unpacked = try archiveService.importRiderClass(from: archiveURL)
        XCTAssertEqual(unpacked.title, testClass.title)
        XCTAssertEqual(unpacked.segments.count, testClass.segments.count)
        let unpackedCue = unpacked.segments.first?.cues.first
        XCTAssertEqual(unpackedCue?.handPosition, .position3)
        XCTAssertEqual(unpackedCue?.reminders.count, 2)
        XCTAssertEqual(unpackedCue?.reminders.first, "站立姿勢來爬坡，鍛鍊股四頭肌力量")

        try? FileManager.default.removeItem(at: archiveURL)
        ClassRepository.shared.deleteClass(byId: unpacked.id)
    }

    private func buildStoredZipLocalHeader(name: String, data: Data) -> Data {
        let fileNameBytes = [UInt8](name.utf8)
        let fileNameLength = UInt16(fileNameBytes.count)
        let size = UInt32(data.count)
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                let mask = (crc & 1) != 0 ? UInt32(0xEDB88320) : 0
                crc = (crc >> 1) ^ mask
            }
        }
        crc = ~crc

        var header = Data()
        header.append(contentsOf: [0x50, 0x4b, 0x03, 0x04])
        header.append(contentsOf: [0x14, 0x00])
        header.append(contentsOf: [0x00, 0x00])
        header.append(contentsOf: [0x00, 0x00])
        header.append(contentsOf: [0x00, 0x00, 0x00, 0x00])
        header.append(contentsOf: withUnsafeBytes(of: crc.littleEndian) { Array($0) })
        header.append(contentsOf: withUnsafeBytes(of: size.littleEndian) { Array($0) })
        header.append(contentsOf: withUnsafeBytes(of: size.littleEndian) { Array($0) })
        header.append(contentsOf: withUnsafeBytes(of: fileNameLength.littleEndian) { Array($0) })
        header.append(contentsOf: [0x00, 0x00])
        header.append(contentsOf: fileNameBytes)
        header.append(data)
        return header
    }

    func testImportDecodesAndroidStyleStoredZipFixture() throws {
        let testClass = WorkoutClass(
            id: UUID(),
            title: "Android 產出的課表",
            author: "Coach Android",
            createdAt: Date(),
            totalDurationMs: 300000,
            estimatedCalories: 80.0,
            segments: [
                WorkoutSegment(
                    id: UUID(),
                    title: "跨平台相容段落",
                    musicFileName: "fixture_track.mp3",
                    durationMs: 300000,
                    baseBpm: 120.0,
                    playbackRate: 1.0,
                    intensityZone: 2,
                    cues: []
                )
            ]
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let jsonData = try encoder.encode(testClass)
        let audioData = Data([0x49, 0x44, 0x33, 0x03, 0x00, 0x01, 0x02, 0x03])

        var fixtureZip = Data()
        fixtureZip.append(buildStoredZipLocalHeader(name: "workout_class.json", data: jsonData))
        fixtureZip.append(buildStoredZipLocalHeader(name: "fixture_track.mp3", data: audioData))

        let fixtureURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).riderclass")
        try fixtureZip.write(to: fixtureURL)
        defer { try? FileManager.default.removeItem(at: fixtureURL) }

        let archiveService = RiderClassArchiveService.shared
        let decoded = try archiveService.importRiderClass(from: fixtureURL)

        XCTAssertEqual(decoded.title, "Android 產出的課表")
        XCTAssertEqual(decoded.segments.count, 1)
        XCTAssertEqual(decoded.segments.first?.musicFileName, "fixture_track.mp3")

        let musicDir = SQLiteDatabase.shared.musicDirectoryURL
        let importedAudioURL = musicDir.appendingPathComponent("fixture_track.mp3")
        XCTAssertTrue(FileManager.default.fileExists(atPath: importedAudioURL.path))
        XCTAssertEqual(try Data(contentsOf: importedAudioURL), audioData)

        try? FileManager.default.removeItem(at: importedAudioURL)
        ClassRepository.shared.deleteClass(byId: decoded.id)
    }

    func testImportSameArchiveTwiceProducesTwoClassesWithDifferentIds() throws {
        let musicDir = SQLiteDatabase.shared.musicDirectoryURL
        let fileName = "s2_dup_\(UUID().uuidString).mp3"
        let audioURL = musicDir.appendingPathComponent(fileName)
        let audioBytes = Data([0x01, 0x02, 0x03, 0x04])
        try audioBytes.write(to: audioURL)
        defer { try? FileManager.default.removeItem(at: audioURL) }

        let sourceClass = WorkoutClass(
            id: UUID(),
            title: "重複匯入測試",
            author: "Coach",
            createdAt: Date(),
            totalDurationMs: 60000,
            estimatedCalories: 10,
            segments: [
                WorkoutSegment(
                    id: UUID(),
                    title: "段落",
                    musicFileName: fileName,
                    durationMs: 60000,
                    baseBpm: 120.0,
                    playbackRate: 1.0,
                    intensityZone: 2,
                    cues: []
                )
            ]
        )

        let archiveService = RiderClassArchiveService.shared
        let archiveURL = try archiveService.exportRiderClass(for: sourceClass)
        defer { try? FileManager.default.removeItem(at: archiveURL) }

        let firstImport = try archiveService.importRiderClass(from: archiveURL)
        let secondImport = try archiveService.importRiderClass(from: archiveURL)

        XCTAssertNotEqual(firstImport.id, sourceClass.id)
        XCTAssertNotEqual(secondImport.id, sourceClass.id)
        XCTAssertNotEqual(firstImport.id, secondImport.id)
        XCTAssertEqual(firstImport.segments.first?.classId, firstImport.id)
        XCTAssertEqual(secondImport.segments.first?.classId, secondImport.id)
        XCTAssertNotEqual(firstImport.segments.first?.id, secondImport.segments.first?.id)

        ClassRepository.shared.deleteClass(byId: firstImport.id)
        ClassRepository.shared.deleteClass(byId: secondImport.id)
    }

    func testImportRenamesFileWhenLocalCopyHasDifferentContentAndKeepsOriginal() throws {
        let musicDir = SQLiteDatabase.shared.musicDirectoryURL
        let sharedName = "s2_collide_\(UUID().uuidString).mp3"
        let localURL = musicDir.appendingPathComponent(sharedName)
        let localBytes = Data([0xAA, 0xBB, 0xCC])
        try localBytes.write(to: localURL)
        defer { try? FileManager.default.removeItem(at: localURL) }

        let incomingBytes = Data([0x11, 0x22, 0x33, 0x44])
        let sourceClass = WorkoutClass(
            id: UUID(),
            title: "同名不同內容測試",
            author: "Coach",
            createdAt: Date(),
            totalDurationMs: 30000,
            estimatedCalories: 5,
            segments: [
                WorkoutSegment(
                    id: UUID(),
                    title: "段落",
                    musicFileName: sharedName,
                    durationMs: 30000,
                    baseBpm: 120.0,
                    playbackRate: 1.0,
                    intensityZone: 2,
                    cues: []
                )
            ]
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let jsonData = try encoder.encode(sourceClass)

        var fixtureZip = Data()
        fixtureZip.append(buildStoredZipLocalHeader(name: "workout_class.json", data: jsonData))
        fixtureZip.append(buildStoredZipLocalHeader(name: sharedName, data: incomingBytes))

        let fixtureURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).riderclass")
        try fixtureZip.write(to: fixtureURL)
        defer { try? FileManager.default.removeItem(at: fixtureURL) }

        let archiveService = RiderClassArchiveService.shared
        let imported = try archiveService.importRiderClass(from: fixtureURL)

        let newFileName = try XCTUnwrap(imported.segments.first?.musicFileName)
        XCTAssertNotEqual(newFileName, sharedName)

        XCTAssertEqual(try Data(contentsOf: localURL), localBytes)
        let renamedURL = musicDir.appendingPathComponent(newFileName)
        XCTAssertEqual(try Data(contentsOf: renamedURL), incomingBytes)

        try? FileManager.default.removeItem(at: renamedURL)
        ClassRepository.shared.deleteClass(byId: imported.id)
    }

    @MainActor
    func testTempoClampingAndPercentageStepping() {
        let audio = AudioEngineManager.shared

        audio.resetRate()
        XCTAssertEqual(audio.currentRate, 1.0)

        audio.adjustRatePercent(by: 2.0)
        XCTAssertEqual(audio.currentRate, 1.02)

        audio.adjustRatePercent(by: -4.0)
        XCTAssertEqual(audio.currentRate, 0.98)

        audio.setRate(1.50)
        XCTAssertEqual(audio.currentRate, 1.15)

        audio.setRate(0.50)
        XCTAssertEqual(audio.currentRate, 0.85)

        audio.resetRate()
        XCTAssertEqual(audio.currentRate, 1.0)
    }

    func testTapTempoDetectorAccuracy() {
        let detector = TapTempoDetector()

        XCTAssertNil(detector.calculateCurrentBpm())
        XCTAssertEqual(detector.tapCount, 0)

        let startTime: TimeInterval = 1000.0
        detector.recordTap(at: startTime)
        XCTAssertNil(detector.calculateCurrentBpm())

        detector.recordTap(at: startTime + 0.5)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)

        detector.recordTap(at: startTime + 1.0)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)

        detector.recordTap(at: startTime + 1.5)
        XCTAssertEqual(detector.calculateCurrentBpm()!, 120.0, accuracy: 0.5)
        XCTAssertEqual(detector.tapCount, 4)

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

        detector.recordTap(at: t + 3.0)
        XCTAssertEqual(detector.tapCount, 1)
        XCTAssertNil(detector.calculateCurrentBpm())

        let bpm120 = TapTempoDetector.calculateBpm(from: [0.5, 0.5, 0.5])
        XCTAssertEqual(bpm120!, 120.0, accuracy: 0.1)
    }

    func testRecalculateTotalsMatchesSegmentsAndAndroidFormula() {
        var workoutClass = WorkoutClass(
            title: "測試課表",
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

        var empty = WorkoutClass(totalDurationMs: 999, estimatedCalories: 999.0, segments: [])
        empty.recalculateTotals()
        XCTAssertEqual(empty.totalDurationMs, 0)
        XCTAssertEqual(empty.estimatedCalories, 0.0, accuracy: 0.001)

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
        let totalClassSec = 3000
        let totalCalories = 500.0

        var elapsedSec = 0
        var ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 0)

        elapsedSec = 1500
        ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 250)

        elapsedSec = 3000
        ratio = min(1.0, max(0.0, Double(elapsedSec) / Double(totalClassSec)))
        XCTAssertEqual(Int(totalCalories * ratio), 500)

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
        let duration = 180.0

        var currentOffset = 5.0
        var targetOffset = min(duration, max(0.0, currentOffset - 10.0))
        XCTAssertEqual(targetOffset, 0.0, accuracy: 0.001)

        currentOffset = 30.0
        targetOffset = min(duration, max(0.0, currentOffset + 10.0))
        XCTAssertEqual(targetOffset, 40.0, accuracy: 0.001)

        currentOffset = 175.0
        targetOffset = min(duration, max(0.0, currentOffset + 10.0))
        XCTAssertEqual(targetOffset, 180.0, accuracy: 0.001)
    }

    func testResolveUniqueMusicFileNameAppendsSuffixOnCollision() {
        XCTAssertEqual(resolveUniqueMusicFileName("track.mp3", existingNames: []), "track.mp3")

        XCTAssertEqual(
            resolveUniqueMusicFileName("track.mp3", existingNames: ["track.mp3"]),
            "track_1.mp3"
        )

        XCTAssertEqual(
            resolveUniqueMusicFileName("track.mp3", existingNames: ["track.mp3", "track_1.mp3"]),
            "track_2.mp3"
        )

        XCTAssertEqual(resolveUniqueMusicFileName("track", existingNames: ["track"]), "track_1")
    }

    func testMusicTitleFromFileNameStripsExtension() {
        XCTAssertEqual(musicTitleFromFileName("我的歌曲.mp3"), "我的歌曲")
        XCTAssertEqual(musicTitleFromFileName("no_extension"), "no_extension")
    }

    func testBuildSegmentsForImportedTracksMapsEachUriToOneSegment() {
        let classId = UUID()
        let tracks = [
            ImportedTrackInfo(fileName: "track_1.mp3", durationMs: 0, bpm: 0.0),
            ImportedTrackInfo(fileName: "曲目二.mp3", durationMs: 245_000, bpm: 132.5),
            ImportedTrackInfo(fileName: "track_1_1.mp3", durationMs: 180_000, bpm: 0.0)
        ]

        let segments = buildSegmentsForImportedTracks(tracks: tracks, classId: classId, startOrderIndex: 2)

        XCTAssertEqual(segments.count, 3)

        XCTAssertEqual(segments[0].orderIndex, 2)
        XCTAssertEqual(segments[1].orderIndex, 3)
        XCTAssertEqual(segments[2].orderIndex, 4)
        segments.forEach { XCTAssertEqual($0.classId, classId) }

        XCTAssertEqual(segments[0].title, "track_1")
        XCTAssertEqual(segments[1].title, "曲目二")
        XCTAssertEqual(segments[1].durationMs, 245_000)

        XCTAssertEqual(segments[0].durationMs, 300_000)
        XCTAssertEqual(segments[0].baseBpm, 128.0, accuracy: 0.001)
        XCTAssertEqual(segments[1].baseBpm, 132.5, accuracy: 0.001)
        XCTAssertEqual(segments[2].durationMs, 180_000)
        XCTAssertEqual(segments[2].baseBpm, 128.0, accuracy: 0.001)

        segments.forEach { XCTAssertFalse($0.cues.isEmpty) }
    }

    func testBuildMusicLibraryTracksReadsCacheAndSortsByTitle() {
        let cache: [String: (Int, Double)] = [
            "b_track.mp3": (210_000, 118.0),
            "a_track.mp3": (190_000, 126.0)
        ]
        let tracks = buildMusicLibraryTracks(fileNames: ["b_track.mp3", "c_no_cache.mp3", "a_track.mp3"]) { cache[$0] }

        XCTAssertEqual(tracks.map { $0.title }, ["a_track", "b_track", "c_no_cache"])

        let aTrack = tracks.first { $0.fileName == "a_track.mp3" }!
        XCTAssertEqual(aTrack.durationMs, 190_000)
        XCTAssertEqual(aTrack.bpm, 126.0, accuracy: 0.001)

        let noCacheTrack = tracks.first { $0.fileName == "c_no_cache.mp3" }!
        XCTAssertEqual(noCacheTrack.durationMs, 300_000)
        XCTAssertEqual(noCacheTrack.bpm, 128.0, accuracy: 0.001)
    }

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
        XCTAssertEqual(segments[0].musicFileName, "climb_anthem.mp3")
        XCTAssertEqual(segments[1].musicFileName, "sprint_fire.mp3")
        XCTAssertEqual(segments[0].title, "climb_anthem")
        XCTAssertEqual(segments[0].durationMs, 420_000)
        XCTAssertEqual(segments[0].baseBpm, 130.0, accuracy: 0.001)
        XCTAssertEqual(segments[1].durationMs, 240_000)
        XCTAssertEqual(segments[1].baseBpm, 140.0, accuracy: 0.001)
    }

    func testMusicSourceIsExternalDetectsPrefix() {
        XCTAssertTrue(MusicSource.isExternal("extfolder://Coach/warmup.mp3"))
        XCTAssertFalse(MusicSource.isExternal("track.mp3"))
        XCTAssertFalse(MusicSource.isExternal(""))
    }

    func testIsAudioFileNameMatchesKnownExtensions() {
        XCTAssertTrue(isAudioFileName("track.mp3"))
        XCTAssertTrue(isAudioFileName("TRACK.MP3"))
        XCTAssertTrue(isAudioFileName("track.m4a"))
        XCTAssertFalse(isAudioFileName("cover.jpg"))
        XCTAssertFalse(isAudioFileName("readme.txt"))
    }

    func testListExternalMusicEntriesRespectsIncludeSubdirectoriesAndFiltersNonAudio() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("ext_music_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let subDir = tempDir.appendingPathComponent("Warmups")
        try FileManager.default.createDirectory(at: subDir, withIntermediateDirectories: true)

        try Data().write(to: tempDir.appendingPathComponent("top_level.mp3"))
        try Data().write(to: tempDir.appendingPathComponent("cover.jpg"))
        try Data().write(to: subDir.appendingPathComponent("nested.m4a"))

        let shallow = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: false)
        XCTAssertEqual(shallow.map { $0.displayName }, ["top_level.mp3"])

        let deep = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: true)
        let names = Set(deep.map { $0.displayName })
        XCTAssertEqual(names, ["top_level.mp3", "nested.m4a"])
        let nested = deep.first { $0.displayName == "nested.m4a" }
        XCTAssertEqual(nested?.relativePath, "Warmups/nested.m4a")
    }

    func testICloudPlaceholderNameRecognisesPlaceholdersOnly() {
        XCTAssertEqual(iCloudPlaceholderName(for: ".Sprint Fire.mp3.icloud"), "Sprint Fire.mp3")
        XCTAssertNil(iCloudPlaceholderName(for: "Sprint Fire.mp3"))
        XCTAssertNil(iCloudPlaceholderName(for: ".DS_Store"))
        XCTAssertNil(iCloudPlaceholderName(for: ".icloud"))
    }

    func testListExternalMusicEntriesSurfacesICloudPlaceholders() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("ext_icloud_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let subDir = tempDir.appendingPathComponent("Warmups")
        try FileManager.default.createDirectory(at: subDir, withIntermediateDirectories: true)

        try Data().write(to: tempDir.appendingPathComponent("downloaded.mp3"))
        try Data().write(to: tempDir.appendingPathComponent(".not_downloaded.mp3.icloud"))
        try Data().write(to: tempDir.appendingPathComponent(".DS_Store"))
        try Data().write(to: tempDir.appendingPathComponent(".cover.jpg.icloud"))
        try Data().write(to: subDir.appendingPathComponent(".nested.m4a.icloud"))

        let deep = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: true)
        XCTAssertEqual(Set(deep.map { $0.displayName }), ["downloaded.mp3", "not_downloaded.mp3", "nested.m4a"])

        let nested = deep.first { $0.displayName == "nested.m4a" }
        XCTAssertEqual(nested?.relativePath, "Warmups/nested.m4a")
        XCTAssertEqual(nested?.isCloudPlaceholder, true)
        XCTAssertEqual(deep.first { $0.displayName == "downloaded.mp3" }?.isCloudPlaceholder, false)

        try Data().write(to: tempDir.appendingPathComponent(".downloaded.mp3.icloud"))
        let racing = listExternalMusicEntries(baseURL: tempDir, includeSubdirectories: false)
        XCTAssertEqual(racing.filter { $0.displayName == "downloaded.mp3" }.count, 1)
        XCTAssertEqual(racing.first { $0.displayName == "downloaded.mp3" }?.isCloudPlaceholder, false)
    }

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
        XCTAssertEqual(segments[0].title, "warmup")
        XCTAssertEqual(segments[1].title, "sprint")
        XCTAssertEqual(segments[0].durationMs, 300_000)
        XCTAssertEqual(segments[0].baseBpm, 128.0, accuracy: 0.001)
        XCTAssertTrue(MusicSource.isExternal(segments[0].musicFileName))
    }

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

    func testCopyMusicFileOrCleanupRemovesLeftoverOnFailure() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("music_import_test_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let destURL = tempDir.appendingPathComponent("track.mp3")
        try Data([0x01, 0x02, 0x03]).write(to: destURL)

        let missingSourceURL = tempDir.appendingPathComponent("does_not_exist.mp3")
        let copied = copyMusicFileOrCleanup(from: missingSourceURL, to: destURL)

        XCTAssertFalse(copied, "來源不存在，複製要回報失敗")
        XCTAssertFalse(
            FileManager.default.fileExists(atPath: destURL.path),
            "失敗後不能在目的檔名留下任何殘留檔案（半成品或舊檔）佔用檔名"
        )
    }

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

    func testExternalMusicFolderStorePersistsIncludeSubdirectoriesPreference() throws {
        let store = ExternalMusicFolderStore.shared
        let defaults = UserDefaults(suiteName: "FitnessRiderExternalFolderTest_\(UUID().uuidString)")!

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

        let sawFile = store.withFileAccess(relativePath: "song.mp3", defaults: defaults) { url in
            FileManager.default.fileExists(atPath: url.path)
        }
        XCTAssertEqual(sawFile, true)

        store.clearFolder(defaults: defaults)
        XCTAssertFalse(store.isFolderConfigured(defaults: defaults))
        let afterClear = store.withFileAccess(relativePath: "song.mp3", defaults: defaults) { _ in true }
        XCTAssertNil(afterClear)
    }

    func testVersionLifecycleExpiration() {
        let baseDate = Date(timeIntervalSince1970: 1774000000)
        let manager = VersionLifecycleManager(buildDate: baseDate)
        let testDefaults = UserDefaults(suiteName: "FitnessRiderTestDefaults_\(UUID().uuidString)")!

        let oneDay: TimeInterval = 86400.0

        let day0 = baseDate
        XCTAssertFalse(manager.isExpired(currentTime: day0, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day0, defaults: testDefaults), 7)

        let day3 = baseDate.addingTimeInterval(3 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day3, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day3, defaults: testDefaults), 4)

        let day5 = baseDate.addingTimeInterval(5 * oneDay)
        let rem5 = manager.remainingDays(currentTime: day5, defaults: testDefaults)
        XCTAssertEqual(rem5, 2)
        XCTAssertTrue((1...7).contains(rem5))

        let day6 = baseDate.addingTimeInterval(6 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day6, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day6, defaults: testDefaults), 1)

        let day7 = baseDate.addingTimeInterval(7 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day7, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day7, defaults: testDefaults), 0)

        let day10 = baseDate.addingTimeInterval(10 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day10, defaults: testDefaults), 0)

        let rollbackDefaults = UserDefaults(suiteName: "FitnessRiderRollback_\(UUID().uuidString)")!
        let day4 = baseDate.addingTimeInterval(4 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day4, defaults: rollbackDefaults))
        let day2 = baseDate.addingTimeInterval(2 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day2, defaults: rollbackDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day2, defaults: rollbackDefaults), 0)

        let persistenceDefaults = UserDefaults(suiteName: "FitnessRiderPersistence_\(UUID().uuidString)")!
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: persistenceDefaults))
        XCTAssertTrue(manager.isExpired(currentTime: day2, defaults: persistenceDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day2, defaults: persistenceDefaults), 0)

        XCTAssertEqual(manager.expirationDate.timeIntervalSince(baseDate), 7.0 * 86400.0, accuracy: 0.001)

        XCTAssertFalse(manager.buildDateFormatted.isEmpty)
        XCTAssertFalse(manager.expirationDateFormatted.isEmpty)
        XCTAssertTrue(VersionLifecycleManager.updateURL.absoluteString.hasPrefix("https://"))
    }

    func testSevenDayTrialCalculationFromFirstLaunch() {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "TrialTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        XCTAssertFalse(manager.isExpired(currentTime: launchDate, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: launchDate, defaults: testDefaults), 7)

        let day3 = launchDate.addingTimeInterval(3 * oneDay)
        XCTAssertFalse(manager.isExpired(currentTime: day3, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day3, defaults: testDefaults), 4)

        let day7 = launchDate.addingTimeInterval(7 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day7, defaults: testDefaults))
        XCTAssertEqual(manager.remainingDays(currentTime: day7, defaults: testDefaults), 0)

        XCTAssertFalse(manager.trialStartDateFormatted.isEmpty)
        XCTAssertEqual(manager.expirationDate.timeIntervalSince(launchDate), 7.0 * 86400.0, accuracy: 0.001)
    }

    func testPromoCodeActivationAndAntiAbuse() {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "PromoTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        let day10 = launchDate.addingTimeInterval(10 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day10, defaults: testDefaults))

        let promoRes = manager.activateLicenseCode("26FR-NR", defaults: testDefaults, overrideCurrentDate: day10)
        XCTAssertTrue(promoRes.success)
        XCTAssertTrue(promoRes.message.contains("30 天"))
        XCTAssertTrue(manager.isVIP)
        XCTAssertEqual(manager.vipPlanName, "推廣課程專屬版 (30天免費)")
        XCTAssertFalse(manager.isExpired(currentTime: day10, defaults: testDefaults))

        let duplicateRes = manager.activateLicenseCode("26FR-NR", defaults: testDefaults, overrideCurrentDate: day10)
        XCTAssertFalse(duplicateRes.success)
        XCTAssertTrue(duplicateRes.message.contains("無法重複領取") || duplicateRes.message.contains("已兌換過"))
    }

    func testVipLicenseCodeActivationUnlocksExpiredState() throws {
        let launchDate = Date(timeIntervalSince1970: 1775000000)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: launchDate)
        let testDefaults = UserDefaults(suiteName: "VipTestDefaults_\(UUID().uuidString)")!
        let oneDay: TimeInterval = 86400.0

        let day35 = launchDate.addingTimeInterval(35 * oneDay)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

        let invalidRes = manager.activateLicenseCode("INVALID-KEY-1234", defaults: testDefaults)
        XCTAssertFalse(invalidRes.success)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

        XCTAssertFalse(manager.activateLicenseCode("RIDER-VIP-2026-PASS", defaults: testDefaults).success)
        XCTAssertFalse(manager.activateLicenseCode("RIDER-VIP-0000", defaults: testDefaults).success)
        XCTAssertFalse(manager.activateLicenseCode("FITNESS-PRO-ANNUAL-KEY", defaults: testDefaults).success)
        XCTAssertTrue(manager.isExpired(currentTime: day35, defaults: testDefaults))

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

    func testVipSerialVerifierRejectsTamperedAndWrongKeySerials() throws {
        let keyPairA = P256.Signing.PrivateKey()
        let keyPairB = P256.Signing.PrivateKey()
        let publicKeyA = keyPairA.publicKey.derRepresentation.base64EncodedString()

        let serial = try signVipSerial(privateKey: keyPairA, serialIdHex: "01020304", planDays: 30)

        let info = VipSerialVerifier.verify(serial, publicKeySPKIBase64Override: publicKeyA)
        XCTAssertEqual(info?.serialId, "01020304")
        XCTAssertEqual(info?.planDays, 30)

        let serialSignedByB = try signVipSerial(privateKey: keyPairB, serialIdHex: "01020304", planDays: 30)
        XCTAssertNil(VipSerialVerifier.verify(serialSignedByB, publicKeySPKIBase64Override: publicKeyA))

        let tamperedPayloadHex = "0101020304012C"
        let originalSigHex = String(serial.split(separator: "-").last!)
        let tampered = "FRVIP-\(tamperedPayloadHex)-\(originalSigHex)"
        XCTAssertNil(VipSerialVerifier.verify(tampered, publicKeySPKIBase64Override: publicKeyA))

        XCTAssertNil(VipSerialVerifier.verify("RIDER-VIP-2026-PASS", publicKeySPKIBase64Override: publicKeyA))
        XCTAssertNil(VipSerialVerifier.verify("RIDER-VIP-0000000000", publicKeySPKIBase64Override: publicKeyA))
    }

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

    func testMissingOrZeroVipExpiryIsNotTreatedAsVip() {
        let testDefaults = UserDefaults(suiteName: "MissingExpiryTestDefaults_\(UUID().uuidString)")!
        testDefaults.set(true, forKey: "fitness_rider_vip_active")

        let manager = VersionLifecycleManager(explicitFirstLaunchDate: Date(timeIntervalSince1970: 1775000000))
        XCTAssertFalse(manager.evaluateVipStatus(currentTime: Date(timeIntervalSince1970: 1775000000), defaults: testDefaults))
        XCTAssertFalse(manager.evaluateVipStatus(currentTime: Date.distantFuture, defaults: testDefaults))
    }

    func testMustUpdateNeverLocksWhenBackendUnreachableOrDisabled() {
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 0, currentBuildNumber: 1))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: -1, currentBuildNumber: 1))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 5))
        XCTAssertFalse(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 6))
        XCTAssertTrue(LicenseVerificationService.computeMustUpdate(minSupportedVersionCode: 5, currentBuildNumber: 4))
    }

    func testBusinessConstantsMatchAcrossPlatforms() {
        let thisFilePath = URL(fileURLWithPath: #filePath)
        let repoRoot = thisFilePath
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
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

    func testPromoCodeRedemptionFailsIfDeviceOlderThan30DaysAndDoesNotBurn() {
        let testDefaults = UserDefaults(suiteName: "PromoOlderThan30Days_\(UUID().uuidString)")!
        let now = Date()
        let thirtyFiveDaysAgo = now.addingTimeInterval(-35 * 86_400)
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: thirtyFiveDaysAgo)

        let currentYear = Calendar.current.component(.year, from: Date()) % 100
        let promoCode = String(format: "%02dFR-NR", currentYear)

        let result = manager.activateLicenseCode(promoCode, defaults: testDefaults, overrideCurrentDate: now)
        XCTAssertFalse(result.success, "首次啟動超過 30 天之設備不可兌換推廣代碼")
        XCTAssertTrue(
            result.message.contains("超過 \(VersionLifecycleManager.promoTotalTrialDays) 天"),
            "錯誤訊息應清楚告知已超過 \(VersionLifecycleManager.promoTotalTrialDays) 天"
        )

        let redeemedList = testDefaults.stringArray(forKey: "fitness_rider_redeemed_promos") ?? []
        XCTAssertFalse(redeemedList.contains(promoCode), "失敗時不可將代碼寫入已兌換清單（不得白燒額度）")
        XCTAssertFalse(testDefaults.bool(forKey: "fitness_rider_vip_active"), "不可設為 VIP 啟用")
    }

    func testPromoCodeCannotDowngradeActivePaidVip() throws {
        let testDefaults = UserDefaults(suiteName: "PromoNoDowngrade_\(UUID().uuidString)")!
        let now = Date()
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: now)

        let keyPair = P256.Signing.PrivateKey()
        let publicKeyB64 = keyPair.publicKey.derRepresentation.base64EncodedString()
        let serial = try signVipSerial(privateKey: keyPair, serialIdHex: "0A0B0C0D", planDays: 365)

        let vipResult = manager.activateLicenseCode(
            serial,
            defaults: testDefaults,
            testVipPublicKeyOverride: publicKeyB64,
            overrideCurrentDate: now
        )
        XCTAssertTrue(vipResult.success)
        let paidExpiresTs = testDefaults.double(forKey: "fitness_rider_vip_expires")
        XCTAssertEqual(paidExpiresTs, now.addingTimeInterval(365 * 86_400).timeIntervalSince1970, accuracy: 1.0)

        let currentYear = Calendar.current.component(.year, from: Date()) % 100
        let promoCode = String(format: "%02dFR-NR", currentYear)
        let promoResult = manager.activateLicenseCode(promoCode, defaults: testDefaults, overrideCurrentDate: now)

        XCTAssertFalse(promoResult.success, "推廣代碼不可覆蓋生效中的付費年繳 VIP")
        XCTAssertTrue(promoResult.message.contains("已有生效中的專業年繳版"))
        XCTAssertEqual(testDefaults.double(forKey: "fitness_rider_vip_expires"), paidExpiresTs, accuracy: 0.001)
        let redeemedList = testDefaults.stringArray(forKey: "fitness_rider_redeemed_promos") ?? []
        XCTAssertFalse(redeemedList.contains(promoCode), "被拒絕時不可白燒推廣碼額度")
    }

    func testPromoBlockedByPaidVipMessageOnlyBlocksPaidVip() {
        let defaults = UserDefaults(suiteName: "PromoGuard_\(UUID().uuidString)")!
        let now = Date()
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: now)
        let currentYear = Calendar.current.component(.year, from: now) % 100
        let promoCode = String(format: "%02dFR-NR", currentYear)

        XCTAssertNil(manager.promoBlockedByPaidVipMessage(promoCode, defaults: defaults, currentTime: now))

        defaults.set(true, forKey: "fitness_rider_vip_active")
        defaults.set(now.addingTimeInterval(86_400).timeIntervalSince1970, forKey: "fitness_rider_vip_expires")
        defaults.set("25FR-NR", forKey: "fitness_rider_vip_code")
        XCTAssertNil(manager.promoBlockedByPaidVipMessage(promoCode, defaults: defaults, currentTime: now))
        defaults.set("promo_verified", forKey: "fitness_rider_vip_code")
        XCTAssertNil(manager.promoBlockedByPaidVipMessage(promoCode, defaults: defaults, currentTime: now))

        defaults.set("FRVIP-01020304FFFF-AABB", forKey: "fitness_rider_vip_code")
        XCTAssertTrue(
            manager.promoBlockedByPaidVipMessage(promoCode, defaults: defaults, currentTime: now)?
                .contains("已有生效中的專業年繳版") == true
        )

        XCTAssertNil(manager.promoBlockedByPaidVipMessage("FRVIP-01020304FFFF-AABB", defaults: defaults, currentTime: now))

        defaults.set(now.addingTimeInterval(-1).timeIntervalSince1970, forKey: "fitness_rider_vip_expires")
        XCTAssertNil(manager.promoBlockedByPaidVipMessage(promoCode, defaults: defaults, currentTime: now))
    }

    func testIsPromoVipCodeIsYearAgnostic() {
        XCTAssertTrue(VersionLifecycleManager.isPromoVipCode("26FR-NR"))
        XCTAssertTrue(VersionLifecycleManager.isPromoVipCode("99FR-NR"))
        XCTAssertTrue(VersionLifecycleManager.isPromoVipCode("promo_verified"))
        XCTAssertFalse(VersionLifecycleManager.isPromoVipCode("server_verified"))
        XCTAssertFalse(VersionLifecycleManager.isPromoVipCode("FRVIP-01020304FFFF-AABB"))
        XCTAssertFalse(VersionLifecycleManager.isPromoVipCode(nil))
    }

    func testDeviceIdentifierServiceKeychainAccessors() {
        let service = DeviceIdentifierService.shared
        let testKey = "app.fitnessrider.unit_test_key"
        let testVal = "test_value_\(UUID().uuidString)"

        service.setKeychainString(key: testKey, value: testVal)
        XCTAssertEqual(service.getKeychainString(key: testKey), testVal)

        service.deleteKeychainString(key: testKey)
        XCTAssertNil(service.getKeychainString(key: testKey))
    }

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

    func testEqualPowerCrossfadeCalculation() {
        let (out0, in0) = CrossfadeCalculator.equalPowerVolumes(progress: 0.0)
        XCTAssertEqual(out0, 1.0, accuracy: 0.0001)
        XCTAssertEqual(in0, 0.0, accuracy: 0.0001)

        let (out1, in1) = CrossfadeCalculator.equalPowerVolumes(progress: 1.0)
        XCTAssertEqual(out1, 0.0, accuracy: 0.0001)
        XCTAssertEqual(in1, 1.0, accuracy: 0.0001)

        let (outMid, inMid) = CrossfadeCalculator.equalPowerVolumes(progress: 0.5)
        XCTAssertEqual(outMid, 0.7071, accuracy: 0.001)
        XCTAssertEqual(inMid, 0.7071, accuracy: 0.001)

        let testPoints = [0.0, 0.1, 0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1.0]
        for p in testPoints {
            let (vOut, vIn) = CrossfadeCalculator.equalPowerVolumes(progress: p)
            let totalPower = (vOut * vOut) + (vIn * vIn)
            XCTAssertEqual(totalPower, 1.0, accuracy: 0.001, "Power should be 1.0 at progress \(p)")
        }

        let (outNeg, inNeg) = CrossfadeCalculator.equalPowerVolumes(progress: -0.5)
        XCTAssertEqual(outNeg, 1.0, accuracy: 0.0001)
        XCTAssertEqual(inNeg, 0.0, accuracy: 0.0001)

        let (outOver, inOver) = CrossfadeCalculator.equalPowerVolumes(progress: 1.8)
        XCTAssertEqual(outOver, 0.0, accuracy: 0.0001)
        XCTAssertEqual(inOver, 1.0, accuracy: 0.0001)
    }

    func testEffectiveCrossfadeDuration() {
        let autoPauseDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 2.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: true
        )
        XCTAssertEqual(autoPauseDuration, 0.0)

        let zeroDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 0.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: false
        )
        XCTAssertEqual(zeroDuration, 0.0)

        let normalDuration = CrossfadeCalculator.effectiveDuration(
            requestedDuration: 2.0,
            segmentDuration: 60.0,
            isAutoPauseEnabled: false
        )
        XCTAssertEqual(normalDuration, 2.0)

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

        settings.crossfadeDurationSeconds = 3.0
        XCTAssertEqual(settings.crossfadeDurationSeconds, 3.0)

        settings.crossfadeDurationSeconds = 0.0
        XCTAssertEqual(settings.crossfadeDurationSeconds, 0.0)

        settings.crossfadeDurationSeconds = originalValue
    }

    @MainActor
    func testCrossfadeOptionsSecondsMatchAcrossPlatforms() {
        let options = AppSettings.crossfadeOptionsSeconds
        XCTAssertEqual(options, [0.0, 1.0, 2.0, 3.0, 5.0, 8.0])
        XCTAssertTrue(options.contains(2.0), "預設值 2 秒必須仍是合法選項之一")
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

        let tenDaysLater = lastTransfer.addingTimeInterval(10 * oneDay)
        let elapsed1 = tenDaysLater.timeIntervalSince(lastTransfer) / oneDay
        let remaining1 = max(0, Int(ceil(30.0 - elapsed1)))
        XCTAssertEqual(remaining1, 20)

        let almostEnd = lastTransfer.addingTimeInterval(29.5 * oneDay)
        let elapsed2 = almostEnd.timeIntervalSince(lastTransfer) / oneDay
        let remaining2 = max(0, Int(ceil(30.0 - elapsed2)))
        XCTAssertEqual(remaining2, 1)

        let expiredCooldown = lastTransfer.addingTimeInterval(30.1 * oneDay)
        let elapsed3 = expiredCooldown.timeIntervalSince(lastTransfer) / oneDay
        let remaining3 = max(0, Int(ceil(30.0 - elapsed3)))
        XCTAssertEqual(remaining3, 0)
    }

    func testDeviceTransferUnlocksLocalVip() {
        let manager = VersionLifecycleManager(explicitFirstLaunchDate: Date(timeIntervalSince1970: 1700000000))
        let testDefaults = UserDefaults(suiteName: "TransferTestDefaults_\(UUID().uuidString)")!

        let future = Date(timeIntervalSince1970: 1800000000)
        XCTAssertTrue(manager.isExpired(currentTime: future, defaults: testDefaults))

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

    func testSegmentReorderAndDeleteReindexesOrderIndex() {
        let classId = UUID()
        func seg(_ title: String, _ order: Int) -> WorkoutSegment {
            WorkoutSegment(id: UUID(), classId: classId, orderIndex: order, title: title)
        }
        let original = [seg("A", 0), seg("B", 1), seg("C", 2), seg("D", 3)]

        let movedOnce = segmentsAfterMove(original, index: 0, offset: 1)
        XCTAssertEqual(movedOnce.map { $0.title }, ["B", "A", "C", "D"])
        XCTAssertEqual(movedOnce.map { $0.orderIndex }, [0, 1, 2, 3])

        let movedToEnd = segmentsAfterMove(original, index: 0, offset: 3)
        XCTAssertEqual(movedToEnd.map { $0.title }, ["B", "C", "D", "A"])
        XCTAssertEqual(movedToEnd.map { $0.orderIndex }, [0, 1, 2, 3])

        let afterRemoval = segmentsAfterRemoval(original, index: 1)
        XCTAssertEqual(afterRemoval.map { $0.title }, ["A", "C", "D"])
        XCTAssertEqual(afterRemoval.map { $0.orderIndex }, [0, 1, 2])

        let outOfRangeMove = segmentsAfterMove(original, index: 0, offset: -1)
        XCTAssertEqual(outOfRangeMove, original)

        let outOfRangeRemoval = segmentsAfterRemoval(original, index: 99)
        XCTAssertEqual(outOfRangeRemoval, original)
    }

    func testSelectedSegmentIndexTracksMoveAndRemoval() {
        XCTAssertEqual(selectedIndexAfterMove(2, movedFromIndex: 2, movedToIndex: 1), 1)
        XCTAssertEqual(selectedIndexAfterMove(1, movedFromIndex: 2, movedToIndex: 1), 2)
        XCTAssertEqual(selectedIndexAfterMove(3, movedFromIndex: 0, movedToIndex: 1), 3)

        XCTAssertEqual(selectedIndexAfterRemoval(2, removedIndex: 0, newSize: 3), 1)
        XCTAssertEqual(selectedIndexAfterRemoval(0, removedIndex: 2, newSize: 3), 0)
        XCTAssertEqual(selectedIndexAfterRemoval(0, removedIndex: 0, newSize: 0), 0)
        XCTAssertEqual(selectedIndexAfterRemoval(3, removedIndex: 3, newSize: 3), 2)
    }

    func testRateStepForSwipeHorizontalPastThreshold() {
        XCTAssertEqual(rateStepForSwipe(dx: 80, dy: 0, threshold: 60), 1)
        XCTAssertEqual(rateStepForSwipe(dx: -80, dy: 0, threshold: 60), -1)
        XCTAssertEqual(rateStepForSwipe(dx: 500, dy: 0, threshold: 60), 1)
    }

    func testRateStepForSwipeBelowThresholdIsIgnored() {
        XCTAssertEqual(rateStepForSwipe(dx: 40, dy: 0, threshold: 60), 0)
        XCTAssertEqual(rateStepForSwipe(dx: -59, dy: 0, threshold: 60), 0)
        XCTAssertEqual(rateStepForSwipe(dx: 60, dy: 0, threshold: 60), 0)
    }

    func testRateStepForSwipeVerticalDominantIsIgnored() {
        XCTAssertEqual(rateStepForSwipe(dx: 70, dy: 90, threshold: 60), 0)
        XCTAssertEqual(rateStepForSwipe(dx: -70, dy: 100, threshold: 60), 0)
        XCTAssertEqual(rateStepForSwipe(dx: 90, dy: 70, threshold: 60), 1)
    }

    func testSegmentStepForSwipeVerticalPastThreshold() {
        XCTAssertEqual(segmentStepForSwipe(dx: 0, dy: -100, threshold: 80), 1)
        XCTAssertEqual(segmentStepForSwipe(dx: 0, dy: 100, threshold: 80), -1)
        XCTAssertEqual(segmentStepForSwipe(dx: 0, dy: -60, threshold: 80), 0)
        XCTAssertEqual(segmentStepForSwipe(dx: 0, dy: -80, threshold: 80), 0)
        XCTAssertEqual(segmentStepForSwipe(dx: 200, dy: -100, threshold: 80), 0)
    }

    func testRateAndSegmentSwipesAreMutuallyExclusive() {
        let swipes: [(CGFloat, CGFloat)] = [
            (200, 0), (-200, 0),
            (0, 200), (0, -200),
            (100, 120), (-100, 120),
            (100, -160), (160, -100)
        ]
        for (dx, dy) in swipes {
            let rate = rateStepForSwipe(dx: dx, dy: dy, threshold: 60)
            let segment = segmentStepForSwipe(dx: dx, dy: dy, threshold: 80)
            XCTAssertFalse(rate != 0 && segment != 0, "同一次滑動 (\(dx), \(dy)) 同時觸發了變速與換曲")
        }
        XCTAssertEqual(rateStepForSwipe(dx: 100, dy: 120, threshold: 60), 0)
        XCTAssertEqual(segmentStepForSwipe(dx: 100, dy: 120, threshold: 80), 0)
    }

    func testPaywallPricingMatchesSpecAndYearlySavingsArithmetic() {
        XCTAssertEqual(PaywallPricing.monthlyPriceTWD, 390)
        XCTAssertEqual(PaywallPricing.quarterlyPriceTWD, 890)
        XCTAssertEqual(PaywallPricing.yearlyPriceTWD, 2390)
        XCTAssertEqual(PaywallPricing.yearlySavingsTWD, 2290)
        XCTAssertEqual(
            PaywallPricing.monthlyPriceTWD * 12 - PaywallPricing.yearlyPriceTWD,
            PaywallPricing.yearlySavingsTWD
        )
        XCTAssertEqual(PaywallPricing.formatTwd(PaywallPricing.yearlyPriceTWD), "NT$2,390")
        XCTAssertEqual(PaywallPricing.formatTwd(PaywallPricing.monthlyPriceTWD), "NT$390")
        XCTAssertTrue(PaywallPricing.yearlyBadgeText.contains("NT$2,290"))
    }
}

