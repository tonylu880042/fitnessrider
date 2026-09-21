# FitnessRider 官方推廣培訓專屬代碼與試用政策手冊 (Promo Code Policy & Annual Maintenance)

本文件為 **FitnessRider 培訓課程專屬推廣代碼** 之官方維護指引與權威規格說明。

---

## 1. 當前年度生效代碼 (Active Promo Code)

| 項目 | 規格與數值 |
| :--- | :--- |
| **生效年度** | **2026 年** |
| **專屬推廣代碼** | **`26FR-NR`** |
| **享有權益** | **30 天全功能免費 VIP 試用**（未輸入代碼之基準體驗為 7 天） |
| **防刷限制 (Anti-Abuse)** | **單機唯一兌換**（每台實體設備針對該代碼限領取一次） |
| **適用對象** | 官方認證室內飛輪師資培訓學員、推廣合作教室與認證教練 |
| **大小寫支援** | 支援大小寫不拘（自動轉為大寫比對）、自動忽略前後空白 |

---

## 2. 代碼命名規範與年度更迭機制 (Annual Rotation Rule)

推廣代碼具備 **年度動態更迭規範**，格式為：
$$\mathbf{YY}\text{FR-NR}$$

- **`YY`**：西元紀年之後兩位數字（例如 2026 年為 `26`、2027 年為 `27`）。
- **`FR`**：**FitnessRider** 產品簡寫。
- **`NR`**：**New Rider / National Rider**（飛輪培訓新學員代號）。

### 歷年與未來代碼對照表

| 年度 | 專屬推廣代碼 | 生效起訖日 | 狀態 |
| :--- | :--- | :--- | :--- |
| **2026 年** | **`26FR-NR`** | 2026-01-01 ~ 2026-12-31 | 🟢 **目前生效中 (Active)** |
| 2027 年 | `27FR-NR` | 2027-01-01 ~ 2027-12-31 | ⚪ 次年度預備代碼 |
| 2028 年 | `28FR-NR` | 2028-01-01 ~ 2028-12-31 | ⚪ 未來代碼 |
| 2029 年 | `29FR-NR` | 2029-01-01 ~ 2029-12-31 | ⚪ 未來代碼 |
| 2030 年 | `30FR-NR` | 2030-01-01 ~ 2030-12-31 | ⚪ 未來代碼 |

> [!TIP]
> **舊設備跨年再次領取機制**：
> 單機防重複驗證是**綁定代碼本身**（以 `device_fingerprint + promo_code` 作為唯一鍵）。
> 當年度更替（如由 `26FR-NR` 更換為 `27FR-NR`）時，舊學員設備在新年度參與新課程時，輸入新代碼 `27FR-NR` 依然可以順利領取新年度的 30 天 VIP！這既能杜絕同一年內重複洗試用期，又能照顧教練回訓與新年度授課的需求。

---

## 3. 超過一年自動提醒與檢查機制 (Expiration Alert)

為了防止代碼超過一年未更新而影響新學員領取，專案建置了多層級自動提醒機制：

### 3.1 終端機檢測工具 (`tools/check_promo_code.sh`)

任何時候皆可執行以下指令檢查代碼有效性：

```bash
./tools/check_promo_code.sh
```

檢測邏輯包含三種狀態：
1. **正常有效**：目前年份等於設定年份（2026），顯示代碼生效中。
2. **即將更替提醒 (每年 12 月)**：若進入 12 月（倒數 30 天內），終端機會主動輸出：
   ```text
   ⚠️ 【即將交更提醒】當前年度僅剩最後一個月，請預備更新次年度推廣代碼！
   ```
3. **超過一年過期警告 (年份 > 2026)**：若跨入新年度但專案尚未更新，會跳出高亮醒目紅色警報：
   ```text
   🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨
   【重大警告】推廣代碼已過期超過一年！
   目前為 2027 年，但專案設定仍為 2026 年度代碼 [26FR-NR]！
   建議立即更新為當前年度專屬代碼: [27FR-NR]
   🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨
   ```

### 3.2 發布流程自動審計 (Firebase App Distribution Hook)

每次執行發布腳本 `./tools/upload_to_firebase.sh` 時，系統會在編譯前自動執行代碼年度審計，確保每一次發布至測試人員的 App 版本都經過代碼效期確認，絕不遺漏。

---

## 4. 年度代碼更新標準作業程序 (SOP Checklist)

當跨入新年度（或每年 12 月底預先交更）時，請依照下列檢查表進行更新：

- [ ] **1. 更新代碼設定檔**：
  - 編輯 [`promo.properties`](file:///Users/tunghunglu/projects/fitnessrider/promo.properties)，將 `PROMO_YEAR` 與 `PROMO_CODE` 改為新年度（如 `2027` 與 `27FR-NR`）。
- [ ] **2. 更新後端與 API**：
  - 檢視 [`backend/src/lib/db.ts`](file:///Users/tunghunglu/projects/fitnessrider/backend/src/lib/db.ts)，更新預設代碼常數。
- [ ] **3. 更新官方網站文案**：
  - [`backend/src/components/Hero.tsx`](file:///Users/tunghunglu/projects/fitnessrider/backend/src/components/Hero.tsx)
  - [`backend/src/components/Pricing.tsx`](file:///Users/tunghunglu/projects/fitnessrider/backend/src/components/Pricing.tsx)
  - [`backend/src/components/DownloadSection.tsx`](file:///Users/tunghunglu/projects/fitnessrider/backend/src/components/DownloadSection.tsx)
  - [`backend/src/components/Faq.tsx`](file:///Users/tunghunglu/projects/fitnessrider/backend/src/components/Faq.tsx)
- [ ] **4. 更新雙平台原生 App 提示**：
  - **iOS**：[`VersionLifecycleManager.swift`](file:///Users/tunghunglu/projects/fitnessrider/ios/FitnessRider/App/VersionLifecycleManager.swift)、[`SettingsBackupView.swift`](file:///Users/tunghunglu/projects/fitnessrider/ios/FitnessRider/Views/Settings/SettingsBackupView.swift)、[`VersionExpiredView.swift`](file:///Users/tunghunglu/projects/fitnessrider/ios/FitnessRider/App/VersionExpiredView.swift)
  - **Android**：[`VersionLifecycleManager.kt`](file:///Users/tunghunglu/projects/fitnessrider/android/app/src/main/java/com/fitnessrider/util/VersionLifecycleManager.kt)、[`SettingsScreen.kt`](file:///Users/tunghunglu/projects/fitnessrider/android/app/src/main/java/com/fitnessrider/ui/settings/SettingsScreen.kt)、[`VersionExpiredScreen.kt`](file:///Users/tunghunglu/projects/fitnessrider/android/app/src/main/java/com/fitnessrider/ui/expiration/VersionExpiredScreen.kt)
- [ ] **5. 執行驗證與測試**：
  - 執行 `./tools/check_promo_code.sh` 確認年度檢查全數亮綠燈。
  - 執行 iOS / Android 單元測試與 Next.js 建置。
- [ ] **6. 部署上線**：
  - 提交 Git Commit 並推播至 GitHub `origin/main`，Vercel 與雙端 App 同步更新生效。

---

## 5. 個人行事曆提醒設定建議

建議在 Google Calendar 或 Apple 日曆中建立以下循環提醒：
- **提醒標題**：FitnessRider 年度推廣代碼更新 (`YYFR-NR`)
- **建議日期**：每年 12 月 15 日
- **週期**：每年重複一次
- **備註內容**：請參閱本手冊 `docs/PROMO_CODES.md`，將推廣代碼推進為下一年度代碼。
