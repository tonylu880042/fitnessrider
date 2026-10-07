# CLAUDE.md

## 專案

FitnessRider — 飛輪課表編排與課堂中控，雙原生（`android/` Kotlin+Compose+Media3、`ios/` Swift+SwiftUI）。

- 業務規格權威來源：`spec.md`
- 技術架構與環境：`Agent.md`
- 推廣培訓代碼政策與年度維護：`docs/PROMO_CODES.md`（當前 2026: `26FR-NR`，過期提醒工具 `tools/check_promo_code.sh`）
- 3D 版（與 3D Biking 搭售的另一個 App）：產品決策、開發單元與待確認事項在 `docs/RIDER3D.md`，動到 3D 相關工作前先讀。
- `original/` 是 2021 年的 Java 舊版，**唯讀參考**，不要修改、不要編譯。

## 通則

- UI 文案一律繁體中文。
- **Android 與 iOS 必須維持功能對等**。任一平台的行為改動，另一平台同一份工作要一起改完，不得只做一邊。
- 顏色用 `theme/Color.kt`（Android）與 `FitnessRiderTheme`（iOS）既有 token，不要寫死色碼。
- 非顯而易見的邏輯要留一個可執行的檢查：Android 加進 `android/app/src/test/java/com/fitnessrider/FitnessRiderAndroidTest.kt`，iOS 加進 `ios/FitnessRiderTests/FitnessRiderTests.swift`。不要引入新測試框架。
- **主程式碼裡不要寫任何註解。** 不寫行內註解、不寫 KDoc／Swift doc comment、不寫檔頭說明，
  既有註解也已全部移除。理由：註解會和程式碼脫節，然後把讀的人（和 AI）帶往錯誤的方向；
  唯一的事實來源是程式架構與程式碼本身。要讓意圖清楚就改名字、拆函式、加測試，不是加註解。
  需要保留的「為什麼」寫在這份 CLAUDE.md 或 commit message，那兩個地方會跟著決策一起維護。
  - 範圍：`android/app/src/`、`ios/FitnessRider/`、`ios/FitnessRiderTests/`、`backend/src/`。
  - **周邊工具不在此限，也不要去清它們**：`tools/` 的 shell／Python 腳本、
    `build.gradle.kts` 等建置腳本、`*.properties` 設定檔的註解一律保留原樣。
    那些註解記的是踩過的坑，改壞了測試抓不到，要到下次發佈才會炸。
- 不要為了「以後可能需要」而加抽象層、介面、設定項。最短可行的 diff 優先。

## 已知落差（已修復對齊）

- **iOS 編輯器的段落試聽（已對齊）**：`ClassEditorView.startPreview` 已全面引入 `AVAudioPlayer` 真實放音，支援變速不變調、即時波形 Scrubbing 與結束自動重置，與 Android 的 `ExoPlayer` 達成完全功能對等。
- **雙平台曲目切換平滑轉場 (Crossfade)（已對齊）**：雙端均採用雙軌／雙 Deck 架構（iOS `AVAudioEngine` 雙 `AudioDeck`；Android 雙 `ExoPlayer`），實現 1~8 秒等能量（Equal-Power: $\cos$ fade-out, $\sin$ fade-in）平滑交錯淡入淡出，並與「段落結束自動暫停 (Auto-Pause)」嚴格互斥，雙端設定提供 0s / 1s / 2s / 3s / 5s / 8s / 10s / 15s 設定（2026-09-28 加長，見下方「開發清單：Crossfade 加長＋手動換曲淡入淡出」）。

## 產品決策（不要當成 bug 修掉）

- **以下這幾條原本寫在程式註解裡，移除註解時搬到這裡** —— 都是從程式碼看不出來、
  猜錯就會產生真 bug 的事實：
  1. iOS HUD 的手勢必須用 `.gesture`，**不可以用 `.simultaneousGesture`**。後者的語意是
     「允許與其他手勢同時辨識」，父層 `cockpitCore` 的換曲手勢會跟著一起成立，變成
     一次滑動同時變速又跳首歌。
  2. `CrossfadeCalculator.effectiveDuration` 兩端都會把實際淡入淡出時間 clamp 在
     「段落長度的一半」，所以 Crossfade 選項要加長不必動音訊引擎。
  3. 段落的 `orderIndex` 必須在移動／刪除後從 0 連續重編號再存：兩端持久化存的是
     `orderIndex` 欄位本身、載入時 `ORDER BY`，只換陣列位置不重編號會「畫面對、重開打回原形」。
  4. `selectedIndexAfterMove` 只算得對相鄰對調（offset ±1，即 UI 的上移／下移）。
     要支援拖過多格時，得改成依新舊陣列比對 id 找位置。
  5. `VipSerialVerifier` 的測試用公鑰參數（`testVipPublicKeyOverride` /
     `publicKeySPKIBase64Override`）只給測試用，正式呼叫端一律不傳。
  6. Android 的 `device_secret` 存在 SharedPreferences，解除安裝或清除資料就會遺失；
     領過推廣碼後重灌的裝置會因此無法再被轉入授權（已知取捨，見 commit 3f779b7）。


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

## 開發清單：客戶回報（2026-09-23，教練 LINE 回報）

### A. Crossfade 可選秒數要能拉長（目前上限 3 秒）

