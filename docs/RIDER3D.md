# FitnessRider 3D 版（與 3D Biking 搭售）

## 產品決策（2026-09-29 Tony 定案）

- 與公司的 3D Biking 系統搭售，是**另一個 App**。同一套程式碼編出兩個 App：現有「教練版」與「3D 版」，
  可同時裝在同一台裝置，資料與授權互不相通（課表要搬就用 `.riderclass`）。
- **雙平台都要**，CLAUDE.md 的對等規則照舊。
- Android flavor：`coach`（`com.fitnessrider.coach`，不變）、`rider3d`（`com.fitnessrider.rider3d`）。
  iOS 新 target `FitnessRider3D`（`app.fitnessrider.rider3d`）。顯示名稱暫定「FitnessRider 3D」，圖示待提供前沿用現有。
- **收費（2026-10-29 Tony 定案）**：**每位教練每月 US$5，商店自動續訂**（App Store 自動續期訂閱、Google Play 訂閱），
  首月免費用商店的免費試用優惠。新舊 3D Biking 客戶一律相同，沒有場館授權、沒有啟動碼。
  3D 版不走教練版的 7 天試用／推廣碼／VIP 序號，也不用後端 `device_trials`。
  - 教練在 App 內付費解鎖數位功能，iOS 依 Guideline 3.1.1 一定要走 IAP，不可在 App 外賣碼繞過。商店抽成約 15%。
  - 授權以商店為準（StoreKit 2 `Transaction.currentEntitlements`、Play Billing `queryPurchasesAsync`）。
    兩者都有本機快取，場館區網連不到外網時仍能判斷；也就不會碰到兩個 App 共用 `device_fingerprint` 的問題
    （同一台裝置兩個 App 的 fingerprint 相同，後端 `device_trials` 只認先裝的那個）。
  - 首月免費每個 Apple ID／Google 帳號只能領一次，由商店把關，不另做防重灌。
  - 訂閱到期只影響 App，3D 主機照常運作（客戶買斷的是主機）。不可在上課途中鎖住。
  - **依賴**：3D 版的訂閱商品（App Store Connect／Play Console）與 RevenueCat 都還沒設定（見 CLAUDE.md Paywall 節）。
    商品 ID 出來前 3D 版沿用教練版授權流程，不要先寫假的購買流程或購買抽象層。
- 3D 主機介接來源：`FitnessRider3D_Spec_V1.0.12_2019.07.01.pptx`（REST `/api/v1/fitnessrider3d/*`，
  主機以 UDP 廣播 `255.255.255.255:24000` 公告 IP 與 port）。實機確認後的正式格式補在本文件「3D 主機 API」一節。

## 分支策略（2026-09-30）

- D0 在 `feature/rider3d-integration` 完成，2026-09-30 合回 main，分支與 worktree 已刪除。
- D1 起直接在 main 開發，一個單元一個 commit。3D 功能以 `isRider3D` 旗標只出現在 3D 版，教練版行為不變；
  教練版的修正 3D 版自動取得，不需要同步分支。

## 兩個 App 之間只差這幾處

其餘程式碼全部共用。

- 單一旗標，雙端同名 `isRider3D`：Android `BuildConfig.IS_RIDER3D`（flavor 各自設定）；
  iOS 讀 3D target 專屬 Info.plist 的 `FRIsRider3D`。3D 相關程式碼放在共用原始碼、以這個旗標決定是否出現，
  這樣既有兩個測試檔可以直接測 3D 的純函式，不另開測試 target。
- 網路權限只加在 3D 版：Android `src/rider3d/AndroidManifest.xml` 允許 cleartext（3D 主機是區網 http）；
  iOS 3D 版 Info.plist 加 `NSLocalNetworkUsageDescription` 與 ATS `NSAllowsLocalNetworking`（對應 Android 的 cleartext）。
- **先做手動輸入主機 IP:port**。UDP 自動搜尋要等 iOS 的 `com.apple.developer.networking.multicast`
  向 Apple 申請核准後，兩端一起加（不要只做 Android）。
- Firebase：`google-services.json` 要有 `com.fitnessrider.rider3d` 的 client（Tony 在 Firebase console
  新增 Android app 後下載取代），沒有的話 rider3d flavor 編不過。
- `tools/upload_to_firebase.sh` 的 `assembleDebug` 與 APK 路徑改為教練版
  （`assembleCoachDebug`、`apk/coach/debug/app-coach-debug.apk`），保持現有上傳流程不變；腳本註解保留原樣。
  Android 測試指令改為 `./gradlew :app:testCoachDebugUnitTest`。

