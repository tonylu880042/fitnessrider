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
- **雙平台曲目切換平滑轉場 (Crossfade)（已對齊）**：雙端均採用雙軌／雙 Deck 架構（iOS `AVAudioEngine` 雙 `AudioDeck`；Android 雙 `ExoPlayer`），實現 1~8 秒等能量（Equal-Power: $\cos$ fade-out, $\sin$ fade-in）平滑交錯淡入淡出，並與「段落結束自動暫停 (Auto-Pause)」嚴格互斥，雙端設定提供 0s / 1s / 2s / 3s / 5s / 8s 設定。

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
- **選項定案：0 / 1 / 2 / 3 / 5 / 8 秒**，兩端必須完全一致，預設仍為 2 秒；
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
  （上滑下一首、下滑上一首）。原本水平滑動在圓形儀表上是變速、在旁邊是換曲，
  課堂中目視前方瞄不準，滑歪就會跳掉一整首歌；改成方向區分後兩者不可能同時成立，
  中間還留有斜向安全死區（垂直大於水平但不到 1.5 倍時兩者都不觸發）。
  判斷邏輯在雙端同名純函式 `rateStepForSwipe` / `segmentStepForSwipe`，有互斥性測試把關。
- 換曲原本就有按鈕（Android `:222`/`:243`、iOS `:227`/`:267`），沒有因此失去功能。

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

## 開發清單：LLM 課表講評（尚未開工，規格已定）

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
