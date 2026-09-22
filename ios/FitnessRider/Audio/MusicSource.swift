import Foundation

enum MusicSource {
    static let externalPrefix = "extfolder://"

    static func isExternal(_ musicFileName: String) -> Bool {
        musicFileName.hasPrefix(externalPrefix)
    }

    @discardableResult
    static func withResolvedFileURL<T>(for musicFileName: String, _ body: (URL) -> T?) -> T? {
        guard !musicFileName.isEmpty else { return nil }
        if isExternal(musicFileName) {
            let relativePath = String(musicFileName.dropFirst(externalPrefix.count))
            return ExternalMusicFolderStore.shared.withFileAccess(relativePath: relativePath) { url in
                guard FileManager.default.fileExists(atPath: url.path) else {
                    startICloudDownloadIfPlaceholder(at: url)
                    return nil
                }
                return body(url)
            }
        } else {
            let url = SQLiteDatabase.shared.musicDirectoryURL.appendingPathComponent(musicFileName)
            guard FileManager.default.fileExists(atPath: url.path) else { return nil }
            return body(url)
        }
    }

    static func fileExists(for musicFileName: String) -> Bool {
        withResolvedFileURL(for: musicFileName) { _ in true } ?? false
    }

    private static func startICloudDownloadIfPlaceholder(at url: URL) {
        let placeholder = url
            .deletingLastPathComponent()
            .appendingPathComponent("." + url.lastPathComponent + ".icloud")
        guard FileManager.default.fileExists(atPath: placeholder.path) else { return }
        try? FileManager.default.startDownloadingUbiquitousItem(at: url)
    }

    static func ensureAvailable(for musicFileName: String, timeout: TimeInterval = 60) async -> Bool {
        if fileExists(for: musicFileName) { return true }
        guard isExternal(musicFileName) else { return false }

        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            try? await Task.sleep(nanoseconds: 500_000_000)
            if fileExists(for: musicFileName) { return true }
        }
        return false
    }
}