教練反應 3 秒不夠長。只要改選項清單，**引擎不用動** ——
`CrossfadeCalculator.effectiveDuration` 兩端都已經把實際淡入淡出時間 clamp 在「段落長度的一半」，
所以選了 8 秒但段落只有 10 秒時會自動降成 5 秒，不會吃掉整段。

- Android：`ui/settings/SettingsScreen.kt:147` 的 `options` 清單（目前 0/1/2/3）。
- iOS：`Views/Settings/SettingsBackupView.swift:38` 的 `Picker` tag（同上）。
- **選項定案：0 / 1 / 2 / 3 / 5 / 8 秒**（2026-09-28 再加 10 / 15，見 C1），兩端必須完全一致，預設仍為 2 秒；
  與「段落結束自動暫停」互斥的規則不變。
- 六個選項塞不進 Android 現在的橫向等寬按鈕排，改成兩排（3+3）。

### B. HUD 播放中「右滑加速、左滑減速」手勢

舊版有、新版退化成只有 `-2% / 100% / +2%` 三顆按鈕，教練點名這個手勢「很帥」要加回來。

- 舊版實作：`original/.../FragClassProgress.java:1111` 的 `onFling`，水平位移超過 `slideThreshold`
  就 ±0.1 倍速，範圍 1.0x ~ 1.4x。
- **不要照抄舊版的級距與範圍**：新版是 ±15%（0.85x ~ 1.15x）、步進 2%（spec M2.1），
  手勢一律走既有的 `adjustRatePercent(±2.0)`，不要另開一條改速路徑。
- 按鈕不移除 —— 課堂中手汗／戴手套時按鈕比手勢可靠，手勢是加法不是取代。
- **已完成（2026-09-23）**，並連帶改掉既有的「左右滑動換曲」：
  核心區的手勢改為依「方向」分派而不是依「位置」——**水平＝變速、垂直＝換曲**
  （下滑下一首、上滑上一首；2026-10-02 依教練回報從「上滑下一首」反轉，對齊左側清單由上往下的順序）。原本水平滑動在圓形儀表上是變速、在旁邊是換曲，
  課堂中目視前方瞄不準，滑歪就會跳掉一整首歌；改成方向區分後兩者不可能同時成立，
  中間還留有斜向安全死區（垂直大於水平但不到 1.5 倍時兩者都不觸發）。
  判斷邏輯在雙端同名純函式 `rateStepForSwipe` / `segmentStepForSwipe`，有互斥性測試把關。
- 換曲原本就有按鈕（Android `:222`/`:243`、iOS `:227`/`:267`），沒有因此失去功能。

## 開發清單：Crossfade 加長＋手動換曲淡入淡出（2026-09-28 教練回報）

教練原話：「很多音樂的前奏和結尾空白太多，建議拉至 15」；「左邊音樂名稱欄直接按的話，好像沒有淡入淡出」。
兩個單元依序做，**C1 完成並 commit 前不要動 C2**（都會碰雙端 `AppSettings`／測試檔）。

### C1 — 選項加到 15 秒

- 選項改為 **0 / 1 / 2 / 3 / 5 / 8 / 10 / 15**：保留舊值（教練已存的設定不能失效），只往後加。預設仍 2 秒。
- Android `AppSettings.CROSSFADE_OPTIONS_SECONDS`、iOS `AppSettings.crossfadeOptionsSeconds`；
  雙端 `testCrossfadeOptionsSecondsMatchAcrossPlatforms` 同步改。
- Android `SettingsScreen.kt` 的 `chunked(3)` 改 `chunked(4)`（8 顆 → 4＋4）。
- 引擎不用動：`effectiveDuration` 已 clamp 在段落長度一半。

### C2 — 手動換曲也淡入淡出

現況：只有「段落自然播完」會 crossfade。以下手動換曲全是硬切（`cancelCrossfade` → 重新載入）：
HUD 左側清單點選（Android `WorkoutHUDScreen.kt:286`、iOS `WorkoutHUDView.swift:327`，呼叫 `loadClass(…, index)`）、
上一首／下一首按鈕、垂直滑動換曲、iOS 鎖定畫面遠端控制（`AudioEngineManager.swift` 的 remote command）。

**產品決策（2026-09-28 Tony 定案）**：
- **所有手動換曲都淡入淡出**，範圍如上。「上一首」在播放超過 3 秒時回到本曲開頭，仍是直接 seek，不淡。
- **秒數完全沿用設定值**（設 15 秒就淡 15 秒），同樣經 `effectiveDuration` clamp 在「目標段落長度的一半」，
  與「段落結束自動暫停」互斥（開啟時一律硬切）。
- **暫停中換曲 → 硬切**（沒有聲音可淡）。點清單在暫停中會開始播放，這個既有行為保留，但從目標段落直接開始、不淡入。

**行為模型：「立即切換＋舊曲尾巴」**，不要沿用自動 crossfade 的「淡完才換 index」模型：
- 手動換曲當下，引擎狀態（`currentSegmentIndex`、目前段落、時長、進度、速率、Cue）**立刻**變成目標段落，
  HUD 清單反白立刻跟著走。教練點了歌卻要等 15 秒反白才移動會以為沒按到。
- 做法：目前的 active 播放器／deck 降級為「尾巴」，目標段落載到另一個播放器／deck、音量 0 從頭播，
  並成為新的 active。之後依**經過的時間**（不是舊曲剩餘時間）算 progress = elapsed / D，
  套既有 `equalPowerVolumes`：尾巴 fadeOut、新曲 fadeIn；progress 到 1 時尾巴停止並清掉。
