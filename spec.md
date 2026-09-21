# FitnessRider 系統功能規格規劃書 (spec.md)

*版本：v1.0.0*  
*更新日期：2026-09-21*  
*狀態：正式規格核定*  

---

## 1. 產品概述與核心願景 (Product Overview)

### 1.1 產品定位
**FitnessRider** 是一款專為**室內飛輪教練（Spinning / Indoor Cycling Instructors）**打造的高效能專業教學工具。
本系統聚焦於飛輪課程的核心靈魂：**「音樂拍頻 (BPM) 與動作強度 (RPM / Cues) 的高度同步」**，提供教練極致流暢的**課表編排**、**高音質無損變速播音**以及**課堂即時橫向大螢幕中控引導**。

### 1.2 目標受眾與硬體場景
* **主要受眾**：商業健身房飛輪教練、精品飛輪工作室教練、私人單車訓練師。
* **主力硬體**：
  * **iPad (iPadOS 17+) 橫向模式 (Landscape)**：安裝於飛輪車把支架或教練台大螢幕。
  * **Android 平板 (Android 14+) 橫向模式**：作為跨平台備用與支援設備。
* **市場規模**：台灣約 200 位活躍專業教練，具備高單價、高黏著度之 Prosumer 特性。

---

## 2. 產品模組架構展開 (Module Architecture Breakdown)

FitnessRider 系統拆解為 **8 大核心功能模組**：

```mermaid
graph TD
    subgraph Core_Product_Modules [FitnessRider 產品功能模組全景]
        M1["M1: 課表編排器<br>Class & Segment Editor"]
        M2["M2: 原生音訊變速引擎<br>Native Audio Engine"]
        M3["M3: 波形與 BPM 分析器<br>Waveform & Tempo Analyzer"]
        M4["M4: 課堂即時執行 HUD<br>Workout Player HUD"]
        M5["M5: 課表檔案交換中心<br>JSON Schema Import/Export"]
        M6["M6: 設備綁定與授權檢核<br>Device Binding & Auth"]
        M7["M7: RevenueCat IAP 訂閱管家<br>Subscription & Paywall"]
        M8["M8: 產品官方介紹與入口網頁<br>Official Landing & Web Portal"]
    end

    M1 --> M2
    M1 --> M3
    M1 --> M5
    M4 --> M2
    M4 --> M1
    M6 --> M7
    M4 -.授權檢驗.-> M6
    M8 -->|提供產品下載/定價展示| M7
    M8 -->|提供隱私權與條款公開網址| M6
```

---

## 3. 各模組詳細功能規格 (Functional Specifications)

### 模組 M1：課表與段落編排器 (Class & Segment Editor)
教練用以規劃整堂 45~60 分鐘課堂之結構化工具。

* **M1.1 課程基本資訊**：
  * 課程標題（如「45min 燃脂間歇衝刺」）、難度等級、教練名稱。
  * 系統自動即時累計：總時長、總段落數、理論預估卡路里（依段落強度時間積分計算）。
* **M1.2 歌曲段落 (Segment) 管理**：
  * 支援單首歌曲對應一個訓練段落。
  * 拖曳排序（Drag & Drop）支援任意調整曲目順序。
  * 設定該段落的主要訓練目標：平路巡航 (Cruising)、爬坡 (Climbing)、衝刺間歇 (Sprint)、緩和恢復 (Recovery)。
* **M1.3 動作提示點 (Cue) 精準標記**：
  * 於歌曲時間軸任意秒數放置提示點。
  * 提示項目包含：
    * **騎乘姿勢**：坐姿 (Seated)、站姿 (Standing)、抽車 (Jumping)。
    * **阻力/踏頻指示**：目標 RPM (如 60~110 RPM)、阻力微調指令（如「右旋一圈半」）。
    * **自訂文字提醒**：如「最後 30 秒爆發，堅持住！」

---

### 模組 M2：原生音訊變速與節奏引擎 (Native Audio Engine)
飛輪教學中，教練經常需要微調音樂速度以精準契合學員踩踏節奏。

* **M2.1 變速不變調 (Pitch-Preserving TimePitch)**：
  * 支援 0.7x ~ 1.3x 連續無損速度調整（步進 0.01x 或以 BPM 為單位同步增減）。
  * 保持原曲音調（人聲不變尖、低音不失真）。
  * **iOS**：基於 `AVAudioEngine` + `AVAudioUnitTimePitch`。
  * **Android**：基於 `AndroidX Media3 (ExoPlayer)` + `PlaybackParameters`。
