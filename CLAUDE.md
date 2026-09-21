# CLAUDE.md

## 專案

FitnessRider — 飛輪課表編排與課堂中控，雙原生（`android/` Kotlin+Compose+Media3、`ios/` Swift+SwiftUI）。

- 業務規格權威來源：`spec.md`
- 技術架構與環境：`Agent.md`
- `original/` 是 2021 年的 Java 舊版，**唯讀參考**，不要修改、不要編譯。

## 通則

- UI 文案一律繁體中文。
- **Android 與 iOS 必須維持功能對等**。任一平台的行為改動，另一平台同一份工作要一起改完，不得只做一邊。
- 顏色用 `theme/Color.kt`（Android）與 `FitnessRiderTheme`（iOS）既有 token，不要寫死色碼。
- 非顯而易見的邏輯要留一個可執行的檢查：Android 加進 `android/app/src/test/java/com/fitnessrider/FitnessRiderAndroidTest.kt`，iOS 加進 `ios/FitnessRiderTests/FitnessRiderTests.swift`。不要引入新測試框架。
- 刻意的簡化用 `ponytail:` 註解標註，並寫明上限與升級路徑。
- 不要為了「以後可能需要」而加抽象層、介面、設定項。最短可行的 diff 優先。

## 已知落差（尚未排程）

- **iOS 編輯器的段落試聽沒有聲音。** `ClassEditorView.startPreview` 只是個推進播放頭的 `Timer`，整個檔案沒有任何播放器；Android 同一個位置用的是真的 ExoPlayer。兩平台體驗不對等，但不在三層計畫範圍內，尚未決定要不要修。
- **匯入失敗時已寫入一半的檔案沒有清掉**，會在 Music 目錄留下截斷檔並佔住檔名。適合併進 Layer 3 的檔案管理一起處理。

## 產品決策（不要當成 bug 修掉）

- **`playbackRate` 不計入總時長與預估消耗。** 總時長一律是各段落 `durationMs` 的總和，卡路里一律依 `durationMs` × `intensityZone` 費率推算，兩者都忽略播放速率。所以 5 分鐘的曲子設成 0.85x 時，實際騎乘約 5:53，但編輯器頂端仍顯示 05:00 —— 這是刻意的，舊版與新版、Android 與 iOS 行為一致。不要「修正」成用有效播放時間計算。

## 進行中：音樂匯入體驗重build（三層，依序完成）

舊版（`original/`）的「新增段落」＝直接開 app 內建音樂瀏覽器，可記資料夾、搜尋、排序、試聽、**多選**，確認後一次建立 N 個段落，段落標題＝曲名、長度＝曲長
（見 `original/app/src/main/java/com/coretronic/vcoach/fitnesscenter/FragDialogSelectMusic.java`
與 `ActivityClassEditor.java:854`、`:1188-1199`）。

新版退化成「建空段落 → 選中 → 開系統檔案選擇器 → 單選 → 重複 N 次」，客戶回報極不直覺。以下三層依序修復，**未完成前一層不要動下一層**。

### Layer 1 — 修 bug + 多選（不動架構）

目標：把「新增→選中→匯入→重複 N 次」壓成「按一次→多選→完成」。

1. **寫回時長**：`WaveformAnalyzer` 已算出 `durationMs` 卻被丟棄，段落永遠停在預設 `300_000`（5:00），導致總時長、波形比例、試聽結束點全錯。
   - Android `ui/editor/ClassEditorScreen.kt:122-129` 目前只寫回 `result.bpm`。
   - iOS `Views/ClassEditor/ClassEditorView.swift:489` 的 callback 連 duration 都沒回傳，需一併補上。
2. **段落標題帶入曲名**：匯入時以檔名（去副檔名）當段落標題，取代「段落 N」。
3. **多選**：
   - Android `ClassEditorScreen.kt:218` 的 `OpenDocument()` 改為 `OpenMultipleDocuments()`。
   - iOS `ClassEditorView.swift:165` 已是 `allowsMultipleSelection: true`，但 `:529` 的 `guard let firstUrl = urls.first` 把其餘檔案無聲丟棄，需改為處理整個 `urls`。
   - 選 N 首 → 自動建立 N 個段落（沿用舊版 `ActivityClassEditor.java:1188` 的行為）。
4. **「新增段落」直接開音樂選擇器**：空段落對教練沒有意義。Android `ClassEditorScreen.kt:296`、iOS `ClassEditorView.swift:447`。
5. **錯誤要看得見**：匯入失敗目前只有 `Log.e`（Android `:105`）與 `print`（iOS `:545`）。改為 Snackbar / Alert。
6. **檔名碰撞**：目前以顯示名稱直接覆寫，兩首不同的 `track.mp3` 會互相蓋掉、偷換別的段落音樂。碰撞時加 `_1`、`_2` 後綴。
7. **未選中段落時按匯入**：Android `if (uri != null && activeSegment != null)` 會讓使用者選完檔案後畫面毫無反應。改為自動建立新段落。

### Layer 2 — app 內音樂庫

- 新增 `MusicLibraryScreen`（Android）／`MusicLibraryView`（iOS），列出 `filesDir/Music`（Android）與 `SQLiteDatabase.shared.musicDirectoryURL`（iOS）。
- 顯示曲名、時長、BPM。**BPM 與波形已由 `WaveformAnalyzer` 算過並經 `saveWaveform` 存起來，直接讀快取，不要重算。**
- 可搜尋、可試聽。Android 沿用編輯器既有的 ExoPlayer。iOS 用 `AVAudioPlayer`（AVFoundation 內建，非新依賴）——
  注意編輯器的 `startPreview` 只是個推進 `previewPlayheadMs` 的 `Timer`，**沒有實際聲音**，沒有現成播放器可沿用。
- 段落指定音樂時開這個列表，不再開系統檔案選擇器；SAF／`fileImporter` 只保留在「＋匯入新檔」一個入口。
- 重用已匯入曲目不得再產生任何檔案複製。

### Layer 3 — 資料夾記憶

對應舊版的 `MusicUtility.getMusicPath()` ＋ `GetMusicInfoTask`（含子資料夾掃描）。

- Android：`OpenDocumentTree()` ＋ `takePersistableUriPermission`，記住教練的音樂資料夾，之後直接列出整個資料夾內容。
- iOS：`.fileImporter` 選資料夾 ＋ security-scoped bookmark 持久化。
- 資料夾內容以串流方式列出，不要一次把整個資料夾複製進 app 儲存空間。