## 執行原則

- **HUD 不可等網路**：除了開課時的 `EnterTraining`，所有 3D 呼叫都送出即返回、逾時要短，失敗只在 HUD 顯示狀態，
  音樂與課程照常進行。
- 3D 功能只在 3D 版且設定頁開啟時才運作。

## 開發單元（依序，前一個 commit 前不要動下一個）

- **D0 兩個 App 的建置設定**（已完成）：flavor／target、`isRider3D`、名稱、權限、上傳腳本。
  驗收：兩個 App 可並存安裝且資料各自獨立；教練版行為與 main 完全相同；兩端既有測試全過。
- **D1 連線**（已完成，`38142b2`）：3D 版設定頁加主機 IP:port、開關、以 `GetUGymBikeStage` 測試連線並顯示狀態。
- **D2 課程連動 MVP**（已完成，`33a771f`，2026-11-02 與實機 `192.168.0.115:8000` 驗證通過）：開課時選場景（`GetSceneInfos`，記住上次選擇，不改資料庫）→ `EnterTraining`
  （失敗時給「重試」與「不連 3D 直接上課」兩個選項）→ `UpdateWorkoutInfo` 的 start／pause／stop／exit，
  以及換 Cue／換段落時的 update（`targetRpm`／`intensityZone`／`posture`）；`start` 要等主機狀態到 `prepare` 才送。
  監看引擎既有狀態即可，引擎不改。
  payload 組裝、姿勢對應、狀態轉指令做成雙端同名純函式＋測試。
- **D2.1 已啟用 3D 但主機位址無效時要看得見**（2026-11-05 實機回報，雙端，D3 之前）：
  現況「啟用 3D Biking 連動」打開但主機位址空白或格式不對時，按「上課開騎」不會有任何提示，直接進 HUD、沒有選場景，
  教練以為 3D 版沒有選場景功能。
  - 雙端同名純函式 `rider3DStartAction(isRider3DApp, isEnabled, hostInput)`，回傳三種：
    一般開課（非 3D 版或開關關閉）、選場景（`normalizedRider3DBaseUrl` 有效）、需要主機位址（開關開但位址無效）。
    課表清單的「上課開騎」改用它決定流程，取代目前直接呼叫 `isRider3DActive` 的判斷（`isRider3DActive` 本身不改，HUD 照用）。
  - 「需要主機位址」時跳對話框：標題「尚未設定 3D 主機」，內文「已啟用 3D Biking 連動，但主機位址未設定或格式不正確。」，
    兩個按鈕「前往設定」（開設定頁）與「不連 3D 直接上課」（照一般開課）。點外面＝取消，不開課。
  - 設定頁：開關打開時，主機位址欄下方顯示一行提示——空白「尚未設定主機位址，開課時不會連動 3D」、
    非空但無效「主機位址格式不正確（例如 192.168.0.115:8000）」；位址有效或開關關閉時不顯示。顏色用既有 token。
  - 只影響 3D 版；教練版（`isRider3D == false`）行為完全不變。
  - 測試（雙端同名）：`rider3DStartAction` 的四種組合——非 3D 版、3D 版開關關、開關開＋空白、開關開＋無效字串、開關開＋有效位址。
- **D3 3D 視角面板：RPM 視圖＋列隊**（2026-11-04 Tony 定案，D2 之後）：
  - HUD 頂端列（3D 狀態文字旁）加一顆「3D 視角」鍵，只在 3D 版、本堂有連 3D、且本堂已在主機開跑（送出 `start`）、尚未 `stop` 時可按，其餘時間 disable 不隱藏。
    按下開一個浮在 HUD 上的面板（點外面關閉），音樂與課程照常進行。**不加任何手勢**：
    中央儀表不可有點擊類手勢（CLAUDE.md B 節），面板內一律是按鈕。
  - 面板內容：「RPM 視圖」開關（送 `{"command":"rpmview","rpmView":true|false}`）、「學員列隊」按鈕（送 `{"command":"lineup"}`，一次性動作）。
  - 主機沒有查詢目前視圖的 API，**RPM 視圖的開關狀態由 App 自己記**：每次 `EnterTraining` 成功後重設為關，不跨課堂記憶。
    送出失敗時開關退回原狀態，並顯示既有的「3D 連線異常」。
  - 送出即返回、逾時 1.5 秒，與 D2 的 update 共用 `Rider3DClient.updateWorkoutInfo`，不另開一條 HTTP 路徑。
  - `buildRider3DUpdateWorkoutJson` 雙端加 `rpmview`（帶 bool `rpmView`）與 `lineup`（只帶 `command`）兩個分支，`Rider3DCommandSpec` 加對應欄位。
  - 雙端同名純函式＋測試：`canSendRider3DViewCommand(isActiveInHud, hasStartedOnHost, hasStoppedOnHost)`（已送出 `start` 且尚未 `stop` 才為 true；不用 `hostStatus`，因為送出 `start` 後 HUD 就停止輪詢主機狀態，它會停在 `prepare`）；
    `rpmview`／`lineup` 的 JSON 內容（`rpmView` 為 JSON bool 不是字串）。
  - **實機驗證**（要 Tony 在 3D 螢幕前看）：Unity 開課時 RPM 視圖的預設是否為關；開／關是否即時生效；
    `lineup` 的畫面效果與持續時間；`stop` 之後（結果頁）送這些指令會不會出錯。