* **M2.2 雙軌平滑淡入淡出 (Crossfade)**：
  * 歌曲換首時自動無縫交錯淡入淡出（可設定 1~5 秒），避免課堂中出現尷尬靜音。
* **M2.3 系統級背景播放**：
  * 宣告 Audio Background Mode，App 進入背景或螢幕鎖定時音樂不中斷。
  * 整合系統控制中心與鎖定畫面（iOS `MPRemoteCommandCenter` / Android `MediaSession`）。

---

### 模組 M3：波形視覺化與 BPM 偵測 (Waveform & Tempo Analyzer)
* **M3.1 非同步音訊解碼與波形生成**：
  * 匯入音訊時，非同步讀取 PCM 緩衝區，降採樣計算振幅分佈。
  * 產出高解析度波形資料陣列，於編輯器以視網膜級流暢度滾動渲染。
* **M3.2 音樂拍頻 (BPM) 偵測**：
  * 移植並優化純數學峰值偵測演算法（Onset/Beat Peak Detection）。
  * 分析出歌曲原始 BPM（如 128 BPM），並支援教練手動 Tap-Tempo（輕敲測速）校正。

---

### 模組 M4：課堂即時執行中控儀表板 (Workout Player HUD)
課堂進行時教練放置於車架上的核心互動介面，採用全橫向大字高對比設計。

* **M4.1 視覺層次佈局 (Landscape Layout)**：
  * **左側/中央核心區**：
    * 動態環形倒數環：當前 Segment 剩餘時間倒數。
    * 當前核心指示：大字顯示當前目標 **RPM**、姿勢圖示（坐/站）、強度色帶（Zone 1 藍 ~ Zone 5 紅）。
    * 下一動作預告（Next Cue Preview）：提前 5~10 秒高亮閃爍提示（如「預備：5 秒後站姿爬坡」）。
  * **上方進度條**：全課堂時間軸進度、總消耗卡路里即時累加器。
  * **下方控音列**：大按鈕播放/暫停、快進/倒退、即時變速滑桿（Slider）與即時 BPM 顯示。
* **M4.2 課堂中斷防護**：
  * 課堂進行中強制保持螢幕常亮（Idle Timer Disabled）。
  * 意外跳出防護：若誤觸返回需彈窗二次確認，避免授課中斷。

---

### 模組 M5：課表檔案交換中心 (JSON Schema Import/Export)
* **M5.1 共通 JSON 課表規範 (`workout_class.json`)**：
  * 格式跨雙原生平台 100% 相容。
* **M5.2 一鍵分享與匯入**：
  * 支援 iOS AirDrop、檔案 App、LINE 分享課表檔案。
  * 匯入時自動比對本機是否存在對應音訊檔；若缺音訊檔，提示教練綁定本機同名歌曲。

---

### 模組 M6：雲端授權檢核與單一設備綁定 (Device Binding & Auth)
確保一組帳號限定一台設備使用，保障付費權益。

* **M6.1 現代設備唯一識別碼提取**：
  * **iOS**：`UIDevice.identifierForVendor` + **iOS Keychain 持久化保存**（防刪除 App 重裝作弊）。
  * **Android**：`Settings.Secure.ANDROID_ID` + **KeyStore** 安全存儲。
* **M6.2 Vercel Serverless 單機綁定檢核**：
  * 登入時送出 `{ email, password, device_fingerprint, device_model }`。
  * 首次登入自動綁定；若在第二台設備登入，回傳 `403 DEVICE_MISMATCH` 阻擋使用。
  * 提供「換機轉移設備」流程（限制每 30 天最多 1 次，防止共用）。

---

### 模組 M7：RevenueCat 跨平台 IAP 訂閱管家 (Subscription & Paywall)
* **M7.1 三層級訂閱方案**：
  1. **月繳**：NT$ 390 / 月
  2. **季繳**：NT$ 890 / 季 (約 NT$ 296 / 月)
  3. **年繳 (主力)**：NT$ 2,390 / 年 (約 NT$ 199 / 月)
* **M7.2 整合流程**：
  * App 端引入 RevenueCat SDK，以極簡 API 呼叫 Apple StoreKit 2 與 Google Play Billing。
  * 透過 RevenueCat Webhook 即時將付款/續訂事件推播至 Vercel 後端，更新資料庫 `licenses` 到期日。

---

### 模組 M8：產品官方介紹與服務入口網頁 (Official Landing Page & Web Portal)
架設於 Vercel 的現代化官方品牌響應式入口網站（同時作為 App Store 審查必備之支援與法律文件公開站點）。

