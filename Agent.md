# FitnessRider 雙原生平台開發與商業授權架構指南 (Agent.md)

本文件定義 **FitnessRider (智慧飛輪運動訓練系統)** 的雙原生平台技術架構、Vercel 雲端授權檢查服務，以及各開發階段的具體實作指引。

> [!IMPORTANT]
> **規格權威參考 (Single Source of Truth)**：
> 完整且詳細之產品模組拆解（M1 ~ M7）、功能清單、欄位定義、JSON Schema 與 UI 規格，請參閱：
> 👉 **[`spec.md` (系統功能規格規劃書)](file:///Users/tunghunglu/projects/fitnessrider/spec.md)**
> 本文件 (`Agent.md`) 專注於工程技術選型、伺服器架構、資料庫綱要與開發執行步驟。

---

## 1. 專案目錄結構規劃 (Workspace Structure)

目前專案根目錄已完成歷史資產歸檔至 `original/`，全新開發分為四大核心目錄：

```text
fitnessrider/
├── spec.md               # 系統功能規格規劃書 (權威業務規格、模組 M1~M7、JSON Schema)
├── Agent.md              # 系統技術架構、開發路徑與環境指南 (本文件)
├── original/             # 歷史 Android 舊版 Java 專案（保留供演算法、UI 資源參考，已加入 .gitignore）
├── ios/                  # 全新 iOS / iPadOS 原生專案 (Swift 6 + SwiftUI + AVFoundation + SwiftData)
├── android/              # 全新 Android 原生專案 (Kotlin + Jetpack Compose + Media3 + Room)
├── backend/              # Vercel 雲端授權檢查與設備綁定服務 (Next.js / Node.js Serverless)
└── specs/                # 共通規格檔存放區 (workout_class.json 等 Schema)
```

---

## 2. Vercel 使用權限檢查與單一設備綁定架構 (Device License Service)

### 2.1 現代行動平台設備識別碼 (Device ID) 方案評估
使用者需求為 **「一組帳號限定單一設備使用，必須記錄並驗證設備唯一碼」**。

> [!WARNING]
> **重要技術限制（MAC Address 禁令）**：
> * **iOS (自 iOS 7 起至今)**：Apple 因隱私法規全面封鎖 App 讀取實體 Wi-Fi / 藍牙 MAC Address。呼叫網路介面僅會回傳虛擬常數 `02:00:00:00:00:00`，若試圖私自繞過會直接遭 App Store 拒審。
> * **Android (自 Android 6.0 / 10+ 起)**：Google 同樣封鎖實體 MAC Address 存取，一律回傳 `02:00:00:00:00:00`。

#### 🟢 官方標準且防篡改之原生「設備唯一識別碼」替代方案：
1. **iOS / iPadOS**：
   * 使用 **`UIDevice.current.identifierForVendor (IDFV)`**。
   * **關鍵防護技巧**：首次取得 IDFV 後將其寫入 **iOS Keychain**。即便教練將 App 刪除後重新安裝，Keychain 內的識別碼依然保留，能保證設備識別碼永不變更。
2. **Android**：
   * 使用 **`Settings.Secure.ANDROID_ID`**，或於首次啟動時產生安全性 UUID 並保存於 **Android KeyStore + EncryptedSharedPreferences**。
3. **API 統一欄位**：在通訊協定中命名為 `device_fingerprint`。

### 2.2 Vercel Serverless 架構設計
* **託管環境**：Vercel (Next.js API Routes / TypeScript)。
* **資料庫選型**：**Supabase (PostgreSQL)** 或 **Neon Serverless Postgres**（高可用、具備完整免費額度、原生支援 SQL 連線池）。
* **核心資料表 (Database Schema)**：
  ```sql
  -- 使用者帳號表
  CREATE TABLE users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email VARCHAR(255) UNIQUE NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      name VARCHAR(100),
      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );

  -- 設備綁定表（一對一約束）
  CREATE TABLE devices (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
      device_fingerprint VARCHAR(255) NOT NULL,
      platform VARCHAR(20) NOT NULL, -- 'ios' or 'android'
      device_model VARCHAR(100),     -- e.g. 'iPad Pro 11-inch (M4)'
      bound_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
      last_active_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );

  -- 訂閱授權表
  CREATE TABLE licenses (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id) ON DELETE CASCADE,
      plan_type VARCHAR(20) NOT NULL, -- 'monthly', 'quarterly', 'yearly'
      start_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
      expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
      status VARCHAR(20) DEFAULT 'active' -- 'active', 'expired', 'canceled'
  );
  ```

### 2.3 核心 API 互動流程
1. **登入與設備驗證 (`POST /api/auth/login`)**：
   * App 傳送 `{ email, password, device_fingerprint, device_model, platform }`。
   * 伺服器校驗密碼後，檢查該帳號的 `devices` 紀錄：
     * **首次登入**：自動將此 `device_fingerprint` 寫入綁定，核發含設備識別之 JWT Token。
     * **已綁定且設備相符**：更新 `last_active_at`，登入成功。
     * **設備不相符（嘗試在第二台設備登入）**：回傳 `403 Forbidden (DEVICE_MISMATCH)`，提示已綁定「iPad Pro (2024-05-12 綁定)」，阻擋登入。
2. **換機/轉移設備機制 (`POST /api/device/transfer`)**：
   * 允許教練換新平板時解除舊機，但系統限制**「每 30 天最多允許更換 1 次或 2 次設備」**，徹底杜絕多位教練共享帳號的作弊行為。
3. **授權狀態檢查 (`POST /api/license/verify`)**：
   * App 每次啟動或進入前台時呼叫，確認 `expires_at > NOW()` 且設備無誤，回傳剩餘天數。

---

## 3. 訂閱付費方案評估與定價策略 (Pricing Model Analysis)

### 3.1 使用者原提方案評估
使用者原構想價格：
* **月繳**：350 元新台幣 / 月
* **季繳**：300 元新台幣 / 月（每季 900 元，約 86 折）
* **半年繳**：250 元新台幣 / 月（每半年 1,500 元，約 71 折）
* **年繳**：200 元新台幣 / 月（每年 2,400 元，約 57 折）

### 3.2 確定採用之「三層級」訂閱方案

經商業評估與心理學最佳實踐，正式確立以下三層級定價：

| 方案名稱 | 訂閱週期 | 總定價 (新台幣) | 折合每月 (約) | 方案定位與訴求 |
| :--- | :--- | :--- | :--- | :--- |
| **月繳方案** | 1 個月 | **NT$ 390** / 月 | NT$ 390 / 月 | **彈性嘗鮮**：適合剛入行、兼職代課或短期試用教練。 |
| **季繳方案** | 3 個月 | **NT$ 890** / 季 | NT$ 296 / 月 | **中期穩定**：約 76 折，適合固定帶課但觀望長約的教練。 |
| **年繳方案 (主打)** | 12 個月 | **NT$ 2,390** / 年 | **NT$ 199** / 月 | **超值主力**：約 51 折，現省近 NT$ 2,300！帶 1~2 堂課即回本，專注衝高年度黏著度與現金流。 |

---

### 3.3 針對台灣市場規模（預估上限約 200 位教練）的深度營運與技術策略

使用者明確評估：**「在台灣此 App 的實際活躍用戶上限約為 200 人」**。這是一個極為務實且精準的市場洞察，直接決定了後續的技術與營運路線：

1. **雲端維運成本完全歸零 (Zero Infrastructure Cost)**：
   * 200 位活躍教練每天的 API 請求量（登入與啟動驗證）大約僅有 **200 ~ 600 次/天**。
   * **Vercel Hobby Plan (免費版)**：每日支援 10 萬次 Edge/Serverless 呼叫（我們的用量僅佔免費額度的 **0.5%**）。
   * **Neon / Supabase (免費版)**：提供 500MB 資料庫與 50,000 MAU。儲存 200 筆用戶與設備紀錄僅需數 MB。
   * **結論**：後端基礎設施成本為 **NT$ 0 元 / 月**，所有營收近乎 100% 轉為毛利。

2. **金流與合規實務建議（針對 200 人規模的最佳途徑）**：
   * **方案 A：走 App Store / Google Play 內購 (IAP) —— 推薦**
     * **優勢**：免去自行串接台灣綠界/藍新、免去開立台灣電子發票系統（每年維護費上萬元）、免去各家銀行特約審查，Apple/Google 全自動處理信用卡扣款與跨國稅務發票。
     * **成本**：小企業方案抽成 15%。以 200 人全部年繳計算（總營收約 48 萬），平台抽成約 7.2 萬，換取免開發金流後台、免維護發票與極佳的購買體驗非常划算。
   * **方案 B：Web 訂閱 + 授權碼/開通碼 (License Redeem Code)**
     * 教練在官方網頁（或透過 LINE 官方帳號/轉帳）購買後，後端產生一組 16 碼「授權啟用序號」。
     * App 內只需輸入序號與設備綁定即可開通，避開 App 內購抽成。

3. **專業利基工具 (Prosumer Niche Tool) 的產品優勢**：
   * 客戶群高度集中，產品可建立專屬的「飛輪教練交流社群 (如 LINE 官方社群)」，教練可提出客製化需求，黏著度極高。
   * **課表檔案匯出與交換**：由於圈子小，只要做好課表 JSON 匯出分享，教練之間互相傳送課表便能形成自發性的口碑裂變。
   * **海外華語市場溢出**：台灣 200 位驗證成功後，系統架構乾淨，可低成本直接擴展至香港、新加坡、馬來西亞與北美華語教練圈。

---

## 4. 雙原生平台技術棧規格對照 (Dual Native)

| 模組功能 | iOS / iPadOS 原生技術棧 | Android 原生技術棧 | 共通設計原則 |
| :--- | :--- | :--- | :--- |
| **開發語言** | **Swift 6** | **Kotlin 2.0+** | 現代強型別、空指針安全 |
| **UI 框架** | **SwiftUI** (優先支援 iPad 橫向) | **Jetpack Compose** (橫向平板佈局) | 宣告式 UI、狀態驅動渲染 |
| **架構模式** | **MVVM / MVI** + Swift Concurrency | **MVVM / MVI** + Kotlin Coroutines / Flow | 一致的業務狀態機與資料流 |
| **音訊播放與變速** | **`AVAudioEngine`** + `AVAudioUnitTimePitch` | **`AndroidX Media3`** + `PlaybackParameters` | 原生支援「變速不變調」與雙軌 Crossfade |
| **波形與 BPM 分析** | **`AVAssetReader`** + Swift 峰值運算 | **`MediaExtractor`** + Kotlin 峰值運算 | 非同步讀取音訊 PCM 緩衝區繪製振幅波形 |
| **本機資料儲存** | **`SwiftData`** | **`Room Database`** | 本機 SQLite ORM 關聯資料庫 |
| **設備識別碼** | `UIDevice.identifierForVendor` + Keychain | `Settings.Secure.ANDROID_ID` + KeyStore | 防刪除重裝、保證單機唯一綁定 |
| **授權驗證通訊** | `URLSession` + `Codable` | `Ktor` 或 `Retrofit` + `Kotlinx.serialization` | HTTPS TLS 1.3 串接 Vercel Serverless API |

---

## 5. 後續開發分工與推進里程碑

### Phase 0：Vercel 後端授權服務與官方介紹網頁 (Backend API & Landing Page)
- [x] 建立 `backend/` 全端專案 (Next.js 15 App Router + Tailwind CSS + TypeScript)。
- [x] **官方介紹網頁 (Landing Page)**：
  - 首頁視覺：產品價值、4 大核心功能展示（無損變速、波形 BPM、橫向 HUD、跨平台課表）。
  * 互動定價卡片：月繳 (NT$ 390)、季繳 (NT$ 890)、年繳 (NT$ 2,390)。
  * 常見問題 (FAQ) 與 App 下載引導。
  * 公開法律頁面：隱私權政策 (Privacy Policy) 與服務條款 (Terms of Service / EULA)。
- [x] 建立資料庫抽象層與結構 (`users`, `devices`, `licenses`, `device_transfers`)，支援 Neon/Supabase PostgreSQL 與本地零配置 JSON 雙模。
- [x] 實作核心 API：
  - `POST /api/auth/register` (教練註冊，初始綁定設備，贈送 7 天 VIP)
  - `POST /api/auth/login` (登入與單一設備綁定檢查，非原設備拒絕 403)
  - `POST /api/license/verify` (授權有效期檢查與最後活躍時間更新)
  - `POST /api/device/transfer` (受限換機申請，具備 30 天冷卻限制與舊設備解除綁定)
  - `POST /api/webhooks/revenuecat` (RevenueCat 訂閱購買與續訂事件同步)
- [x] 本地全套端點自動化整合測試通過 (Registration -> Verification -> Mismatch Block -> Transfer -> Migration)。
- [ ] 部署至 Vercel (將 GitHub 倉儲匯入 Vercel 即可完成全自動部署)。

### Phase 1：iOS & Android 雙原生骨架與音訊引擎
- [ ] 建立 `ios/` 與 `android/` 全新原生專案目錄。
- [ ] 實作雙原生音訊引擎（變速播放、Crossfade、波形提取、BPM 分析）。
- [ ] 實作設備唯一識別碼提取與 Vercel 授權驗證串接。

### Phase 2：課程編排器與課堂執行中控 (Class Editor & HUD)
- [ ] 實作本機資料庫與音樂匯入。
- [ ] 實作課程編排器（波形拖曳、Cue 提示點設定、目標 RPM）。
- [ ] 實作橫向大盤播放 HUD（動態倒數環、當前姿勢提示、下一動作預告）。

---

*文件更新時間：2026-09-21*  
*狀態：已納入 Vercel 單機綁定架構、設備識別碼法規因應方案與付費定價商業評估*