- 尾巴的「播放結束」回呼一律忽略（Android 靠 `CrossfadeFinishCoordinator` 的 active index 檢查、
  iOS 靠 `handleTrackBufferFinished` 的 `deckId == activeDeck.id` 檢查——換 active 後舊的自然被擋掉）。
  Android 需要在 coordinator 加一個「交換 active 播放器但不動 segment index 規則」的入口，別繞過它自己改 index。
- 淡入淡出途中：
  - 暫停 → 尾巴與新曲一起暫停；繼續 → 一起繼續，elapsed 不含暫停時間。
  - 再次手動換曲 → 舊尾巴立刻停掉，目前的 active 成為新尾巴，重新開始計時。
  - seek／`loadClass`／離開 HUD → 尾巴立刻停掉，音量復原 1.0。
  - 若自動 crossfade 的觸發條件在尾巴還沒淡完時成立 → 先停掉尾巴再開始自動 crossfade（實務上 clamp 一半長度後不會碰到，但要保證不會三軌同響）。
  - 手動換曲時若自動 crossfade 正在進行 → 先 `cancelCrossfade()` 再照上面流程。
- 目標段落沒有音檔（檔案不存在）時：照常切換並把舊曲當尾巴淡出，新曲那邊沒聲音即可，不能崩潰。
- 速率：新 active 用目標段落的 `playbackRate`；尾巴保留原速率直到停止。

**API 形狀**：雙端引擎各加一個 `jumpToSegment(index)`（iOS 同名），內部決定淡或硬切；
`nextSegment`／`previousSegment` 改呼叫它；HUD 清單改呼叫 `jumpToSegment(index)` 然後 `play()`，
不再用 `loadClass` 換曲（`loadClass` 只留給進入 HUD 時載入課表）。

**測試（雙端同名純函式＋測試）**：
- `manualJumpFadeDuration(requestedDuration, targetSegmentDuration, isAutoPauseEnabled, isPlaying)`：
  暫停中→0、自動暫停開→0、設定 0→0、正常→min(設定, 目標長度一半)、15 秒設定配 20 秒段落→10。
  放在 `CrossfadeCalculator` 裡，內部重用 `effectiveDuration`。
- Android：coordinator 的「交換 active」後，舊播放器 index 的 track-ended 被拒絕、新的被接受，segment index 等於目標。

## 開發清單：VIP 序號疊加＋終身版顯示（2026-09-29）

背景：要讓兩組人長期使用——(a) 負責推廣的人給**終身**（簽一組 `--plan-days 36500` 的序號，現有工具即可）；
(b) 另一組靠推薦賺月份：他們推薦的新教練付費後，Tony **手動**簽一組幾個月的序號給推薦人。
自動化推薦碼**暫不做**：商店付款還沒接（見 Paywall 節），沒有可靠的「對方已付費」訊號，以註冊觸發會被假裝置刷。

### V1 — 序號時間疊加，且同一序號不能重複加天數

現況（三邊都一樣）：兌換 VIP 序號時到期 = **現在** + `planDays`，直接覆蓋。
- 後端 `lib/db.ts` `activateLicenseWithCode` 的 `expiresAt = Date.now() + durationDays`。
- Android `util/VersionLifecycleManager.kt` 的 `vipSerialInfo != null` 分支（離線 fallback）。
- iOS `App/VersionLifecycleManager.swift` 的 `if let vipSerialInfo` 分支（離線 fallback）。
兩個問題：還有 30 天時兌換 90 天序號只剩 90 天（推薦獎勵會吃掉剩餘天數）；
**同一台裝置重新輸入同一組序號會成功並把到期重設為「現在＋天數」**——等於同一組序號可以無限續用
（後端 `claimVipSerial` 對「已被本裝置認領」回傳 true、`/api/license/activate` 的 `isSerialClaimedByDevice` 也放行）。

改為：
- **新序號**：到期 = max(現在, 目前生效中的到期時間) ＋ `planDays`。「目前生效中」＝ 此裝置／user 的 license
  `status === 'active'` 且未過期，**推廣碼 30 天體驗也算**（不另外排除，簡單且對教練有利）；基礎 7 天試用不是 VIP 到期，不算。
- **同一序號在同一裝置再輸入一次**：回傳成功但**不加天數**，沿用目前到期時間（已過期就維持過期、回傳清楚的錯誤訊息
  「此序號已在本設備使用過」）。後端要讓 `claimVipSerial` 分得出「剛認領」與「本來就是本裝置的」，不要另寫一套查詢。
- App 本機（離線 fallback）同樣規則：記錄已兌換過的序號 `serialId` 集合（Android 仿 `KEY_REDEEMED_PROMOS` 的 StringSet，
  iOS 用 UserDefaults 陣列），重複的不加天數。線上兌換成功時 App 一律採用後端回傳的 `expires_at`（現行行為，
  不要在 App 端再疊一次），並把該 serialId 記入本機集合。
- 疊加計算做成純函式，三邊同名：`stackedVipExpiry(nowMs, currentExpiryMs /* 沒有或已過期傳 null/0 */, planDays)`。
- 測試：後端寫在 `backend/src/lib/*.test.mjs` 並加進 `package.json` 的 test 指令；Android／iOS 寫在既有測試檔。
  至少：無現有到期→now+days；現有到期在未來→現有＋days；現有已過期→now+days；
  後端整合測（本機 JSON 模式）：同裝置兌換兩組不同序號天數相加；同一序號兌換兩次到期不變；
  同一序號換另一台裝置仍被拒（既有行為不能壞）。

