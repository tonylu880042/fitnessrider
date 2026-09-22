# CLAUDE.md

## 專案

FitnessRider — 飛輪課表編排與課堂中控，雙原生（`android/` Kotlin+Compose+Media3、`ios/` Swift+SwiftUI）。

- 業務規格權威來源：`spec.md`
- 技術架構與環境：`Agent.md`
- 推廣培訓代碼政策與年度維護：`docs/PROMO_CODES.md`（當前 2026: `26FR-NR`，過期提醒工具 `tools/check_promo_code.sh`）
- `original/` 是 2021 年的 Java 舊版，**唯讀參考**，不要修改、不要編譯。

## 通則

- UI 文案一律繁體中文。
- **Android 與 iOS 必須維持功能對等**。任一平台的行為改動，另一平台同一份工作要一起改完，不得只做一邊。
- 顏色用 `theme/Color.kt`（Android）與 `FitnessRiderTheme`（iOS）既有 token，不要寫死色碼。
- 非顯而易見的邏輯要留一個可執行的檢查：Android 加進 `android/app/src/test/java/com/fitnessrider/FitnessRiderAndroidTest.kt`，iOS 加進 `ios/FitnessRiderTests/FitnessRiderTests.swift`。不要引入新測試框架。
- 刻意的簡化用 `ponytail:` 註解標註，並寫明上限與升級路徑。
- 不要為了「以後可能需要」而加抽象層、介面、設定項。最短可行的 diff 優先。

## 已知落差（已修復對齊）

- **iOS 編輯器的段落試聽（已對齊）**：`ClassEditorView.startPreview` 已全面引入 `AVAudioPlayer` 真實放音，支援變速不變調、即時波形 Scrubbing 與結束自動重置，與 Android 的 `ExoPlayer` 達成完全功能對等。
- **雙平台曲目切換平滑轉場 (Crossfade)（已對齊）**：雙端均採用雙軌／雙 Deck 架構（iOS `AVAudioEngine` 雙 `AudioDeck`；Android 雙 `ExoPlayer`），實現 1~3 秒等能量（Equal-Power: $\cos$ fade-out, $\sin$ fade-in）平滑交錯淡入淡出，並與「段落結束自動暫停 (Auto-Pause)」嚴格互斥，雙端設定提供 0s / 1s / 2s / 3s 設定。

## 產品決策（不要當成 bug 修掉）

- **`playbackRate` 不計入總時長與預估消耗。** 總時長一律是各段落 `durationMs` 的總和，卡路里一律依 `durationMs` × `intensityZone` 費率推算，兩者都忽略播放速率。所以 5 分鐘的曲子設成 0.85x 時，實際騎乘約 5:53，但編輯器頂端仍顯示 05:00 —— 這是刻意的，舊版與新版、Android 與 iOS 行為一致。不要「修正」成用有效播放時間計算。

- **商業模型（授權／試用）**：
  1. **基礎試用 7 天**，從裝置首次啟動起算（`BASE_TRIAL_DAYS`，權威來源 `promo.properties`；Android `build.gradle.kts` 直接讀取注入 `BuildConfig.LIFECYCLE_DAYS`，iOS 用 `VersionLifecycleManager.lifecycleDays` 常數，backend 用 `licenseConfig.ts` 的 `BASE_TRIAL_DAYS`，三邊數字一致性由各自測試檔互相校驗）。
  2. **推廣代碼（如 `26FR-NR`）可延長一次到「總共 30 天」**（`PROMO_TOTAL_TRIAL_DAYS`），不是在剩餘天數上再加 30 天：到期時間 = 裝置試用起算錨點 + 30 天。每台裝置對每組年度代碼限領一次，且只接受「當年度」代碼——過去年度視為過期，未來年度（如 `99FR-NR`）一律視為無效，不可被誤判為永遠不過期。詳見 `docs/PROMO_CODES.md`。
  3. **之後必須付費購買 VIP**，否則無法使用。VIP 序號一律是 ECDSA P-256 簽章序號（`FRVIP-<payload hex>-<簽章 hex>`，見 `backend/src/lib/vipSerial.ts`、`android/.../util/VipSerialVerifier.kt`、`ios/.../Auth/VipSerialVerifier.swift`），App 內只放公鑰、驗過簽章才開通；後端另有 `vip_serial_redemptions` 表限制同一組序號只能在一台裝置開通一次。**不要**再用字串前綴/長度規則或寫死序號判斷 VIP，也不要把簽發序號用的私鑰放進 repo 或程式碼常數（只放在簽發工具讀取的環境變數）。
  4. VIP 到期時間（`expires`）遺失或為 `0` 一律視為「非 VIP」，不是「永久授權」——這是安全修正，不要因為某個裝置的到期時間讀不到就當作已經開通。

- **強制更新只在「有新版可拿」時觸發，不是建置日期到期。** 後端提供 `min_supported_version_code`（`licenseConfig.ts`，預設 0 = 永不強制），App 啟動時透過 `/api/license/verify` 連同授權狀態一起取得；只有目前的 `versionCode`／build number 低於這個門檻才顯示強制更新畫面。**離線或連不上後端時一律不鎖**，直接沿用本機快取狀態正常使用。到期畫面依原因分成「試用結束」（導向輸入推廣碼／購買 VIP）與「必須更新」（導向下載最新版本）兩種文案，不要合併成同一套說法。不要再引入以建置時間（`BUILD_TIME_MS`/`buildDate`）為準的到期或強制更新邏輯，那兩個欄位只保留做顯示與測試用途。

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

### Layer 3 — 資料夾記憶（已完成）

對應舊版的 `MusicUtility.getMusicPath()` ＋ `GetMusicInfoTask`（含子資料夾掃描）。

- Android：`OpenDocumentTree()` ＋ `takePersistableUriPermission`，記住教練的音樂資料夾，之後直接列出整個資料夾內容
  （見 `data/ExternalMusicFolder.kt`）。段落的 `musicFileName` 直接存該檔案的 content Uri 字串
  （`MusicSource.isExternalUri`），播放/波形分析一律透過 `data/MusicSource.kt` 判斷來源與存在性，不複製檔案。
- iOS：`.fileImporter` 選資料夾 ＋ security-scoped bookmark 持久化（見 `Database/ExternalMusicFolderStore.swift`）。
  `musicFileName` 存 `"extfolder://" + 相對路徑`（`MusicSource.isExternal`），實際存取一律透過
  `Audio/MusicSource.swift` 的 `withResolvedFileURL`，把 security-scoped 存取視窗正確涵蓋在檔案開啟/讀取期間。
- 資料夾內容以串流方式列出（Android 用 `DocumentsContract` 對 child documents 做 Cursor 查詢；iOS 用
  `FileManager` 的 enumerator／`contentsOfDirectory`），不會一次把整個資料夾複製進 app 儲存空間。
- 「含子資料夾」開關兩平台都保留，預設沿用舊版的 `true`。
- 音樂庫畫面（Layer 2）用分頁清楚區分「已匯入音樂庫」與「音樂資料夾」，避免教練搞混哪些檔案在哪。
- 資料夾授權被撤銷、或 bookmark／檔案已失效時，一律當作「找不到檔案」優雅降級（列表顯示「資料夾存取已失效」，
  播放/波形分析退回預設值），不會讓 App 崩潰。
- 匯出 `.riderclass`（`RiderClassArchiveService`）目前不會把外部資料夾曲目的音檔一併打包──
  兩平台既有的「檔案不存在就跳過」邏輯剛好自然涵蓋這個情況，是刻意不擴充的範圍，不是遺漏。
