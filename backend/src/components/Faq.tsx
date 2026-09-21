export default function Faq() {
  const faqs = [
    {
      q: '什麼是「單一設備綁定」？如果我換了新 iPad 怎麼辦？',
      a: '為了維護專業軟體的公平授權，一組 FitnessRider 帳號同一時間僅限綁定並使用於一台主要設備（例如您的教練用 iPad 或 Android 平板）。若您更換了新設備，登入時系統會提供「轉移設備」功能，為防止帳號多人共用作弊，系統設有每 30 天可更換一次的正常轉移機制。',
    },
    {
      q: '在健身房地下室上課時，需要連接網路嗎？',
      a: '完全不需要！課堂進行中的音訊變速、波形滾動、倒數計時與 HUD 指示皆為 100% 設備端本機運算，所有音樂檔案亦儲存於本機沙盒中。App 支援離線授權快取，即使健身房完全無訊號，亦能順暢授課。',
    },
    {
      q: '如何將我的音樂檔案匯入 FitnessRider？',
      a: '您可以將音樂存放在「檔案 App」或「iCloud Drive / Google Drive」，在 App 內點選「匯入音樂」即可一鍵加入；若使用 Mac，亦可直接透過 AirDrop 將 MP3 或 AAC 檔案傳送至 iPad 中直接開啟。',
    },
    {
      q: '我編排好的課表可以分享給其他教練朋友嗎？',
      a: '可以！FitnessRider 採用開放共通的 workout_class.json 格式。您可以點選「匯出課表」，透過 AirDrop、LINE 或 Email 將整堂課的動作時間軸與節奏結構分享給其他教練，對方匯入後即可直接使用。',
    },
    {
      q: '訂閱付款如何進行？可以隨時取消嗎？',
      a: '系統透過 Apple App Store 與 Google Play 官方安全內購機制扣款，支援信用卡、電信帳單或 Apple Pay。您可以在設備的「設定 > 訂閱項目」中隨時查詢或取消續訂，取消後仍可使用至當期結束。',
    },
  ];

  return (
    <section id="faq" className="py-20 bg-zinc-950/60 border-t border-zinc-800/80">
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div className="text-center">
          <h2 className="text-xs uppercase tracking-widest text-emerald-400 font-bold">FREQUENTLY ASKED QUESTIONS</h2>
          <p className="mt-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            常見問題與解答
          </p>
          <p className="mt-3 text-zinc-400 text-sm">
            有其他問題？歡迎隨時透過下方官方信箱聯繫我們。
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