### V2 — 終身版顯示

目前序號開通一律顯示「專業年繳版 (VIP)」（Android `auth/LicenseVerificationService.kt`、`util/VersionLifecycleManager.kt`；
iOS `App/VersionLifecycleManager.swift` 的 `planName`、`Auth/LicenseVerificationService.swift`；後端 activate 回應訊息）。
- 規則只看到期時間：**VIP 到期距今超過 10 年 → 顯示「終身版 (VIP)」**，兌換成功訊息同步改為「已升級為「終身版 (VIP)」」。
  不新增 plan_type、不改序號格式。推廣碼體驗版的名稱不變。
- 純函式雙端同名（例如 `isLifetimeVip(expiresMs, nowMs)`），有測試（9 年→否、11 年→是、剛好 36500 天序號→是）。
- 後端 `/api/license/activate` 的成功訊息同樣依此規則。

## 開發清單：code review 急件修正（2026-09-29，v1.0.7 上線後）

2026-09-29 code review（範圍 90df8c6..78f448b）找到的三個已上線問題，依序修，**前一個 commit 前不要動下一個**
（F2、F3 都改 iOS `AudioEngineManager.swift`）。其餘 review 發現（序號先認領後寫 license、iOS Keychain／UserDefaults
到期時間不一致、轉移只認最後一組序號、剩餘天數寫死 365 等）不在這一輪。

### F1 — 伺服器已拒絕的序號，App 不可再走離線兌換

V1 新增的後端錯誤碼 `VIP_SERIAL_ALREADY_USED` 沒有加進 App 的 anti-abuse 清單
（Android `auth/LicenseVerificationService.kt` `activateCode` 內的 `antiAbuseCodes`、
iOS `Auth/LicenseVerificationService.swift` `activateCode` 內的 `antiAbuseCodes`），
伺服器拒絕後 App 落到離線 `activateLicenseCode`；重灌／清資料後本機已兌換集合是空的，同一組舊序號又被加一次天數，
V1 要堵的漏洞在 App 端重新打開。

- 把 `VIP_SERIAL_ALREADY_USED` 加進兩端清單。
- 清單從函式內的區域變數抽成具名常數（Android companion／object 常數、iOS `static let`），雙端同名，
  並加測試：兩端清單內容完全一致且包含 `VIP_SERIAL_ALREADY_USED`（仿 `testCrossfadeOptionsSecondsMatchAcrossPlatforms`）。
- **刻意不改成「只有網路失敗才走離線」**：Android `device_secret` 存在 SharedPreferences，重灌後遺失，
  後端對新序號回 `DEVICE_SECRET_REQUIRED`（403）；若一律不 fallback，重灌的付費教練會完全無法開通新序號
  （見產品決策第 6 點）。這個取捨等序號管理工具／後端改版時再處理。

### F2 — iOS：淡入淡出途中跳回前一首，會被舊的完成回呼跳到下一首

iOS `scheduleBuffer` 的 completion 只帶 `deckId` 與 `segmentIndex`。`clearManualTail()` 停掉尾巴 deck 時會觸發它的
completion（非同步丟到 main）；若這次是跳回那個 deck 剛放的段落（例：N → 下一首 N+1 → 3 秒內按上一首回 N），
deck 重新成為 active、index 也相同，舊回呼通過 `handleTrackBufferFinished` 的檢查 → `handleTrackCompletion` 直接跳到 N+1。
`seek` 也會 `playerNode.stop()` 同一個 deck、同一個 index，同樣有風險。Android 不受影響（`clearMediaItems` 不發 `STATE_ENDED`）。

- 修法：`AudioDeck` 加一個排程世代計數（例 `scheduleGeneration`），每次 `scheduleBuffer`／`stop()` 都遞增；
  completion 捕捉排程當下的世代，`handleTrackBufferFinished` 多檢查「世代仍是該 deck 目前的世代」，舊的一律丟棄。
  不要用延遲、旗標或「忽略接下來 N 毫秒」這類時間補丁。
- 測試：優先在模擬器上做真實整合測試——測試內產生兩個短的無聲音檔（AVAudioFile 寫 PCM）放進音樂目錄、
  建一堂兩段的課、play → nextSegment → previousSegment、跑一小段 RunLoop 後斷言 `currentSegmentIndex` 仍是 0。
  若整合測試在 CI／模擬器上不穩定，退而把判斷抽成純函式並測「舊世代被拒、新世代被接受」，並在回報中說明。

### F3 — 自動 crossfade 進行中手動換曲：音量跳回滿格、正在淡入的歌被重來

雙端 `jumpToSegment` 一開始先 `cancelCrossfade()`：把正在淡入的播放器清掉、把淡出中的舊歌音量重設 1.0，
之後才讀 `tailStartVolume`。15 秒 crossfade 讓這個窗口很長，教練聽到下一首進來時按「下一首」或點清單很常見。

- **目標正好是正在淡入的那一段（currentIndex + 1）**：不要重載。把這次自動 crossfade 直接「轉成」手動尾巴——
  淡入中的播放器／deck 成為 active（保留目前播放位置，不從 0 開始），淡出中的舊歌成為尾巴；
  `manualTailDuration` = 目前這次自動 crossfade 的有效秒數，`manualTailElapsed` = 目前 crossfade progress × 該秒數，
  `manualTailStartVolume` = 1.0。這樣接手瞬間兩軌音量與原本曲線完全連續，剩下的時間照手動尾巴規則淡完。
  引擎狀態（index、時長、進度、速率、cue）立刻切到該段，進度以淡入播放器的實際位置為準。