* **M8.1 視覺首頁 (Hero & Product Showcase)**：
  * 產品標題與核心價值主張（「專為室內飛輪教練打造的音樂節奏與課表編排引擎」）。
  * 核心功能亮點卡片：
    * 🎵 **無損變速不變調**：自訂節奏契合踩踏踏頻。
    * ⏱️ **橫向大字課堂 HUD**：動態倒數環、當前姿勢與下一動作預告。
    * 📊 **視覺化波形與 BPM**：自動偵測歌曲節拍與音訊振幅。
    * 📱 **雙平台共通課表**：iPad 與 Android 平板隨意切換、課表一鍵分享。
  * App 下載引導（App Store / Google Play 徽章按鈕）。
* **M8.2 訂閱價格試算卡片 (Interactive Pricing Tiers)**：
  * 視覺化月繳 (NT$ 390)、季繳 (NT$ 890)、年繳 (NT$ 2,390) 三卡片。
  * 年繳方案標記「🔥 飛輪教練首選・現省 NT$ 2,290」。
  * 明確列出訂閱包含權益：無限課表建立、無損變速播放、全功能 HUD、課表備份匯出。
* **M8.3 教練常見問題 (FAQ)**：
  * 涵蓋：單一設備綁定如何運作？換新 iPad 如何轉移？音樂需要連上網才能播嗎（100% 離線可用）？
* **M8.4 App Store 審查強制必備之公開法律頁面**：
  * **隱私權政策 (Privacy Policy)**：載明不搜集多餘個資、僅使用設備識別碼做授權綁定。
  * **使用者服務條款 (Terms of Service / EULA)**：載明訂閱週期、自動續約條款與退訂方式。
  * **支援與客服聯繫 (Support Contact)**：提供教練反饋管道（Email / LINE 官方諮詢）。

---

## 4. 共通資料格式規範 (JSON Data Contract)

```json
{
  "$schema": "https://fitnessrider.app/schemas/workout_class_v1.json",
  "version": "1.0",
  "classId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "45min 高強度間歇爬坡",
  "author": "Coach Tony",
  "createdAt": "2026-09-21T08:00:00Z",
  "totalDurationMs": 2700000,
  "estimatedCalories": 420.5,
  "segments": [
    {
      "segmentId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "orderIndex": 0,
      "title": "熱身與節奏平路",
      "musicFileName": "warmup_track.mp3",
      "durationMs": 300000,
      "baseBpm": 128.0,
      "playbackRate": 1.0,
      "intensityZone": 2,
      "cues": [
        {
          "cueId": "cue-01",
          "offsetMs": 0,
          "posture": "SEATED_FLAT",
          "targetRpm": 80,
          "resistanceLevel": "LIGHT",
          "message": "坐姿平路，輕鬆踩踏建立踏頻"
        },
        {
          "cueId": "cue-02",
          "offsetMs": 120000,
          "posture": "STANDING_CLIMB",
          "targetRpm": 65,
          "resistanceLevel": "HEAVY",
          "message": "起立站姿爬坡，增加兩圈阻力"
        }
      ]
    }
  ]
}
```

---

## 5. 非功能性需求與指標 (Non-Functional Requirements)

| 指標類別 | 規格標準 | 驗證方式 |
| :--- | :--- | :--- |
| **音訊播放延遲** | 變速或 Seeking 響應時間 < 50ms | `AVAudioEngine` 緩衝區監控 |
| **介面幀率 (FPS)** | 橫向 HUD 動畫與波形滾動恆定 60 FPS (ProMotion 120 FPS) | Instruments Core Animation 檢測 |
| **離線授權寬限** | 支援最長 7 天離線無網路使用（離線快取 JWT 憑證） | 斷網模式驗證課堂執行功能 |
| **記憶體佔用** | 60 分鐘課堂音訊播放記憶體穩定 < 120 MB | Instruments Allocations / Leaks 檢測 |
| **設備相容性** | iPadOS 17+ (全螢幕橫向) / Android 14+ (平板橫向) | 實機測試 |

---

## 6. Google Stitch 現代化視覺設計系統與原型 (UI Design System & Screen Mockups)

以舊版業務邏輯為骨幹，重構為**純白與活力綠風格（強調健康與運動熱情）**的現代飛輪座艙設計系統 **Vital Green & Crisp White**：

