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
                FileManager.default.fileExists(atPath: url.path) ? body(url) : nil
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
}
