import Foundation
import SQLite3

public final class ClassRepository: @unchecked Sendable {
    public static let shared = ClassRepository()

    private let db = SQLiteDatabase.shared
    private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private init() {
        seedInitialDataIfNeeded()
    }

    public func fetchAllClasses() -> [WorkoutClass] {
        guard let dbPtr = db.getDbPointer() else { return [] }

        let query = "SELECT id, title, author, created_at, total_duration_ms, estimated_calories FROM classes ORDER BY created_at DESC;"
        var statement: OpaquePointer?
        var classes: [WorkoutClass] = []

        if sqlite3_prepare_v2(dbPtr, query, -1, &statement, nil) == SQLITE_OK {
            while sqlite3_step(statement) == SQLITE_ROW {
                let idStr = String(cString: sqlite3_column_text(statement, 0))
                let title = String(cString: sqlite3_column_text(statement, 1))
                let author = String(cString: sqlite3_column_text(statement, 2))
                let createdAtStr = String(cString: sqlite3_column_text(statement, 3))
                let durationMs = Int(sqlite3_column_int(statement, 4))
                let calories = sqlite3_column_double(statement, 5)

                let id = UUID(uuidString: idStr) ?? UUID()
                let createdAt = ISO8601DateFormatter().date(from: createdAtStr) ?? Date()

                let segments = fetchSegments(for: id)
                let workoutClass = WorkoutClass(
                    id: id,
                    title: title,
                    author: author,
                    createdAt: createdAt,
                    totalDurationMs: durationMs,
                    estimatedCalories: calories,
                    segments: segments
                )
                classes.append(workoutClass)
            }
        }
        sqlite3_finalize(statement)
        return classes
    }