* **Stitch Project ID**：`16053146227512897618`
* **Design System Asset**：`assets/11077945758532214833` (Vital Green & Crisp White)
* **核心品牌語彙與設計規格**：
  * **底色 (Canvas Background)**：純淨純白 (`#FFFFFF`)，搭配乾淨俐落的極淺灰板塊 (`#F8FAFC`, 邊框 `#E2E8F0`)，傳達專業健身工作室的明亮、健康與通透質感。
  * **主強調色 (Health & Vitality Green)**：活力運動綠 (`#16A34A` / `#22C55E`)，象徵生命力、健康踩踏、目標踏頻達成與正向反饋。
  * **熱情與極限色 (Passion & Peak Intensity)**：熱情暖橙與珊瑚紅 (`#F97316` / `#EF4444`)，用於高阻力爬坡、全力衝刺、區間倒數警示，激發熱血運動熱情。
  * **高對比排版文字**：深板岩碳黑 (`#0F172A` / `#1E293B`) 與中度石板灰 (`#64748B`)，在白底上達成極高對比與遠距離防汗眩光辨識度。
  * **字型階層**：
    * 標題與核心數據：`Plus Jakarta Sans`（等寬數字 Tabular digits，數字跳動零抖動，線條現代俐落）。
    * 內文與標籤：`Inter`（高可讀性、抗眩光）。
    * 時間碼與技術參數：`JetBrains Mono`。
  * **觸控人體工學**：主控制按鈕全面擴大為 **72pt+** 圓形大觸發區，防止車架劇烈晃動時誤觸。

### 核心畫面原型 (Screen Mockups - Vital Green & Crisp White)

#### 1. 課堂即時執行 HUD (Workout Cockpit HUD - Vital Green)
* **Screen ID**：`f6310c9f80564d4db61653f1f9da0a4e`
* **設計亮點**：
  * **純白明亮儀表**：頂部多階段彩色課堂進度條（暖身/爬坡/衝刺/緩和）與剩餘總時間，右上方整合電量、螢幕鎖與 VIP 狀態。
  * **左半部音訊與 Cue 點軌道**：純白底色搭配運動綠音訊振幅波形與當前播放指針；上方醒目提示卡片：`NEXT IN 00:15: STANDING CLIMB (65-75 RPM, RESISTANCE 6)`。
  * **右半部雙主控儀表**：超大字體即時目標踏頻（`110 RPM`）搭配活力綠圓形動態儀表；`00:45` 區間倒數計時器搭配熱情橙紅脈衝邊框；坐姿衝刺姿態徽章與阻力段位 (`Level 7 / 20`)。
  * **底部操控台**：72pt 活力綠圓形 Play/Pause 按鈕、無損 TimePitch 變速滑桿（`+0%`, `+2%`, `-2%` 快捷微調，音調鎖定不變調）。

#### 2. 課表編排與波形節奏標記器 (Class & Waveform Editor - Vital Green)
* **Screen ID**：`fc2b83574e354efcb3daeb1b0d50bacb`
* **設計亮點**：
  * **左側 35% 歌曲清單**：乾淨白底卡片序列，清楚顯示歌曲編號、時長、原始 BPM 與變速後目標 BPM（如 `132 BPM (+3%)`），作用中歌曲以活力綠邊框與淡綠底色突顯。
  * **右側 65% 音訊波形工作區**：
    * 運動綠與深岩色波形畫布，支援時間軸縮放與播放游標精確微調。
    * 波形上清楚標記 Cue 點旗標（暖身綠、爬坡橙、衝刺紅、緩和薄荷綠）。
    * 專屬 **Cue Point Inspector** 編輯面板：動作名稱、開始時間點（支援 `±0.5s` 節拍微調）、騎乘姿勢切換、目標踏頻範圍、建議阻力段位與即時變速試聽播放器。

#### 3. 課表總覽與庫存清單 (Class Library - Vital Green)
* **Screen ID**：`fd3e55a3253e40a7b5fa02d1644d5af2`
* **設計亮點**：
  * **3x2 課表卡片網格**：白底質感卡片，展示每套課表時長、曲數、運動強度等級條、爬坡爬升模擬圖、一鍵綠色「開始課堂 (Start Ride)」與「編輯課表 (Edit)」。
  * **頂部搜尋與分類過濾**：活力綠色選中標籤（全部、HIIT/衝刺、重爬坡、耐力、緩和、收藏）、`+ Create New Class` 綠色按鈕與 `Import JSON`。
  * **底部狀態列**：課表統計數據、音訊引擎連線狀態、VIP 授權剩餘天數與當前綁定設備狀態 (`iPad Air 5 • This Device`)。

---

*文件產出完畢。後續 Native 開發需嚴格遵照本功能規格書與 Stitch 設計系統規範。*
