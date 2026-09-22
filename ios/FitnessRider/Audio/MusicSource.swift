import Foundation

/// 統一處理兩種音樂來源，讓播放／波形分析／匯出等消費端不用各自判斷：
/// - Layer 2：複製進 `Music/` 目錄的檔名（純檔名字串）。
/// - Layer 3：教練指定的外部資料夾內容，一律以 `"extfolder://" + 相對路徑` 存在
///   `WorkoutSegment.musicFileName`，天然可以跟本機檔名區分，不需要另外加欄位標記來源。
enum MusicSource {
    static let externalPrefix = "extfolder://"

    static func isExternal(_ musicFileName: String) -> Bool {
        musicFileName.hasPrefix(externalPrefix)
    }

    /// 在需要實際存取檔案內容的期間執行 [body]（開檔、讀取、建立 player 都要在這個閉包裡做）。
    /// 外部資料夾的 security-scoped 存取視窗只在 [body] 執行期間有效，`body` 回傳後就會關閉，
    /// 所以任何需要繼續讀取檔案內容的物件（`AVAudioFile`、`AVAudioPlayer`...）都要在 [body]
    /// 裡完成初始化——一旦系統把檔案描述子開好，關閉存取視窗不影響後續讀取。
    ///
    /// 本機 `Music/` 目錄檔案不需要 security scope，直接組出 URL 執行 [body]。
    /// 資料夾授權被撤銷、bookmark 失效、或檔案已被搬走／刪除時回傳 nil，呼叫端視同「找不到檔案」處理。
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

    /// iCloud Drive 開著「最佳化儲存空間」時，還沒下載到本機的曲目在邏輯路徑上是不存在的，
    /// 磁碟上只有一個隱藏的 `.<檔名>.icloud` 預留位置。找不到檔案時順手觸發下載，
    /// 本次呼叫仍照既有規則回傳 nil，由呼叫端當作「找不到檔案」優雅降級（CLAUDE.md Layer 3）。
    private static func startICloudDownloadIfPlaceholder(at url: URL) {
        let placeholder = url
            .deletingLastPathComponent()
            .appendingPathComponent("." + url.lastPathComponent + ".icloud")
        guard FileManager.default.fileExists(atPath: placeholder.path) else { return }
        try? FileManager.default.startDownloadingUbiquitousItem(at: url)
    }

    /// 觸發 iCloud 下載並等到檔案真的落地（最多 [timeout] 秒）。已經在本機的曲目直接回傳 true。
    /// 供「教練主動按下去、而且畫面上有等待指示」的路徑使用（選取曲目建立段落、試聽）。
    ///
    /// ponytail: 固定間隔輪詢而不是 NSMetadataQuery —— 一次只等選取的那幾首、教練是主動等待的，
    /// 不需要即時進度回報。要在畫面上顯示下載百分比時再換成 NSMetadataQuery。
    static func ensureAvailable(for musicFileName: String, timeout: TimeInterval = 60) async -> Bool {
        // fileExists 走 withResolvedFileURL，找不到檔案時本身就會觸發下載。
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