- **目標是其他段落**：兩軌中「目前音量較大」的那一軌成為尾巴（從它目前的音量開始淡出），另一軌停掉並載入目標。
  Android 需要讓 coordinator 能指定「新的 active 是哪個 index」（淡入軌較大聲時 active index 不翻轉），
  不要繞過 coordinator 自己改 index。
- 暫停中、自動暫停開啟、設定 0 秒時仍是硬切（沿用 `manualJumpFadeDuration` 規則），行為不變。
- 測試（雙端同名純函式＋測試）：把「接手時要用的 elapsed／起始音量／誰當尾巴」抽成純函式，至少測：
  progress 0.3 接手 → elapsed＝0.3×D、兩軌音量與接手前相同；淡入軌較大聲時由淡入軌當尾巴；
  Android coordinator 在「active 不翻轉」情境下，舊 index 的 track-ended 仍被拒絕。

## 開發清單：編輯器段落清單（spec M1.2 補完）

段落目前只能「就地取代」—— 編輯器所有操作都是 `segments[selectedIndex] = updated`，
既不能刪除也不能改順序，匯入時多選錯一首就只能整張課表重建。

- **刪除**：每列一顆刪除鍵，跳確認對話框（段落含 cue，誤刪成本高）。不做 undo。
- **排序**：每列「上移／下移」兩顆按鈕，**不做 drag & drop** ——
  SwiftUI 的 `.onMove` 幾乎免費，但 Compose 要自己算位移，兩端行為還難對齊；
  上下移按鈕雙端一致、diff 小。第一列的上移與最後一列的下移要 disable，不要隱藏。
- **`orderIndex` 一定要重編號再存**：兩端都是存 `segment.orderIndex` 這個欄位本身
  （Android `data/ClassRepository.kt:140`、iOS `Database/ClassRepository.swift:136`），
  載入時 `ORDER BY orderIndex ASC`。只換陣列位置而不重寫 orderIndex，畫面會對、
  重新載入就打回原形。刪除後也一律從 0 連續重編，不要留洞。
- 清單 UI：Android `ui/editor/ClassEditorScreen.kt:307`、iOS `Views/ClassEditor/ClassEditorView.swift:153`。
- `selectedSegmentIndex` 要跟著移動／刪除修正，不然會指到別的段落或越界
  （刪掉最後一個段落時尤其要注意）。

## 開發清單：LLM 課表講評（**暫緩，不要開工**）

**2026-09-24 決定暫緩**：使用者規模還小，LLM 成本不划算。以 Opus 5 估算，
50 位教練依實際使用樣態約 US$26／月，全部用滿額度則約 US$260／月，
對照 50 位年繳教練約 US$3,700 的年營收，額度用滿會吃掉大部分毛利。

規格保留在下面不刪，**但在明確重啟之前不要實作、不要派 agent 做**。
重啟的判斷點是付費教練數成長到成本佔比可接受，屆時也應該先拿真實課表
比對 Sonnet 5 的輸出品質再決定用哪個模型，不要直接沿用這裡的 Opus 5 假設。


編排完成後由 LLM 給教練一份講評：做得好的部分、可以調整的部分。**教練主動按才跑**，
不做「存檔自動跑」——額度有限，自動跑存幾次檔就燒光，教練也不知道額度花去哪了。

### 先算再講：規則引擎在前，LLM 只負責組織文字

講評內容有一大半是確定性的，資料本機都有（`intensityZone`、`durationMs`、`baseBpm`、`cues`）：
總時長離目標多遠、有沒有暖身與緩和、強度曲線分佈、連續幾段高強度沒有恢復、
有沒有整段沒有 cue、BPM 與強度是否搭配。

這些一律用本機純函式算，雙端各一份、各自有測試。**LLM 只拿這些結構化發現去組織成
教練聽得懂的話，以及補規則抓不到的東西**（課程敘事、音樂情緒與訓練目標的搭配）。
好處是離線時規則講評照樣能用，只是少了那段文字。不要把這些判斷丟給 LLM 算。

### 用量限制（這是會產生費用的功能）

- **VIP 每台裝置每天 5 次；試用期每台裝置每天 2 次。**
- 「每人」在這個 App 只能是「每台裝置」——App 從不呼叫 `/api/auth/register`／`login`，
  身分就是 `device_fingerprint` ＋ `device_secret`。一個教練有兩台裝置就是兩份額度，
  這是已知且接受的取捨，不要為了它去蓋一套帳號系統。
- **計數必須寫資料庫**。不可以用 `/api/license/verify` 那支 in-memory rate limiter——
  Vercel 每個 Lambda 實例各有各的記憶體，那種擋法對防洗頻勉強夠用，對防花錢完全無效。
- **每日邊界用 Asia/Taipei，不是 UTC**。UTC 午夜是台北早上 8 點，額度會在教練備課或快上課時
  莫名其妙重置。上線後再改會有人一天用到雙倍。
- **另外要有全站每日總上限 ＋ 一個能立刻關掉功能的開關**。每裝置限制擋不住「大量假裝置」，
  全站上限才是半夜被刷爆時唯一能止血的東西。