- **D4 學員名單與鏡頭跟隨**（D3 commit 後才開始）：
  - 開啟「3D 視角」面板時呼叫一次 `GetUserTrainingInfos`（唯讀，非同步、逾時 2 秒，讀取中顯示「讀取學員中…」），
    **不做背景輪詢**；面板上有「重新整理」鍵。回應格式見「3D 主機 API」一節。
  - 「鏡頭跟隨名次」：依目前人數列出 1～N 的按鈕，送 `{"command":"focusRank","focusRank":"<N>"}`（`focusRank` 是字串）。
  - 「鏡頭跟隨學員」：列出學員姓名（`name`，空白時顯示「<deviceNo> 號車」），點選送 `{"command":"focusUser","focusUserId":"<phone>"}`。
  - **學員手機號碼是個資**：只放在面板的記憶體狀態裡當作送出用的 id，不顯示、不寫 log、不存檔、不放進 URL 以外的任何地方，
    面板關閉即丟棄（URL 是主機 API 規定的傳法，只在區網內）。
  - 錯誤訊息：ErrCode `0x09005`（`GroupCycling_OutOfRankNo`）→「名次超過目前騎乘人數」，ErrCode `0x02000`（`Account_NotExist`）→「找不到這位學員」，
    其餘沿用「3D 連線異常」。顯示後重新讀一次名單。
  - 名單為空時顯示「目前沒有學員登入」，名次與學員兩區 disable。
  - 雙端同名純函式＋測試：`parseRider3DUserTrainingInfos(body)`（含空名單、缺欄位、ErrCode ≠ 0）、
    `rider3DFocusRankOptions(userCount)`（0→空、3→1…3）、`focusRank`／`focusUser` 的 JSON、上述兩個 ErrCode 的文案對應、
    `rider3DRiderDisplayName(name, deviceNo)`。
  - **實機驗證**：至少兩台車有學員登入，確認名次與學員跟隨都會轉鏡頭；**怎麼回到預設鏡頭**（主機原始碼裡找不到「取消跟隨」的指令，
    要看 `lineup` 或 Unity 有沒有自動回復）。若沒有辦法回到預設，面板要明講「跟隨後需重新開課才會回到全景」或由 Tony 決定。
- **之後再說**：HUD 常駐的學員排行（輪詢 `GetUserTrainingInfos`）、`trap`／`groupRide`（屬於 `start` 參數，要先決定開課時怎麼讓教練選）、
  阻力連動（需先把「建議阻力」改成 1~40 數值）、UDP 自動搜尋、商店訂閱（商品設定好之後）。
- **不做**：`power`（與 spec §6「剔除瓦特數」衝突）、`CheckUpdate`（我們有自己的強制更新）。

## 3D 主機 API

來源：實機 `192.168.0.115` 探測（2026-09-30）＋主機原始碼 `uGymLauncher`（Windows WPF，.NET Nancy 自架於 port 8000；
`NancyModule/NancyModuleTraining.cs` 的 `#region fitnessrider3d`、`NancyModule/NancyModuleControl.cs`、`Data/NancyData.cs`）。
原始碼 zip 放在 repo 外 `/Users/tunghunglu/projects/uGymLauncher.zip`，不進 repo。

### 找主機
- UDP 廣播到 `255.255.255.255:24000`，純 ASCII、逗號分隔、無引號：`uGym,ip,<主機IP>,8000`。port 在主機程式裡寫死 8000。

