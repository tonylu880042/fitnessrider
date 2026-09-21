export default function Faq() {
  const faqs = [
    {
      q: '新用戶有哪些免費試用權益？如何使用推廣課程專屬代碼取得 30 天免費？',
      a: '新用戶首次在 iPad 或 Android 平板下載並啟動 FitnessRider，即自動享有 7 天全功能免費 VIP 試用，無需預先綁定信用卡。若您參加了官方認證或合作的飛輪師資培訓推廣課程，可向授課講師索取年度專屬推廣代碼（2026 年度專屬代碼為：26FR-NR）。在 App「設定 > 輸入授權序號或推廣代碼」中填入該代碼，每台設備即可立即解鎖 30 天全功能免費 VIP 體驗！為維護授權公平，每台實體設備針對該年度專屬代碼限兌換一次。',
    },
    {
      q: '什麼是「單一設備綁定」？如果我換了新 iPad 或 Android 平板怎麼辦？',
      a: '為了維護專業軟體的公平授權，一組 FitnessRider 帳號或 VIP 序號同一時間僅限綁定於一台主要教學設備。若您更換了新平板，在登入或過期畫面點擊「🔄 轉移設備授權」，即可選擇透過「會員帳密」或「原 VIP 序號」一鍵將授權遷入本設備，舊設備將自動停用。為保障單機授權政策、防範多人共用序號，系統設有每 30 天允許更換一次設備的正常轉移冷卻保護機制。',
    },
    {
      q: '在健身房地下室或防空避難室上課時，需要連接網路嗎？',
      a: '完全不需要！FitnessRider 專為商業健身房極端環境打造。課堂進行中的音訊 TimePitch 變速、雙軌 Crossfade、波形滾動、倒數計時與大字 HUD 指示皆為 100% 設備本機運算，所有音樂檔案亦儲存於本機沙盒中。App 支援離線授權快取，即使健身房完全無 Wi-Fi 與 4G/5G 訊號，亦能長效順暢授課。',
    },
    {
      q: '什麼是「.riderclass」完整課表包？如何分享給代課教練？',
      a: '這是 FitnessRider 專為主教練與代課教練設計的殺手級功能。在課表清單點選「📦 匯出完整課表包 (.riderclass)」，App 會自動將該課表的動作時間軸、RPM 踏頻指示、阻力等級與該課表使用到的所有音樂檔案（MP3/M4A）一鍵打包壓縮為單一檔案。收到檔案的教練只需點開，App 就會自動解壓縮音樂至本地音樂庫並建立課表，0 門檻直接開騎授課！',
    },
    {
      q: '雙軌「等能量平滑 Crossfade」如何運作？會不會影響我的動作倒數？',
      a: '傳統播音在切歌時常有 2~5 秒尷尬靜音，造成踩踏節奏中斷。FitnessRider 內建雙原生 Audio Engine 播放核心，支援在曲目結尾與下一首開頭進行 1~3 秒等能量（Equal-Power）聲學交叉淡入淡出，踩踏能量不中斷。系統同時提供「曲目段落結束自動暫停 (Auto-Pause)」開關，開啟時 Crossfade 自動智慧互斥避讓，滿足需要間歇技術解說的特殊課堂需求。',
    },
    {
      q: '支援哪些設備與系統版本？',
      a: '支援 iPad (iPadOS 17 及以上版本，推薦 11 吋或 13 吋橫向安裝於車把大支架) 與 Android 平板 (Android 14 及以上版本)。手機端（iPhone / Android 手機）亦可自適應響應版面，方便教練輕量隨身檢視課表。',
    },
    {
      q: '訂閱付款如何進行？可以隨時取消嗎？',
      a: '系統透過 Apple App Store 與 Google Play 官方安全內購機制扣款，支援信用卡、電信帳單或 Apple Pay。您可以在設備的「設定 > 訂閱項目」中隨時查詢或取消續訂，取消後仍可繼續享有專業版功能至當期結束，完全無綁約壓力。',
    },
  ];

  return (
    <section id="faq" className="py-20 bg-zinc-950/60 border-t border-zinc-800/80">
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div className="text-center">
          <h2 className="text-xs uppercase tracking-widest text-emerald-400 font-bold">FREQUENTLY ASKED QUESTIONS</h2>
          <p className="mt-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            飛輪教練常見問題與解答
          </p>
          <p className="mt-3 text-zinc-400 text-sm">
            還有其他問題？歡迎隨時透過下方官方客服信箱聯繫我們，專人快速回覆。
          </p>
        </div>

        <div className="mt-12 space-y-4">
          {faqs.map((faq, index) => (
            <details
              key={index}
              className="group rounded-2xl border border-zinc-800 bg-zinc-900/50 p-6 [&_summary::-webkit-details-marker]:hidden open:border-emerald-500/50 open:bg-zinc-900 transition-all"
            >
              <summary className="flex cursor-pointer items-center justify-between text-base font-bold text-white group-hover:text-emerald-400 transition-colors">
                <span>{faq.q}</span>
                <span className="ml-4 shrink-0 rounded-full bg-zinc-800 p-1.5 text-zinc-400 group-open:rotate-180 group-open:bg-emerald-500 group-open:text-zinc-950 transition-all">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M19 9l-7 7-7-7" />
                  </svg>
                </span>
              </summary>
              <p className="mt-4 text-sm text-zinc-400 leading-relaxed border-t border-zinc-800/60 pt-4">
                {faq.a}
              </p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
