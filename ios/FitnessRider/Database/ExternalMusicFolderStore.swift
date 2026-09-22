import Foundation

public final class ExternalMusicFolderStore: Sendable {
    public static let shared = ExternalMusicFolderStore()

    private static let bookmarkKey = "externalMusicFolderBookmark"
    private static let includeSubdirectoriesKey = "externalMusicFolderIncludeSubdirectories"

    private init() {}

    public func isFolderConfigured(defaults: UserDefaults = .standard) -> Bool {
        defaults.data(forKey: Self.bookmarkKey) != nil
    }

    public func persist(folderURL: URL, defaults: UserDefaults = .standard) throws {
        let didAccess = folderURL.startAccessingSecurityScopedResource()
        defer { if didAccess { folderURL.stopAccessingSecurityScopedResource() } }
        let bookmark = try folderURL.bookmarkData()
        defaults.set(bookmark, forKey: Self.bookmarkKey)
    }

    public func clearFolder(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: Self.bookmarkKey)
    }

    public func includeSubdirectories(defaults: UserDefaults = .standard) -> Bool {
        (defaults.object(forKey: Self.includeSubdirectoriesKey) as? Bool) ?? true
    }

    public func setIncludeSubdirectories(_ include: Bool, defaults: UserDefaults = .standard) {
        defaults.set(include, forKey: Self.includeSubdirectoriesKey)
    }

    @discardableResult
    public func withFileAccess<T>(
        relativePath: String,
        defaults: UserDefaults = .standard,
        _ body: (URL) -> T?
    ) -> T? {
        guard let bookmark = defaults.data(forKey: Self.bookmarkKey) else { return nil }
        var isStale = false
        guard let base = try? URL(
            resolvingBookmarkData: bookmark,
            options: [],
            relativeTo: nil,
            bookmarkDataIsStale: &isStale
        ) else {
            return nil
        }
        guard base.startAccessingSecurityScopedResource() else { return nil }
        defer { base.stopAccessingSecurityScopedResource() }

        if isStale, let refreshed = try? base.bookmarkData() {
            defaults.set(refreshed, forKey: Self.bookmarkKey)
        }

        let target = relativePath.isEmpty
            ? base
            : relativePath.split(separator: "/").reduce(base) { $0.appendingPathComponent(String($1)) }
        return body(target)
    }
}

struct ExternalMusicEntry: Identifiable, Equatable {
    var id: String { relativePath }
    let relativePath: String
    let displayName: String
    var isCloudPlaceholder: Bool = false
}

private let audioFileExtensions: Set<String> = ["mp3", "m4a", "aac", "wav", "flac", "ogg", "wma"]

func isAudioFileName(_ name: String) -> Bool {
    let ext = (name as NSString).pathExtension.lowercased()
    return audioFileExtensions.contains(ext)
}

func iCloudPlaceholderName(for fileName: String) -> String? {
    let suffix = ".icloud"
    guard fileName.hasPrefix("."), fileName.hasSuffix(suffix), fileName.count > suffix.count + 1 else {
        return nil
    }
    return String(fileName.dropFirst().dropLast(suffix.count))
}

func listExternalMusicEntries(baseURL: URL, includeSubdirectories: Bool) -> [ExternalMusicEntry] {
    let fm = FileManager.default
    let basePath = baseURL.standardizedFileURL.path

    func relativePath(for url: URL) -> String {
        let path = url.standardizedFileURL.path
        guard path.hasPrefix(basePath) else { return url.lastPathComponent }
        var relative = String(path.dropFirst(basePath.count))
        if relative.hasPrefix("/") { relative.removeFirst() }
        return relative
    }

    func makeEntry(_ fileURL: URL) -> ExternalMusicEntry? {
        let rawName = fileURL.lastPathComponent
        if let realName = iCloudPlaceholderName(for: rawName) {
            guard isAudioFileName(realName) else { return nil }
            let rawRelative = relativePath(for: fileURL)
            let logicalRelative = String(rawRelative.dropLast(rawName.count)) + realName
            return ExternalMusicEntry(
                relativePath: logicalRelative,
                displayName: realName,
                isCloudPlaceholder: true
            )
        }
        guard !rawName.hasPrefix("."), isAudioFileName(rawName) else { return nil }
        return ExternalMusicEntry(relativePath: relativePath(for: fileURL), displayName: rawName)
    }

    var results: [ExternalMusicEntry] = []

    if includeSubdirectories {
        guard let enumerator = fm.enumerator(
            at: baseURL,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: []
        ) else {
            return []
        }
        for case let fileURL as URL in enumerator {
            let isDirectory = (try? fileURL.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory ?? false
            if isDirectory {
                if fileURL.lastPathComponent.hasPrefix(".") { enumerator.skipDescendants() }
                continue
            }
            if let entry = makeEntry(fileURL) { results.append(entry) }
        }
    } else {
        guard let items = try? fm.contentsOfDirectory(
            at: baseURL,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: []
        ) else {
            return []
        }
        for fileURL in items {
            let isDirectory = (try? fileURL.resourceValues(forKeys: [.isDirectoryKey]))?.isDirectory ?? false
            guard !isDirectory else { continue }
            if let entry = makeEntry(fileURL) { results.append(entry) }
        }
    }

    var deduped: [String: ExternalMusicEntry] = [:]
    for entry in results {
        if let existing = deduped[entry.relativePath], !existing.isCloudPlaceholder { continue }
        deduped[entry.relativePath] = entry
    }

    return deduped.values.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
}