- 回應要帶「今天剩餘次數」，App 的按鈕直接顯示「今天還剩 N 次」，不要按下去才失敗。

### 架構與安全

- **API 金鑰絕不進 App**：雙端都是發到裝置上的 binary，金鑰會被挖出來。一律走既有的
  Vercel 後端代理（新端點 `/api/coach/review`），金鑰只放後端環境變數——
  與 VIP 簽發私鑰同一條規則。
- 端點認證沿用既有的 `device_secret` HMAC 簽章（與 `/api/license/verify` 同一套），
  未簽章請求一律拒絕，否則別人可以燒掉受害者的額度。
- 後端是 Next.js／TypeScript，用官方 `@anthropic-ai/sdk`，模型 `claude-opus-5`。
  一次講評約 2–4K input token、800 output token，成本約 US$0.03。
- **這個功能絕不能出現在 HUD 路徑上**。課堂進行中任何等待網路的東西都是災難。
- 離線時：規則引擎照跑並顯示結果，LLM 那段明確標示需要連網，不要讓它看起來像壞掉。

### 送出去的資料與隱私

送到 API 的是課表結構 ＋ 曲名 ＋ cue 文字。曲名屬於教練的音樂庫資料，
**隱私權政策（`backend/src/app/privacy`）必須同步揭露**，現在寫的是「不搜集多餘個資」，
Apple 審查會看這塊。

### system prompt 必須寫進去的前提

LLM 看到資料會把刻意的產品決策當成 bug 報，這些要先講清楚：

- `playbackRate` **刻意不計入**總時長與預估消耗（見上面「產品決策」節）。
  否則它百分之百會「好心」告訴教練總時長算錯了。
- 語氣是「觀察」不是「錯誤」。對教了二十年的教練說「你這堂課設計有問題」是 UX 地雷。
- 講評永遠不能擋住儲存，只是附加資訊。

## 開發清單：Paywall（M7）第一階段——不依賴商店的部分

App Store Connect／Google Play Console／RevenueCat 的訂閱商品都還沒設定（2026-09-23 確認），
沒有商品 ID 就寫不出可測的 IAP 流程。所以第一階段只做「商品 ID 出來之後不必重寫」的部分，
**不要引入 RevenueCat SDK，也不要先蓋一層假的購買抽象層**——沒有真的購買系統可接。

### P1. Webhook 身分解析（後端，現在就能做完並測試）

`/api/webhooks/revenuecat` 目前拿 `app_user_id` 去 `getUserById`／`getUserByEmail`，
但**純試用的裝置在後端沒有 user row**：`/api/auth/register` 從來沒被 App 呼叫過，
合成帳號 `coach_xxxxxxxx@fitnessrider.local` 只有在 `activateLicenseWithCode`
（輸入代碼）時才會建立。從沒輸過代碼、直接付費的教練會走到 `User not found`，
log 一行警告然後回 200——錢收了、授權沒開。

- **`app_user_id` 一律用 `device_fingerprint`**。這是沒有帳號系統前提下唯一對得起來的識別。
- Webhook 改為經 `devices` 表反查 `user_id`；查不到就沿用 `activateLicenseWithCode`
  既有那段建立合成 user ＋ 綁定 device，不要另寫一套建立流程。
- 為了相容，舊的 `getUserById`／email 查法保留當 fallback。
- 測試寫在 `backend/src/lib/*.test.mjs`（`node:test` 可直接 import `.ts`；
  未設 `DATABASE_URL` 時 `db.ts` 走本機 JSON 模式）。不要引入新測試框架。

### P2. 付費牆畫面（雙端）

到期畫面目前只給「輸入推廣代碼」，沒有任何地方說明 VIP 是什麼、多少錢。

- 三檔方案卡片（spec M7.1）：月繳 NT$390、季繳 NT$890、年繳 NT$2,390，
  年繳標「🔥 飛輪教練首選・現省 NT$2,290」。
- 列出權益：無限課表建立、無損變速播放、全功能 HUD、課表備份匯出。
- 入口：到期畫面「試用結束」那條路徑，以及設定頁。
- **價格只放在每端一個常數裡**，不要散在 UI 文字中——商店設定好之後價格可能會調。
- **CTA 指向現有的序號輸入流程**，因為今天只有這條路能真的開通。
  不要做假的購買按鈕，也不要做「即將推出」的死按鈕。

### 之後（商店設定好才能做）

接 RevenueCat SDK、三檔商品 ID、購買與恢復購買流程。
注意 iOS 上架後，App 內解鎖數位功能一律要走 IAP（Apple Guideline 3.1.1），
屆時「聯繫取得序號」這條路在 iOS 版可能要收掉或改寫。

## 開發清單：課表包分享（`.riderclass`，spec M5.5）修正（S1、S2 已完成 2026-09-24）

2026-09-24 盤點：格式（Zip ＋ `workout_class.json` ＋ 音檔）與 JSON 欄位雙端已對齊，
但「分享出去對方拿到錯的或打不開」的問題有四條。分兩個單元依序做，**S1 完成並 commit 前不要動 S2**
（兩個單元都改雙端的 `RiderClassArchiveService`）。

### S1 — 匯出：Android 分享鍵接上 ＋ 跨平台 Zip 相容

1. **Android 分享鍵沒有作用**：`MainActivity.kt` 的 `onShareClick` 呼叫 `exportRiderClass(it)`
   後丟掉回傳的檔案，從未叫出分享面板。沿用 `ui/settings/SettingsScreen.kt` 備份分享那套
   `FileProvider.getUriForFile` ＋ `ACTION_SEND` ＋ `createChooser`（確認 `file_paths` 涵蓋
   `cacheDir/exports`）。匯出失敗（回傳 null）要讓使用者看得到。