### 呼叫方式
- 基底 `http://<ip>:8000/api/v1/fitnessrider3d/<API>`，**全部 GET**，路由不分大小寫。
- `EnterTraining`、`UpdateWorkoutInfo` 的參數是**單一 query 參數 `json`**，值為 URL-encode 過的 JSON 字串：
  `GET .../UpdateWorkoutInfo?json=%7B%22command%22%3A%22start%22...%7D`。
- 回應外殼（HTTP 200，`Content-Type` 標成 `text/html`，不要依賴它）：
  `{"ErrVer":1,"ErrPairs":[{"ErrCode":0,"ErrMsg":null}],"Obj":<資料>}`，`ErrCode == 0` 為成功；
  主機端任何例外都回 `ErrCode 20 General_ProcessFail`。不存在的 API 回 HTTP 404。

### EnterTraining
JSON 欄位全是字串：`classId`、`classTitle`、`classDuration`、`sceneId`。
- 主機實際只用 `classId` 與 `sceneId`；**`classDuration` 在主機端被註解掉、沒有使用**，`classTitle` 也沒用。
  所以播放速率不會造成兩邊時間不同步（3D 不做課程倒數）。照規格照送即可（`classDuration` 送總毫秒）。
- `sceneId` 空字串時主機當成 `"1"`。場景從主機的 `gymSceneList` 找 `Id`，找到就啟動該場景的 Unity 程式，
  狀態變 `waitUnity`。
- **`gymSceneList` 是主機首頁載入時讀 `C:\uGym\GymSceneList_<語系>.xml`**（tw／cn／en／pt）；檔案不在就是 null，
  此時 `GetSceneInfos` 與 `EnterTraining` 都會回 `ErrCode 20`。2026-11-02 實機 `192.168.0.115` 已載入 12 個場景，且實機 `GetSceneInfos` 的 `Obj` 直接回傳陣列 `[{"Id":"1","Name":"黃昏湖畔",...}, ...]`（規格投影片寫成 `{"sceneInfos":[...]}`，雙端 parser 同時相容兩種格式）。

### UpdateWorkoutInfo
JSON：`command`、`rpm`、`intensity`、`posture`、`focusUserId`、`focusRank` 為**字串**；`resistance`、`power` 為 int；
`trap`、`groupRide`、`rpmView`、`silentMode` 為 bool。`command` 不分大小寫。

| command | 主機行為 |
|---|---|
| `start` | 通知 Unity 開跑（帶 `trap`、`groupRide`）。Unity 必須已到 `prepare`，否則訊息會遺失 |
| `update` | 把 `rpm`、`intensity`、`posture` 轉給 Unity；**`rpm` 必須 > 0 才會送** |
| `pause` | **主機什麼都不做**（Unity 不會暫停） |
| `stop` | Unity 顯示結果頁 |
| `exit` | 關掉 Unity 程式、停掉主機音樂清單 |
| `focusUser` | 以 `focusUserId`（學員帳號＝手機號碼）找登入學員，MQTT `UpdateGameInfo,FocusByMac,<padMac>`；找不到回 `Account_NotExist`（`0x02000`） |
| `focusRank` | `focusRank` 字串轉 int，須在 1～登入人數之間，否則回 `GroupCycling_OutOfRankNo`（`0x09005`）；MQTT `UpdateGameInfo,FocusByNo,<N>` |
| `lineUp` | MQTT `LineUp`，不帶參數 |
| `rpmView` | bool `rpmView`：true → MQTT `RPMViewOpen`、false → `RPMViewClose`（2019-07-01 為巴西客戶加入）。主機只轉送，不記狀態、沒有查詢 API |
| `resistance` | 直接對單車下阻力 `Level,<n>`（1~40） |
| `power` | 直接對單車下功率 |
| `silentMode` | 規格沒寫（2020 新增）：主機音樂清單暫停／繼續 |

### 學員名單（GetUserTrainingInfos）
唯讀，無參數。`Obj: {"userTrainingInfos":[...],"currentSeconds":<int>}`，只列 `status != 0` 的登入學員。每筆：
`name`、`phone`（＝ `focusUser` 用的帳號）、`imageUrl`、`status`（字串）、`type`、`rpm`、`hr`、`calorie`、`sideForces`、`rank`（無排名為 0）、`deviceNo`。
注意 `focusRank` 的上限檢查用的是全部登入人數（含 `status == 0`），可能比這份名單多；App 依名單人數列名次即可。

