import { Check, Sparkles } from 'lucide-react';
import Link from 'next/link';

export default function Pricing() {
  const plans = [
    {
      name: '月繳方案',
      cycle: '每月自動扣款',
      price: '390',
      period: '/ 月',
      effectivePrice: 'NT$ 390 / 月',
      description: '彈性靈活，適合剛取得證照、兼職代課或想完整體驗產品的教練。',
      popular: false,
      features: [
        '無限建立與編排飛輪課程',
        '高音質無損變速播放 (0.7x ~ 1.3x)',
        '音訊波形圖繪製與 BPM 峰值偵測',
        '全功能橫向車架 HUD 課堂中控台',
        '雙軌歌曲無縫平滑 Crossfade',
        '單一設備授權綁定保障',
      ],
      ctaText: '開始 7 天免費試用',
    },
    {
      name: '季繳方案',
      cycle: '每 3 個月扣款 NT$ 890',
      price: '890',
      period: '/ 季',
      effectivePrice: '折合約 NT$ 296 / 月',
      discount: '現省 24%',
      description: '固定代課或固定課表教練的中期高性價比選擇。',
      popular: false,
      features: [
        '包含月繳方案全部完整功能',
        '換算每月僅需 NT$ 296 元',
        '跨平台 JSON 課表一鍵匯出與備份',
        '新功能優先體驗更新',
        '專屬教練社群與功能優先建議權',
      ],
      ctaText: '開始 7 天免費試用',
    },
    {
      name: '年繳方案',
      cycle: '每年扣款 NT$ 2,390',
      price: '2,390',
      period: '/ 年',
      effectivePrice: '折合僅 NT$ 199 / 月',
      discount: '現省 NT$ 2,290（51折）',
      description: '帶 1~2 堂課即完全回本！全職專屬教練的最佳投資。',
      popular: true,
      features: [
        '包含所有進階功能，全年無限暢用',
        '每日僅需約 6.5 元，省下每週數小時備課心力',
        '跨平台 iPad 與 Android 課表互通交換',
        '離線 100% 穩定授課支援',
        '年約專屬 VIP 優先技術諮詢服務',
      ],
      ctaText: '🔥 立即加入年度教練方案',
    },
  ];

  return (
    <section id="pricing" className="py-20 relative">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto">
          <h2 className="text-xs uppercase tracking-widest text-emerald-400 font-bold">SIMPLE & TRANSPARENT PRICING</h2>
          <p className="mt-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            透明超值的訂閱方案，投資你的專業課堂
          </p>
          <p className="mt-4 text-zinc-400 text-sm sm:text-base">
            新用戶註冊即可享有 <strong className="text-emerald-400">7 天全功能免費 VIP 試用</strong>。試用期滿自由選擇月繳、季繳或最划算的年度方案，App Store / Google Play 一鍵安全訂閱。
          </p>
        </div>

        <div className="mt-16 grid grid-cols-1 lg:grid-cols-3 gap-8 items-stretch">
          {plans.map((plan, index) => (
            <div
              key={index}
              className={`relative flex flex-col justify-between rounded-3xl p-8 transition-all ${
                plan.popular
                  ? 'border-2 border-emerald-500 bg-gradient-to-b from-emerald-950/40 via-zinc-900 to-zinc-950 shadow-2xl shadow-emerald-500/20 lg:-translate-y-2'
                  : 'border border-zinc-800 bg-zinc-900/40 hover:border-zinc-700'
              }`}
            >
              {plan.popular && (
                <div className="absolute -top-4 left-1/2 -translate-x-1/2 rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 px-4 py-1 text-xs font-black text-zinc-950 shadow-md shadow-emerald-500/30 flex items-center gap-1">
                  <Sparkles className="h-3.5 w-3.5" />
                  🔥 飛輪教練首選・最划算
                </div>
              )}

              <div>
                <div className="flex items-center justify-between">
                  <h3 className="text-xl font-bold text-white">{plan.name}</h3>
                  {plan.discount && (
                    <span className="rounded-full bg-emerald-500/10 border border-emerald-500/30 px-2.5 py-0.5 text-xs font-bold text-emerald-400">
                      {plan.discount}
                    </span>
                  )}
                </div>

                <div className="mt-4 flex items-baseline gap-1">
                  <span className="text-sm font-semibold text-zinc-400">NT$</span>
                  <span className="text-4xl sm:text-5xl font-black text-white font-mono">{plan.price}</span>
                  <span className="text-sm text-zinc-400">{plan.period}</span>
                </div>

                <div className="mt-1 flex items-center gap-2">
                  <span className="text-xs font-semibold text-emerald-400 font-mono">{plan.effectivePrice}</span>
                  <span className="text-xs text-zinc-500">({plan.cycle})</span>
                </div>

                <p className="mt-4 text-xs text-zinc-400 leading-relaxed border-t border-zinc-800/80 pt-4">
                  {plan.description}
                </p>

                <ul className="mt-6 space-y-3 text-sm text-zinc-300">
                  {plan.features.map((feature, fIndex) => (
                    <li key={fIndex} className="flex items-start gap-2.5">
                      <Check className="h-4 w-4 text-emerald-400 shrink-0 mt-0.5" />
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="mt-8 pt-6 border-t border-zinc-800/80">
                <Link
                  href="#download"
                  className={`flex w-full items-center justify-center rounded-xl py-3 text-sm font-bold transition-all ${
                    plan.popular
                      ? 'bg-gradient-to-r from-emerald-500 to-teal-400 text-zinc-950 shadow-lg shadow-emerald-500/30 hover:shadow-emerald-500/50 hover:from-emerald-400 hover:to-teal-300'
                      : 'bg-zinc-800 text-white hover:bg-zinc-700'
                  }`}
                >
                  {plan.ctaText}
                </Link>
                <span className="block mt-2 text-center text-[11px] text-zinc-500">
                  透過 App Store / Google Play 安全訂閱，可隨時取消
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