2. **Android 產生的包 iOS 解不開**：`ZipOutputStream` 預設 DEFLATED ＋ data descriptor
   （local header 大小欄位為 0），iOS 手寫的 `extractZipData` 只支援「STORED、大小寫在 local header」。
   **修 Android 寫入端**：每個 entry 改 `ZipEntry.STORED`，事先設好 `size`／`compressedSize`／`crc`。
   iOS 解壓器不改、不引入 zip 套件。mp3/m4a 本來就壓不小，不壓縮不是損失。
   - Android 測試：產生的 zip 每個 local header 的 method＝0、大小欄位非 0、flag bit 3 未設。
   - iOS 測試：用一段寫死的 STORED zip 位元組（等同 Android 輸出格式）餵給匯入，確認解得出
     `workout_class.json` 與音檔內容。
3. iOS `shareClassPackage` 失敗時的 `print` 改為 Alert。

### S2 — 匯入：不覆蓋、不偷換、看得見

1. **重複匯入會覆蓋既有課表**：匯入沿用原 class id，存檔是 `INSERT OR REPLACE`。
   **匯入一律重新產生 id**（class、segment、cue 全換，segment 的 `classId`、cue 的 `segmentId` 跟著改）。
   已知且接受的結果：同一包匯入兩次會出現兩份課表。
2. **音檔同名偷換**：目前本機已有同名檔就跳過，不同歌但同檔名時課表會播成本機那首。
   改為：同名且內容相同（大小＋位元組比對即可）→ 重用；同名但內容不同 → 以 `_1`、`_2`
   後綴另存，並改寫該課表所有引用這個檔名的 `musicFileName`。
   與 Layer 1 第 6 點的碰撞後綴規則一致（有現成函式就重用，不要再寫一份）。
3. **錯誤看得見**：雙端匯入失敗改為使用者可見的提示（Android Snackbar/Toast/Dialog、iOS Alert）；
   Android 匯入成功也要有提示（iOS 已有「匯入成功」Alert，文案對齊）。
4. **到期時不匯入**：iOS 只在 `ClassListView` 上掛 `onOpenURL`，到期畫面不會匯入；
   Android 在 `onCreate` 一開始就匯入。Android 改為與 iOS 一致：試用到期／必須更新時不匯入。
   順手讓 Android 在 App 已開啟時（`onNewIntent`）也能匯入。
5. 測試：雙端各一個——匯入同一包兩次得到兩份不同 id 的課表；本機已有同名不同內容音檔時，
   匯入後段落指向改名後的檔案且原檔未被覆寫。

### 不在範圍

- iOS 匯出／匯入把整個音檔讀進記憶體（`Data(contentsOf:)`）：有人回報大課表閃退再改串流。
- M5.4 純 JSON 輕量分享：`.riderclass` 缺音檔時本來就會降級，不另做。
- 外部資料夾曲目不打包：見 Layer 3 最後一點，刻意不擴充。

## 開發清單：客戶回報（2026-10-07，教練回饋＋HUD 截圖）

四個單元依序做，**前一個 commit 前不要動下一個**（U1、U4 都改 `WaveformAnalyzer`／編輯器，U3 改 HUD）。

### U1 — 曲目長度一律 05:00（bug，雙端）

從「音樂資料夾」分頁加歌時，段落長度與 BPM 是寫死的預設值，不是實際音檔：
Android `ui/musiclibrary/MusicLibraryScreen.kt` 的 `buildSegmentsFromExternalSelection`（`durationMs = 300_000, bpm = 128.0`）、
iOS `Views/ClassEditor/MusicLibraryView.swift` 同名函式。音樂庫列表中沒有快取的曲目也退回 `300_000`
（Android 同檔 `cached?.first ... ?: 300_000`、iOS `MusicLibraryView.swift` 開頭）。
編輯器只在「選中段落」時分析並寫回，所以沒點過的段落永遠停在 5:00，總時間、卡路里、進度、Crossfade 時間點全錯。

- 從資料夾或音樂庫加歌時，先對每首跑 `WaveformAnalyzer.analyzeWaveform`（有快取，同首只算一次），
  用實際 `durationMs`／`bpm` 建段落；分析期間顯示進度（沿用音樂庫既有的 importing 狀態樣式），不要卡 UI 執行緒。
- 分析失敗（`generateFallbackResult`，檔案讀不到）才退回 300_000／128，行為不變。
- **既存課表修復**：編輯器開啟時，在背景對所有有音檔的段落跑分析，套用與「選中段落」**同一條**寫回規則
  （長度不同就寫回；BPM 只有在仍是 128 時才寫回，教練手動校正過的值不覆蓋）。把這條規則抽成純函式雙端同名
  （例如 `segmentWithAnalyzedTrack(segment, durationMs, bpm)`），選中段落與背景修復都呼叫它，不要寫兩份。
  修復結果跟一般編輯一樣，要教練按儲存才寫入（不要偷偷存檔）。
- 測試：外部資料夾建段落時使用傳入的分析結果；`segmentWithAnalyzedTrack` 的長度寫回、BPM 128 才覆蓋、非 128 保留、fallback 不覆蓋。

### U2 — 平板倒過來時，實體音量鍵上下要跟著翻（Android 限定）

