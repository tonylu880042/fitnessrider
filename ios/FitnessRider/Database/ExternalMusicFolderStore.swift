import Foundation

/// Layer 3「資料夾記憶」：對應舊版 `MusicUtility.getMusicPath()` / `isIncludeSubdirectories()`。
/// 用 security-scoped bookmark 記住教練用 `.fileImporter` 選定的音樂資料夾，並持久化
/// 「含子資料夾」開關。只用 `UserDefaults` 存一個 bookmark `Data` 跟一個 `Bool`，
/// 沒有新增資料庫欄位／表格，也沒有把資料夾內容複製進來。
///
/// `defaults` 參數讓測試可以注入獨立的 `UserDefaults` suite，不會互相汙染、也不會動到
/// 真正的 App 設定（比照 `VersionLifecycleManager` 既有的測試模式）。
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

    // 舊版 MusicUtility.isIncludeSubdirectories 預設值就是 true，這裡沿用。
    public func includeSubdirectories(defaults: UserDefaults = .standard) -> Bool {
        (defaults.object(forKey: Self.includeSubdirectoriesKey) as? Bool) ?? true
    }

    public func setIncludeSubdirectories(_ include: Bool, defaults: UserDefaults = .standard) {
        defaults.set(include, forKey: Self.includeSubdirectoriesKey)
    }

    /// 在資料夾（或資料夾內某個相對路徑）存取視窗開著的期間執行 [body]。[relativePath] 傳空字串
    /// 代表要存取資料夾本身（例如列出內容）。bookmark 不存在／已失效／資料夾被移除時回傳 nil，
    /// 呼叫端視同「資料夾存取已失效」處理，不會讓 App 崩潰。
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

/// 資料夾內一筆音樂檔的串流列表項目：只帶相對路徑與檔名，時長/BPM 留到教練實際選取後才分析快取。
struct ExternalMusicEntry: Identifiable, Equatable {
    var id: String { relativePath }
    let relativePath: String
    let displayName: String
    /// 這首歌在 iCloud Drive 上還沒下載到本機（磁碟上只有隱藏的 `.<檔名>.icloud` 預留位置）。
    /// [relativePath] 一律是「下載完成後」的邏輯路徑，所以 `musicFileName` 不受下載狀態影響。
    var isCloudPlaceholder: Bool = false
}

private let audioFileExtensions: Set<String> = ["mp3", "m4a", "aac", "wav", "flac", "ogg", "wma"]

/// 依副檔名判斷是否為音樂檔，用來在資料夾掃描時過濾非音樂項目。
func isAudioFileName(_ name: String) -> Bool {
    let ext = (name as NSString).pathExtension.lowercased()
    return audioFileExtensions.contains(ext)
}

/// iCloud Drive 開著「最佳化 iPad 儲存空間」時，還沒下載到本機的檔案在磁碟上只是一個隱藏的
/// 預留位置：`Song.mp3` 會變成 `.Song.mp3.icloud`。回傳它代表的真實檔名，不是預留位置則回傳 nil。
///
/// 這是教練把 iCloud 雲端硬碟資料夾綁成「音樂資料夾」時，曲目整批消失的根因 ——
/// 列表掃描開著 `.skipsHiddenFiles`，會把所有還沒下載的歌一起濾掉。
func iCloudPlaceholderName(for fileName: String) -> String? {
    let suffix = ".icloud"
    guard fileName.hasPrefix("."), fileName.hasSuffix(suffix), fileName.count > suffix.count + 1 else {
        return nil
    }
    return String(fileName.dropFirst().dropLast(suffix.count))
}

/// 串流列出 [baseURL] 資料夾內容：用 `FileManager` 的 enumerator／`contentsOfDirectory`
/// 逐一取得檔名，不開啟、不下載、更不複製任何音樂檔案本體（對應 CLAUDE.md Layer 3
/// 「以串流方式列出，不要一次把整個資料夾複製進 app 儲存空間」）。
///
/// [includeSubdirectories] 開啟時遞迴掃描子資料夾，對應舊版「含子資料夾」選項。
/// 回傳的 [ExternalMusicEntry.relativePath] 是相對 [baseURL] 的路徑，用來組成
/// `"extfolder://" + relativePath` 形式的 `musicFileName`。
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

    /// 把磁碟上的一筆檔案換算成列表項目：iCloud 預留位置換算回它代表的真實檔名與邏輯路徑，
    /// 其餘隱藏檔一律略過，非音樂檔回傳 nil。
    func makeEntry(_ fileURL: URL) -> ExternalMusicEntry? {
        let rawName = fileURL.lastPathComponent
        if let realName = iCloudPlaceholderName(for: rawName) {
            guard isAudioFileName(realName) else { return nil }
            // 預留位置的路徑是 `Sub/.Song.mp3.icloud`，換算成下載完成後的 `Sub/Song.mp3`，
            // 讓段落存下來的 musicFileName 從頭到尾都是同一個，不會因為下載狀態改變而失效。
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

    // 不能開 `.skipsHiddenFiles`：iCloud 尚未下載的檔案本身就是隱藏的預留位置檔，
    // 開著會讓「還沒下載的歌」整批從列表消失。改成在 makeEntry 裡手動略過隱藏項目。
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

    // 下載進行中的那幾秒，同一首歌可能同時有 `Song.mp3` 與 `.Song.mp3.icloud` 兩筆，
    // 以已經落地的那筆為準，避免列表出現重複項目。
    var deduped: [String: ExternalMusicEntry] = [:]
    for entry in results {
        if let existing = deduped[entry.relativePath], !existing.isCloudPlaceholder { continue }
        deduped[entry.relativePath] = entry
    }

    return deduped.values.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
}