### 狀態（GetUGymBikeStage）
`Obj: {"stage":"launcher","status":<狀態>,"extra":null}`，fitnessrider3d 模式下 `stage` 永遠是 `launcher`。
`status` 流程：`""` →（`EnterTraining`）`waitUnity` →（Unity 載入完成）`prepare` →（`start`）`start` →（`stop`）`result`；
`exit` 回到 `""`。**App 要等到 `prepare` 才送 `start`。**

### 主機自己的音樂
Unity 進入訓練畫面時，若主機設定 `gymSettings.playMusic` 為 true，主機會播自己的音樂清單——
會和 FitnessRider 的音樂同時響。搭售安裝時主機要把 `playMusic` 關掉。

### 其他
- `GetUserTrainingInfos` → `Obj: {"userTrainingInfos":[...],"currentSeconds":<int>}`。
- `SegmentInfo_v1`（rpm 0~250、intensity 35~90、posture 0~3、resistance 1~40、altitude 0~100、duration 分鐘）
  是**主機自己的課表編輯器**用的格式（`/api/bikeconsole/SaveClassInfo_v1`），**不在 fitnessrider3d API 裡**。
  它的值域可以沿用在 `update`：

| update 欄位 | 值域 | 來源 |
|---|---|---|
| `rpm` | 0~250，必須 > 0 | 目前 Cue 的 `targetRpm`（編輯器限 40~140） |
| `intensity` | 35~90，心率強度（%HRmax） | 段落 `intensityZone`：Z1→55、Z2→65、Z3→75、Z4→85、Z5→90 |
| `posture` | 0 坐姿平地、1 坐姿爬坡、2 站姿跑步、3 站姿爬坡 | SEATED_FLAT→0、SEATED_CLIMB→1、STANDING_FLAT→2、STANDING_CLIMB→3、RECOVERY→0、SPRINT→0、JUMPS→2 |

## 待確認

### 3D 主機（擋 D1／D2 實機驗證）
- [x] 主機補上 `C:\uGym\GymSceneList_<語系>.xml`，讓 `GetSceneInfos`／`EnterTraining` 可用（2026-11-02 實機 `192.168.0.115` 已確認可用並通過連動測試）。
- [ ] 主機 `gymSettings.playMusic` 關閉（避免雙重音樂），或改由 App 在 Unity 開跑後送 `silentMode: true`。

### 產品範圍（2026-09-30 Tony 全部同意）
- [x] 這一輪只做 D1＋D2（MVP），鏡頭控制、學員排行、阻力連動之後再做。
- [x] 2026-11-04：D3（RPM 視圖＋列隊）與 D4（學員名單＋名次／學員鏡頭跟隨）都要做，依序進行。
- [x] 課程播完送 `stop`（3D 顯示結果）、教練中途離開 HUD 送 `exit`。
- [x] `EnterTraining` 失敗時給「重試」與「不連 3D 直接上課」。
- [x] 姿勢對應中 SPRINT→0、JUMPS→2。
- [x] 暫停時 3D 不會停（主機 `pause` 無作用），可接受。

### D3／D4 實機（要 Tony 在 3D 螢幕前看）
- [ ] 對 `192.168.0.115` 送 `rpmview`／`lineup`／`focusRank`／`focusUser` 前先取得 Tony 同意（會改變主機畫面）。
- [ ] RPM 視圖的預設狀態與畫面樣子；`lineup` 的效果；鏡頭跟隨後怎麼回到全景。

### 收費（2026-10-29）
- [ ] **3D 版要怎麼避免取代教練版**：3D 版每月 US$5，比教練版 VIP（NT$390）便宜，又是同一套功能。
      建議：3D 版的 HUD 只有在「最近 30 天內連過 3D 主機」時可用，否則只能編課表與匯出；
      「不連 3D 直接上課」也受同一條件限制。未定前不要實作。
- [ ] 在 App Store Connect／Play Console 建立 3D 版訂閱商品（US$4.99／月、首月免費試用）。

### D0 需要 Tony 處理
- [ ] Firebase console 新增 Android app `com.fitnessrider.rider3d`，提供新的 `google-services.json`。
- [ ] 顯示名稱「FitnessRider 3D」與圖示。
- [ ] Apple Developer 新增 App ID `app.fitnessrider.rider3d`（UDP 自動搜尋的 multicast 權限之後再申請）。
