import Foundation
import SQLite3

public final class SQLiteDatabase: @unchecked Sendable {
    public static let shared = SQLiteDatabase()

    private var dbPointer: OpaquePointer?
    private let lock = NSLock()

    public var databaseURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("FitnessRider.sqlite")
    }

    public var musicDirectoryURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let musicDir = docs.appendingPathComponent("Music", isDirectory: true)
        if !FileManager.default.fileExists(atPath: musicDir.path) {
            try? FileManager.default.createDirectory(at: musicDir, withIntermediateDirectories: true)
        }
        return musicDir
    }

    private init() {
        openDatabase()
        createTables()
    }

    deinit {
        closeDatabase()
    }

    private func openDatabase() {
        lock.lock()
        defer { lock.unlock() }

        let path = databaseURL.path
        if sqlite3_open(path, &dbPointer) == SQLITE_OK {
            _ = executeRaw("PRAGMA journal_mode=WAL;")
            _ = executeRaw("PRAGMA foreign_keys=ON;")
        } else {
            print("Failed to open SQLite database at: \(path)")
        }
    }

    public func closeDatabase() {
        lock.lock()
        defer { lock.unlock() }

        if let db = dbPointer {
            sqlite3_close(db)
            dbPointer = nil
        }
    }

    public func reopen() {
        closeDatabase()
        openDatabase()
    }

    @discardableResult
    public func executeRaw(_ sql: String) -> Bool {
        guard let db = dbPointer else { return false }
        var errMsg: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(db, sql, nil, nil, &errMsg) != SQLITE_OK {
            if let err = errMsg {
                print("SQLite exec error: \(String(cString: err)) in [\(sql)]")
                sqlite3_free(errMsg)
            }
            return false
        }
        return true
    }

    public func executeWithTransaction<T>(_ block: () throws -> T) throws -> T {
        lock.lock()
        defer { lock.unlock() }

        _ = executeRaw("BEGIN IMMEDIATE TRANSACTION;")
        do {
            let result = try block()
            _ = executeRaw("COMMIT;")
            return result
        } catch {
            _ = executeRaw("ROLLBACK;")
            throw error
        }
    }

    private func createTables() {
        let schema = """
        CREATE TABLE IF NOT EXISTS classes (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            author TEXT NOT NULL,
            created_at TEXT NOT NULL,
            total_duration_ms INTEGER NOT NULL DEFAULT 0,
            estimated_calories REAL NOT NULL DEFAULT 0.0
        );

        CREATE TABLE IF NOT EXISTS segments (
            id TEXT PRIMARY KEY,
            class_id TEXT NOT NULL,
            order_index INTEGER NOT NULL DEFAULT 0,
            title TEXT NOT NULL,
            music_file_name TEXT NOT NULL,
            duration_ms INTEGER NOT NULL DEFAULT 0,
            base_bpm REAL NOT NULL DEFAULT 128.0,
            playback_rate REAL NOT NULL DEFAULT 1.0,
            intensity_zone INTEGER NOT NULL DEFAULT 2,
            FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS cues (
            id TEXT PRIMARY KEY,
            segment_id TEXT NOT NULL,
            offset_ms INTEGER NOT NULL DEFAULT 0,
            posture TEXT NOT NULL,
            target_rpm INTEGER NOT NULL DEFAULT 80,
            resistance_level TEXT NOT NULL,
            message TEXT NOT NULL,
            hand_position INTEGER NOT NULL DEFAULT 1,
            reminders TEXT NOT NULL DEFAULT '[]',
            FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS waveform_cache (
            file_name TEXT PRIMARY KEY,
            samples_blob BLOB NOT NULL,
            sample_count INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL,
            calculated_bpm REAL NOT NULL
        );
        """
        _ = executeRaw(schema)

        _ = executeRaw("ALTER TABLE cues ADD COLUMN hand_position INTEGER NOT NULL DEFAULT 1;")
        _ = executeRaw("ALTER TABLE cues ADD COLUMN reminders TEXT NOT NULL DEFAULT '[]';")
    }

    public func createHotBackup(to destinationURL: URL) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard let sourceDb = dbPointer else { return false }

        try? FileManager.default.removeItem(at: destinationURL)

        var destDb: OpaquePointer?
        guard sqlite3_open(destinationURL.path, &destDb) == SQLITE_OK, let dest = destDb else {
            return false
        }
        defer { sqlite3_close(dest) }

        guard let backup = sqlite3_backup_init(dest, "main", sourceDb, "main") else {
            return false
        }

        let stepResult = sqlite3_backup_step(backup, -1)
        sqlite3_backup_finish(backup)

        return stepResult == SQLITE_DONE
    }

    public func restoreDatabase(from sourceURL: URL) throws {
        lock.lock()
        defer { lock.unlock() }

        let fileHandle = try FileHandle(forReadingFrom: sourceURL)
        let headerData = fileHandle.readData(ofLength: 16)
        try fileHandle.close()
        guard let headerString = String(data: headerData, encoding: .utf8),
              headerString.hasPrefix("SQLite format 3") else {
            throw NSError(domain: "SQLiteDatabase", code: -1, userInfo: [NSLocalizedDescriptionKey: "選取的檔案不是有效的 SQLite 備份檔"])
        }

        if let db = dbPointer {
            sqlite3_close(db)
            dbPointer = nil
        }

        let destURL = databaseURL
        try? FileManager.default.removeItem(at: destURL)
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: destURL.path + "-wal"))
        try? FileManager.default.removeItem(at: URL(fileURLWithPath: destURL.path + "-shm"))

        try FileManager.default.copyItem(at: sourceURL, to: destURL)

        if sqlite3_open(destURL.path, &dbPointer) == SQLITE_OK {
            _ = executeRaw("PRAGMA journal_mode=WAL;")
            _ = executeRaw("PRAGMA foreign_keys=ON;")
        } else {
            throw NSError(domain: "SQLiteDatabase", code: -2, userInfo: [NSLocalizedDescriptionKey: "無法重新開啟還原後的資料庫"])
        }
    }

    public func getDbPointer() -> OpaquePointer? {
        return dbPointer
    }
}
