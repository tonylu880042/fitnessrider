import Foundation

public final class SQLiteBackupService: Sendable {
    public static let shared = SQLiteBackupService()

    private let db = SQLiteDatabase.shared

    private init() {}

    public func exportBackupFile() -> URL? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmm"
        let timestamp = formatter.string(from: Date())
        let fileName = "FitnessRider_Backup_\(timestamp).sqlite"

        let tempDir = FileManager.default.temporaryDirectory
        let exportURL = tempDir.appendingPathComponent(fileName)

        if db.createHotBackup(to: exportURL) {
            return exportURL
        }
        return nil
    }

    public func restoreFromFile(at fileURL: URL) throws {
        try db.restoreDatabase(from: fileURL)
    }
}