舊版每個 Activity 都是 `android:screenOrientation="sensorLandscape"`：**即使系統鎖定自動旋轉，畫面仍會在兩種橫向間翻轉**，
而系統依畫面方向調換音量鍵上下，所以「音量鍵跟著轉」其實是畫面跟著轉的副作用（舊版沒有攔截任何音量鍵）。
新版沒有鎖方向，完全跟系統自動旋轉設定，教練關掉自動旋轉後畫面不翻、音量鍵也不換。

- **只在 HUD 期間**把 `MainActivity` 的 `requestedOrientation` 設為 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，
  離開 HUD 復原為 `SCREEN_ORIENTATION_UNSPECIFIED`（其他畫面與手機直向行為不變）。Compose 用 `DisposableEffect`。
- **不要自己攔截音量鍵對調**：系統本身已依畫面方向處理，App 再對調一次會在部分機型變成雙重反轉。
- 已知天花板：`targetSdk` 升到 36 後，Android 16 在大螢幕（sw ≥ 600dp）會忽略方向鎖定，屆時要重新評估。
- **iOS 不做（刻意的平台例外）**：iOS App 無法覆寫系統旋轉鎖定，也無法攔截實體音量鍵；iPad 本來就只支援橫向。
- 測試：方向值的選擇抽成純函式（`hudRequestedOrientation(isInHud)`）並測試。

### U3 — HUD 字太小、看不清（雙端，不改深色主題）

**產品決策（Tony，2026-10-07）：維持現有白底主題，不改深色。**
舊版 HUD 以 1920×1080 為基準依螢幕高度等比縮放（`original/.../ui/FitScreen.java`），新版全部固定 sp/pt，
在 10 吋平板上關鍵資訊太小，且大量用灰字 `TextSecondary`。換算到約 800dp 高的平板：
舊版動作倒數平常約 74dp、最後 5 秒換成約 207dp 的大數字並縮放動畫；動作名稱約 36dp；新版倒數只有 32sp、動作名稱 15～22。

- HUD 字級改為「基準值 × `hudScale`」，`hudScale = clamp(HUD 可用高度 / 800, 0.7, 1.6)`，雙端同名純函式＋測試。
  Android 用 `BoxWithConstraints`、iOS 用 `GeometryReader`。
- 在 800 高（scale 1.0）時的基準值調大，至少：動作倒數 32→64、騎乘姿勢名稱→30、握把把位名稱→30、
  cue 說明列→20、下一動作提示列→20、左側清單曲名 14→18／時長 BPM 11→14、課程時間 13→20。
  目標轉速大數字已夠大，維持 82×scale。
- **最後 5 秒大倒數**：動作倒數剩 ≤5 秒時，在中央圓形儀表內顯示佔 HUD 高度約 25% 的秒數（沿用 `AccentRed`），
  每秒有縮放動畫，對應舊版 `FragClassProgress.java` 的 `countDownForCue`。不擋任何按鈕或手勢（`rateStepForSwipe`／`segmentStepForSwipe` 行為不變）。
- 關鍵資訊（數字、姿勢、cue 文字、清單曲名）改用 `TextPrimary`／粗體；灰字只留給次要標籤。顏色只用既有 token。
- 不能溢位：在 1280×800dp 平板與手機橫向（約 390pt 高）都要排得下，長文字用 `maxLines`＋省略號。

### U4 — 自動計算 BPM（雙端）

新版其實有自動估算，但不準：`WaveformAnalyzer.estimateBpm` 拿的是畫波形用的 800 點，一首 5 分鐘的歌每點 375ms，
128 BPM 每拍才 469ms，解析度根本分不出拍子。舊版是先讀 ID3 `TBPM` 標籤，沒有才用高解析度能量分析
（`original/.../musicplayer/GetBpmTask.java`、`Mp3TagReadWrite.java`）。

- **先讀標籤**：iOS 用 AVFoundation metadata（ID3 `TBPM`、iTunes `tmpo`）；Android 的 `MediaMetadataRetriever` 沒有 BPM，
  自己解析檔頭 ID3v2 的 `TBPM` frame（數十行，不加套件；外部 content Uri 一樣透過 `MusicSource` 開串流）。
- **沒有標籤才分析**：在既有的解碼迴圈裡**同時**累積約 10ms 一格的能量包絡（不要再解碼第二次），
  onset strength（能量正向差分）做自相關，範圍 65～175 BPM（沿用既有倍頻折疊），四捨五入到 0.1。
  雙端同名純函式 `estimateBpmFromEnvelope(envelope, hopMs)`。
- 快取：波形快取表的 BPM 是舊演算法算的，加一個分析版本欄位（或等效方式），版本舊的重算 BPM；長度與波形可沿用。
- BPM 校正視窗（Android `ui/editor/BpmCalibrationDialog.kt`、iOS `Views/ClassEditor/BpmCalibrationSheet.swift`）
  加一顆「自動偵測」按鈕，結果填入目前數值，教練可再用 TAP／±1／±5 微調後套用。
- 既有段落的 BPM 不自動覆蓋（可能是教練校正過的值），只有仍為 128 的才會在 U1 的修復流程中更新。
- 測試：程式產生已知節拍的合成脈衝包絡（120、128、140、半速/倍速情境）誤差 ±1 以內；
  Android 用寫死的 ID3v2 位元組測 `TBPM` 解析（含無標籤、標籤非數字）。
