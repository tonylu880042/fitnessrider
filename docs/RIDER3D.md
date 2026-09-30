# FitnessRider 3D 版（與 3D Biking 搭售）

## 產品決策（2026-09-29 Tony 定案）

- 與公司的 3D Biking 系統搭售，是**另一個 App**。同一套程式碼編出兩個 App：現有「教練版」與「3D 版」，
  可同時裝在同一台裝置，資料與授權互不相通（課表要搬就用 `.riderclass`）。
- **雙平台都要**，CLAUDE.md 的對等規則照舊。
- Android flavor：`coach`（`com.fitnessrider.coach`，不變）、`rider3d`（`com.fitnessrider.rider3d`）。
  iOS 新 target `FitnessRider3D`（`app.fitnessrider.rider3d`）。顯示名稱暫定「FitnessRider 3D」，圖示待提供前沿用現有。
- 3D 版不走 7 天試用／推廣碼／VIP 付費牆，改用**啟動碼**解鎖——**設計待定，先不做**。
  定案前 3D 版沿用教練版的授權流程，不要自己先發明一套。
- 3D 主機介接來源：`FitnessRider3D_Spec_V1.0.12_2019.07.01.pptx`（REST `/api/v1/fitnessrider3d/*`，
  主機以 UDP 廣播 `255.255.255.255:24000` 公告 IP 與 port）。實機確認後的正式格式補在本文件「3D 主機 API」一節。

## 分支策略（2026-09-30）

- D0 在 `feature/rider3d-integration`（worktree `../fitnessrider-rider3d`）完成、確認教練版行為與 main 完全相同後，
  **合回 main 並刪除 worktree**。
- D1 起直接在 main 開發，一個單元一個 commit。3D 功能以 `isRider3D` 旗標只出現在 3D 版，教練版行為不變；
  教練版的修正 3D 版自動取得，不需要同步分支。

## 兩個 App 之間只差這幾處

其餘程式碼全部共用。

- 單一旗標，雙端同名 `isRider3D`：Android `BuildConfig.IS_RIDER3D`（flavor 各自設定）；
  iOS 讀 3D target 專屬 Info.plist 的 `FRIsRider3D`。3D 相關程式碼放在共用原始碼、以這個旗標決定是否出現，
  這樣既有兩個測試檔可以直接測 3D 的純函式，不另開測試 target。
- 網路權限只加在 3D 版：Android `src/rider3d/AndroidManifest.xml` 允許 cleartext（3D 主機是區網 http）；
  iOS 3D 版 Info.plist 加 `NSLocalNetworkUsageDescription`。
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

- **D0 兩個 App 的建置設定**：flavor／target、`isRider3D`、名稱、權限、上傳腳本。
  驗收：兩個 App 可並存安裝且資料各自獨立；教練版行為與 main 完全相同；兩端既有測試全過。
- **D1 連線**（「3D 主機 API」一節補齊後）：3D 版設定頁加主機 IP:port、開關、以 `GetUGymBikeStage` 測試連線並顯示狀態。
- **D2 課程連動 MVP**：開課時選場景（`GetSceneInfos`，記住上次選擇，不改資料庫）→ `EnterTraining`
  （失敗時給「重試」與「不連 3D 直接上課」兩個選項）→ `UpdateWorkoutInfo` 的 start／pause／stop／exit，
  以及換 Cue／換段落時的 update（`targetRpm`／`intensityZone`／`posture`）；`start` 要等主機狀態到 `prepare` 才送。
  監看引擎既有狀態即可，引擎不改。
  payload 組裝、姿勢對應、狀態轉指令做成雙端同名純函式＋測試。
- **之後再說**：HUD 3D 鏡頭控制（focusUser／focusRank／lineUp／rpmView／trap／groupRide）、
  學員排行（`GetUserTrainingInfos`）、阻力連動（需先把「建議阻力」改成 1~40 數值）、UDP 自動搜尋、啟動碼。
- **不做**：`power`（與 spec §6「剔除瓦特數」衝突）、`CheckUpdate`（我們有自己的強制更新）。

## 3D 主機 API

來源：實機 `192.168.0.115` 探測（2026-09-30）＋主機原始碼 `uGymLauncher`（Windows WPF，.NET Nancy 自架於 port 8000；
`NancyModule/NancyModuleTraining.cs` 的 `#region fitnessrider3d`、`NancyModule/NancyModuleControl.cs`、`Data/NancyData.cs`）。
原始碼 zip 不進 repo。

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
  此時 `GetSceneInfos` 與 `EnterTraining` 都會回 `ErrCode 20`。2026-09-30 實機即為此狀態。

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
| `focusUser`／`focusRank`／`lineUp`／`rpmView` | 3D 鏡頭與顯示控制 |
| `resistance` | 直接對單車下阻力 `Level,<n>`（1~40） |
| `power` | 直接對單車下功率 |
| `silentMode` | 規格沒寫（2020 新增）：主機音樂清單暫停／繼續 |

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
- [ ] 主機補上 `C:\uGym\GymSceneList_<語系>.xml`，讓 `GetSceneInfos`／`EnterTraining` 可用。
- [ ] 主機 `gymSettings.playMusic` 關閉（避免雙重音樂），或改由 App 在 Unity 開跑後送 `silentMode: true`。

### 產品範圍（2026-09-30 Tony 全部同意）
- [x] 這一輪只做 D1＋D2（MVP），鏡頭控制、學員排行、阻力連動之後再做。
- [x] 課程播完送 `stop`（3D 顯示結果）、教練中途離開 HUD 送 `exit`。
- [x] `EnterTraining` 失敗時給「重試」與「不連 3D 直接上課」。
- [x] 姿勢對應中 SPRINT→0、JUMPS→2。
- [x] 暫停時 3D 不會停（主機 `pause` 無作用），可接受。

### D0 需要 Tony 處理
- [ ] Firebase console 新增 Android app `com.fitnessrider.rider3d`，提供新的 `google-services.json`。
- [ ] 顯示名稱「FitnessRider 3D」與圖示。
- [ ] Apple Developer 新增 App ID `app.fitnessrider.rider3d`（UDP 自動搜尋的 multicast 權限之後再申請）。