    public func fetchClass(byId id: UUID) -> WorkoutClass? {
        guard let dbPtr = db.getDbPointer() else { return nil }

        let query = "SELECT id, title, author, created_at, total_duration_ms, estimated_calories FROM classes WHERE id = ? LIMIT 1;"
        var statement: OpaquePointer?
        var workoutClass: WorkoutClass?

        if sqlite3_prepare_v2(dbPtr, query, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, id.uuidString, -1, SQLITE_TRANSIENT)
            if sqlite3_step(statement) == SQLITE_ROW {
                let idStr = String(cString: sqlite3_column_text(statement, 0))
                let title = String(cString: sqlite3_column_text(statement, 1))
                let author = String(cString: sqlite3_column_text(statement, 2))
                let createdAtStr = String(cString: sqlite3_column_text(statement, 3))
                let durationMs = Int(sqlite3_column_int(statement, 4))
                let calories = sqlite3_column_double(statement, 5)

                let classId = UUID(uuidString: idStr) ?? id
                let createdAt = ISO8601DateFormatter().date(from: createdAtStr) ?? Date()
                let segments = fetchSegments(for: classId)

                workoutClass = WorkoutClass(
                    id: classId,
                    title: title,
                    author: author,
                    createdAt: createdAt,
                    totalDurationMs: durationMs,
                    estimatedCalories: calories,
                    segments: segments
                )
            }
        }
        sqlite3_finalize(statement)
        return workoutClass
    }

    public func saveClass(_ workoutClassInput: WorkoutClass) {
        guard let dbPtr = db.getDbPointer() else { return }

        var workoutClass = workoutClassInput
        workoutClass.recalculateTotals()

        try? db.executeWithTransaction {
            let classSql = """
            INSERT OR REPLACE INTO classes (id, title, author, created_at, total_duration_ms, estimated_calories)
            VALUES (?, ?, ?, ?, ?, ?);
            """
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(dbPtr, classSql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, workoutClass.id.uuidString, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(stmt, 2, workoutClass.title, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(stmt, 3, workoutClass.author, -1, SQLITE_TRANSIENT)
                sqlite3_bind_text(stmt, 4, ISO8601DateFormatter().string(from: workoutClass.createdAt), -1, SQLITE_TRANSIENT)
                sqlite3_bind_int(stmt, 5, Int32(workoutClass.totalDurationMs))
                sqlite3_bind_double(stmt, 6, workoutClass.estimatedCalories)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)

            let delSegSql = "DELETE FROM segments WHERE class_id = ?;"
            if sqlite3_prepare_v2(dbPtr, delSegSql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, workoutClass.id.uuidString, -1, SQLITE_TRANSIENT)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)

            let segSql = """
            INSERT INTO segments (id, class_id, order_index, title, music_file_name, duration_ms, base_bpm, playback_rate, intensity_zone)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
            let cueSql = """
            INSERT INTO cues (id, segment_id, offset_ms, posture, target_rpm, resistance_level, message, hand_position, reminders)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """

            for segment in workoutClass.segments {
                var segStmt: OpaquePointer?
                if sqlite3_prepare_v2(dbPtr, segSql, -1, &segStmt, nil) == SQLITE_OK {
                    sqlite3_bind_text(segStmt, 1, segment.id.uuidString, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(segStmt, 2, workoutClass.id.uuidString, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_int(segStmt, 3, Int32(segment.orderIndex))
                    sqlite3_bind_text(segStmt, 4, segment.title, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_text(segStmt, 5, segment.musicFileName, -1, SQLITE_TRANSIENT)
                    sqlite3_bind_int(segStmt, 6, Int32(segment.durationMs))
                    sqlite3_bind_double(segStmt, 7, segment.baseBpm)
                    sqlite3_bind_double(segStmt, 8, segment.playbackRate)
                    sqlite3_bind_int(segStmt, 9, Int32(segment.intensityZone))
                    sqlite3_step(segStmt)
                }
                sqlite3_finalize(segStmt)

                for cue in segment.cues {
                    var cueStmt: OpaquePointer?
                    if sqlite3_prepare_v2(dbPtr, cueSql, -1, &cueStmt, nil) == SQLITE_OK {
                        sqlite3_bind_text(cueStmt, 1, cue.id.uuidString, -1, SQLITE_TRANSIENT)
                        sqlite3_bind_text(cueStmt, 2, segment.id.uuidString, -1, SQLITE_TRANSIENT)
                        sqlite3_bind_int(cueStmt, 3, Int32(cue.offsetMs))
                        sqlite3_bind_text(cueStmt, 4, cue.posture.rawValue, -1, SQLITE_TRANSIENT)
                        sqlite3_bind_int(cueStmt, 5, Int32(cue.targetRpm))
                        sqlite3_bind_text(cueStmt, 6, cue.resistanceLevel, -1, SQLITE_TRANSIENT)
                        sqlite3_bind_text(cueStmt, 7, cue.message, -1, SQLITE_TRANSIENT)
                        sqlite3_bind_int(cueStmt, 8, Int32(cue.handPosition.rawValue))
                        let remindersData = (try? JSONEncoder().encode(cue.reminders)) ?? Data("[]".utf8)
                        let remindersJson = String(data: remindersData, encoding: .utf8) ?? "[]"
                        sqlite3_bind_text(cueStmt, 9, remindersJson, -1, SQLITE_TRANSIENT)
                        sqlite3_step(cueStmt)
                    }
                    sqlite3_finalize(cueStmt)
                }
            }
        }
    }

    public func deleteClass(byId id: UUID) {
        guard let dbPtr = db.getDbPointer() else { return }
        let sql = "DELETE FROM classes WHERE id = ?;"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(dbPtr, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, id.uuidString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    private func fetchSegments(for classId: UUID) -> [WorkoutSegment] {
        guard let dbPtr = db.getDbPointer() else { return [] }

        let query = """
        SELECT id, order_index, title, music_file_name, duration_ms, base_bpm, playback_rate, intensity_zone
        FROM segments WHERE class_id = ? ORDER BY order_index ASC;
        """
        var stmt: OpaquePointer?
        var segments: [WorkoutSegment] = []

        if sqlite3_prepare_v2(dbPtr, query, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, classId.uuidString, -1, SQLITE_TRANSIENT)
            while sqlite3_step(stmt) == SQLITE_ROW {
                let idStr = String(cString: sqlite3_column_text(stmt, 0))
                let orderIndex = Int(sqlite3_column_int(stmt, 1))
                let title = String(cString: sqlite3_column_text(stmt, 2))
                let musicFileName = String(cString: sqlite3_column_text(stmt, 3))
                let durationMs = Int(sqlite3_column_int(stmt, 4))
                let baseBpm = sqlite3_column_double(stmt, 5)
                let rate = sqlite3_column_double(stmt, 6)
                let zone = Int(sqlite3_column_int(stmt, 7))

                let segmentId = UUID(uuidString: idStr) ?? UUID()
                let cues = fetchCues(for: segmentId)

                let seg = WorkoutSegment(
                    id: segmentId,
                    classId: classId,
                    orderIndex: orderIndex,
                    title: title,
                    musicFileName: musicFileName,
                    durationMs: durationMs,
                    baseBpm: baseBpm,
                    playbackRate: rate,
                    intensityZone: zone,
                    cues: cues
                )
                segments.append(seg)
            }
        }
        sqlite3_finalize(stmt)
        return segments
    }

    private func fetchCues(for segmentId: UUID) -> [WorkoutCue] {
        guard let dbPtr = db.getDbPointer() else { return [] }

        let query = """
        SELECT id, offset_ms, posture, target_rpm, resistance_level, message, hand_position, reminders
        FROM cues WHERE segment_id = ? ORDER BY offset_ms ASC;
        """
        var stmt: OpaquePointer?
        var cues: [WorkoutCue] = []

        if sqlite3_prepare_v2(dbPtr, query, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, segmentId.uuidString, -1, SQLITE_TRANSIENT)
            while sqlite3_step(stmt) == SQLITE_ROW {
                let idStr = String(cString: sqlite3_column_text(stmt, 0))
                let offsetMs = Int(sqlite3_column_int(stmt, 1))
                let postureStr = String(cString: sqlite3_column_text(stmt, 2))
                let targetRpm = Int(sqlite3_column_int(stmt, 3))
                let resistance = String(cString: sqlite3_column_text(stmt, 4))
                let message = String(cString: sqlite3_column_text(stmt, 5))
                let handPositionRaw = Int(sqlite3_column_int(stmt, 6))

                var reminders: [String] = []
                if let remindersText = sqlite3_column_text(stmt, 7) {
                    let jsonStr = String(cString: remindersText)
                    if let data = jsonStr.data(using: .utf8),
                       let decoded = try? JSONDecoder().decode([String].self, from: data) {
                        reminders = decoded
                    }
                }

                let cueId = UUID(uuidString: idStr) ?? UUID()
                let posture = PostureType(rawValue: postureStr) ?? .seatedFlat
                let handPosition = HandPosition(rawValue: handPositionRaw) ?? posture.defaultHandPosition

                let cue = WorkoutCue(
                    id: cueId,
                    segmentId: segmentId,
                    offsetMs: offsetMs,
                    posture: posture,
                    targetRpm: targetRpm,
                    resistanceLevel: resistance,
                    message: message,
                    handPosition: handPosition,
                    reminders: reminders
                )
                cues.append(cue)
            }
        }
        sqlite3_finalize(stmt)
        return cues
    }

    public func fetchWaveform(for fileName: String) -> (samples: [Float], durationMs: Int, bpm: Double)? {
        guard let dbPtr = db.getDbPointer() else { return nil }
        let sql = "SELECT samples_blob, sample_count, duration_ms, calculated_bpm FROM waveform_cache WHERE file_name = ? LIMIT 1;"
        var stmt: OpaquePointer?
        var result: (samples: [Float], durationMs: Int, bpm: Double)?

        if sqlite3_prepare_v2(dbPtr, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, fileName, -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW {
                if let blobPtr = sqlite3_column_blob(stmt, 0) {
                    let byteCount = Int(sqlite3_column_bytes(stmt, 0))
                    let count = byteCount / MemoryLayout<Float>.size
                    let buffer = blobPtr.bindMemory(to: Float.self, capacity: count)
                    let samples = Array(UnsafeBufferPointer(start: buffer, count: count))
                    let durationMs = Int(sqlite3_column_int(stmt, 2))
                    let bpm = sqlite3_column_double(stmt, 3)
                    result = (samples: samples, durationMs: durationMs > 0 ? durationMs : 300_000, bpm: bpm > 0 ? bpm : 128.0)
                }
            }
        }
        sqlite3_finalize(stmt)
        return result
    }

    public func saveWaveform(for fileName: String, samples: [Float], durationMs: Int, bpm: Double) {
        guard let dbPtr = db.getDbPointer() else { return }
        let sql = """
        INSERT OR REPLACE INTO waveform_cache (file_name, samples_blob, sample_count, duration_ms, calculated_bpm)
        VALUES (?, ?, ?, ?, ?);
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(dbPtr, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, fileName, -1, SQLITE_TRANSIENT)
            let byteCount = samples.count * MemoryLayout<Float>.size
            samples.withUnsafeBytes { rawBytes in
                sqlite3_bind_blob(stmt, 2, rawBytes.baseAddress, Int32(byteCount), nil)
            }
            sqlite3_bind_int(stmt, 3, Int32(samples.count))
            sqlite3_bind_int(stmt, 4, Int32(durationMs))
            sqlite3_bind_double(stmt, 5, bpm)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    private func seedInitialDataIfNeeded() {
        let existing = fetchAllClasses()
        if existing.isEmpty {
            var demoClass = WorkoutClass(
                id: UUID(uuidString: "770E8400-E29B-41D4-A716-446655440001")!,
                title: "45min 燃脂間歇爬坡示範課",
                author: "Coach Tony",
                createdAt: Date(),
                totalDurationMs: 2700000,
                estimatedCalories: 450.0,
                segments: []
            )

            let seg1 = WorkoutSegment(
                id: UUID(),
                classId: demoClass.id,
                orderIndex: 0,
                title: "1. 熱身與節奏平路 (Warm-Up Flat)",
                musicFileName: "warmup_groove.mp3",
                durationMs: 300_000,
                baseBpm: 120.0,
                playbackRate: 1.0,
                intensityZone: 2,
                cues: [
                    WorkoutCue(offsetMs: 0, posture: .seatedFlat, targetRpm: 85, resistanceLevel: "LEVEL 3", message: "坐姿平路，輕鬆踩踏建立踏頻節奏"),
                    WorkoutCue(offsetMs: 120_000, posture: .standingFlat, targetRpm: 80, resistanceLevel: "LEVEL 4", message: "起立站姿平路，拉長呼吸與核心穩定")
                ]
            )

            let seg2 = WorkoutSegment(
                id: UUID(),
                classId: demoClass.id,
                orderIndex: 1,
                title: "2. 連續重爬坡巡航 (Hill Climbing)",
                musicFileName: "climb_anthem.mp3",
                durationMs: 420_000,
                baseBpm: 130.0,
                playbackRate: 1.0,
                intensityZone: 4,
                cues: [
                    WorkoutCue(offsetMs: 0, posture: .seatedClimb, targetRpm: 68, resistanceLevel: "LEVEL 6", message: "加兩圈重阻力，深沉坐姿爬坡"),
                    WorkoutCue(offsetMs: 180_000, posture: .standingClimb, targetRpm: 60, resistanceLevel: "LEVEL 8", message: "起立站姿重爬坡，用全身重量下壓踩動！")
                ]
            )

            let seg3 = WorkoutSegment(
                id: UUID(),
                classId: demoClass.id,
                orderIndex: 2,
                title: "3. 爆發高轉速衝刺 (Sprint Interval)",
                musicFileName: "sprint_fire.mp3",
                durationMs: 240_000,
                baseBpm: 140.0,
                playbackRate: 1.0,
                intensityZone: 5,
                cues: [
                    WorkoutCue(offsetMs: 0, posture: .seatedFlat, targetRpm: 90, resistanceLevel: "LEVEL 4", message: "預備區間，呼吸吸飽加速中"),
                    WorkoutCue(offsetMs: 60_000, posture: .sprint, targetRpm: 115, resistanceLevel: "LEVEL 5", message: "🔥 全力衝刺 30 秒！踩破 110 RPM！"),
                    WorkoutCue(offsetMs: 150_000, posture: .recovery, targetRpm: 75, resistanceLevel: "LEVEL 2", message: "放鬆深呼吸，緩和踩踏")
                ]
            )

            demoClass.segments = [seg1, seg2, seg3]
            demoClass.recalculateTotals()
            saveClass(demoClass)
        }
    }
}
