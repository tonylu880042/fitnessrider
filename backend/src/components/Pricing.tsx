'use client';

import { useState } from 'react';
import { Check, Sparkles, Calculator, Flame, ShieldCheck } from 'lucide-react';
import Link from 'next/link';

export default function Pricing() {
  const [classesPerWeek, setClassesPerWeek] = useState(3);
  const [hourlyRate, setHourlyRate] = useState(1000);

  // Annual calculation
  const annualEarnings = classesPerWeek * 50 * hourlyRate;
  const yearlyPlanPrice = 2390;
  const classesToBreakEven = Math.max(1, Math.ceil(yearlyPlanPrice / hourlyRate));
  const annualHoursSaved = classesPerWeek * 50 * 1; // 1 hour saved per class

  const plans = [
    {
      name: '月繳方案',
      cycle: '每月自動扣款',
      price: '390',
      period: '/ 月',
      effectivePrice: 'NT$ 390 / 月',
      annualCost: 'NT$ 4,680 / 年',
      description: '彈性靈活，適合剛取得證照、兼職代課或短期體驗的教練。',
      popular: false,
      features: [
        '30 天全功能免費試用（不扣款）',
        '無限建立與編排飛輪課程',
        '高音質無損變速 (0.7x ~ 1.3x)',
        '音訊波形圖與 BPM 自動峰值偵測',
        '全功能橫向車架 HUD 課堂中控台',
        '1~3 秒等能量雙軌平滑 Crossfade',
        '單一設備授權綁定保障',
      ],
      ctaText: '開始 30 天免費試用',
    },
    {
      name: '季繳方案',
      cycle: '每 3 個月扣款 NT$ 890',
      price: '890',
      period: '/ 季',
      effectivePrice: '折合約 NT$ 296 / 月',
      annualCost: 'NT$ 3,560 / 年',
      discount: '省下 24%',
      description: '固定代課或固定課表教練的中期高性價比選擇。',
      popular: false,
      features: [
        '包含月繳方案全部完整功能',
        '換算每月僅需 NT$ 296 元',
        '跨平台 JSON 課表一鍵匯出與備份',
        '新功能優先體驗更新',
        '專屬教練社群與功能優先建議權',
      ],
      ctaText: '開始 30 天免費試用',
    },
    {
      name: '年繳方案',
      cycle: '每年扣款 NT$ 2,390',
      price: '2,390',
      period: '/ 年',
      effectivePrice: '折合僅 NT$ 199 / 月',
      annualCost: 'NT$ 2,390 / 年',
      discount: '🔥 現省 NT$ 2,290 (51折)',
      description: '帶 1~2 堂課即 100% 回本！全職與專職教練的首選投資。',
      popular: true,
      features: [
        '包含所有進階功能，全年無限暢用',
        '折合每日僅需 6.5 元，省下每週數小時備課心力',
        '跨平台 iPad 與 Android 課表一鍵交換',
        '100% 離線穩定授課支援，地下室零延遲',
        'M6.3 換機 30 天冷卻跨設備一鍵轉移',
        '年約專屬 VIP 優先技術諮詢服務',
      ],
      ctaText: '🔥 立即加入年度教練 VIP',
    },
  ];

  return (
    <section id="pricing" className="py-20 relative overflow-hidden">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto">
          <h2 className="text-xs uppercase tracking-widest text-emerald-400 font-bold">SIMPLE & TRANSPARENT PRICING</h2>
          <p className="mt-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            透明超值的訂閱方案，投資你的專業課堂
          </p>
          <p className="mt-4 text-zinc-400 text-sm sm:text-base">
            新用戶登入即享 <strong className="text-emerald-400">30 天全功能免費 VIP 試用</strong>，不需預先綁定信用卡。試用期滿自由選擇月繳、季繳或最划算的年度方案，App Store / Google Play 一鍵安全訂閱。
          </p>
        </div>

        {/* Pricing Cards */}
        <div className="mt-16 grid grid-cols-1 lg:grid-cols-3 gap-8 items-stretch">
          {plans.map((plan, index) => (
            <div
              key={index}
              className={`relative flex flex-col justify-between rounded-3xl p-8 transition-all ${
                plan.popular
                  ? 'border-2 border-emerald-500 bg-gradient-to-b from-emerald-950/40 via-zinc-900 to-zinc-950 shadow-2xl shadow-emerald-500/20 lg:-translate-y-2 ring-1 ring-emerald-500/50'
                  : 'border border-zinc-800 bg-zinc-900/40 hover:border-zinc-700'
              }`}
            >
              {plan.popular && (
                <div className="absolute -top-4 left-1/2 -translate-x-1/2 rounded-full bg-gradient-to-r from-emerald-500 to-teal-400 px-4 py-1 text-xs font-black text-zinc-950 shadow-md shadow-emerald-500/30 flex items-center gap-1">
                  <Sparkles className="h-3.5 w-3.5" />
                  🔥 飛輪教練首選・現省 NT$ 2,290
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

                <p className="mt-2 text-xs text-zinc-400">{plan.description}</p>

                <div className="mt-6 flex items-baseline gap-1">
                  <span className="text-sm font-semibold text-zinc-400">NT$</span>
                  <span className="text-4xl sm:text-5xl font-extrabold text-white tracking-tight font-mono">{plan.price}</span>
                  <span className="text-sm text-zinc-400">{plan.period}</span>
                </div>

                <div className="mt-2 text-xs font-medium text-emerald-400">
                  {plan.effectivePrice}
                  <span className="text-zinc-500 ml-2">({plan.cycle})</span>
                </div>

                <div className="mt-8 border-t border-zinc-800/80 pt-6">
                  <span className="text-xs font-bold uppercase tracking-wider text-zinc-300">方案包含權益：</span>
                  <ul className="mt-4 space-y-3">
                    {plan.features.map((feature, fIdx) => (
                      <li key={fIdx} className="flex items-start gap-3 text-xs sm:text-sm text-zinc-300">
                        <Check className="h-4 w-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{feature}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="mt-8">
                <Link
                  href="#download"
                  className={`w-full flex items-center justify-center rounded-xl py-3 text-sm font-bold transition-all ${
                    plan.popular
                      ? 'bg-gradient-to-r from-emerald-500 to-teal-400 text-zinc-950 shadow-lg shadow-emerald-500/25 hover:from-emerald-400 hover:to-teal-300 hover:shadow-emerald-500/40'
                      : 'bg-zinc-800 text-white hover:bg-zinc-700'
                  }`}
                >
                  {plan.ctaText}
                </Link>
                <div className="mt-2 text-center text-[11px] text-zinc-500 flex items-center justify-center gap-1">
                  <ShieldCheck className="h-3 w-3 text-emerald-400" />
                  <span>30 天免費試用，隨時可於商店取消</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Interactive Coach ROI Calculator */}
        <div className="mt-16 rounded-3xl border border-zinc-800 bg-zinc-900/60 p-6 sm:p-10 backdrop-blur-md">
          <div className="flex flex-col lg:flex-row items-center justify-between gap-8">
            <div className="max-w-xl">
              <div className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-xs font-bold text-amber-400 mb-3">
                <Calculator className="h-3.5 w-3.5" />
                <span>教練授課收益與時間成本試算</span>
              </div>
              <h3 className="text-2xl font-bold text-white">
                年繳方案需要帶幾堂課才能回本？
              </h3>
              <p className="mt-2 text-sm text-zinc-400 leading-relaxed">
                以全台商業健身房平均每堂飛輪課鐘點費 NT$ 800 ~ 1,500 計算，每年僅需帶 1 ~ 2 堂課即可全額賺回軟體年費，剩下的 50 週全是純收益與倍增的教學效率！
              </p>

              {/* Sliders */}
              <div className="mt-6 space-y-4">
                <div>
                  <div className="flex justify-between text-xs font-semibold text-zinc-300 mb-1.5">
                    <span>每週授課堂數：</span>
                    <span className="font-mono text-emerald-400 font-bold">{classesPerWeek} 堂 / 週</span>
                  </div>
                  <input
                    type="range"
                    min="1"
                    max="10"
                    step="1"
                    value={classesPerWeek}
                    onChange={(e) => setClassesPerWeek(Number(e.target.value))}
                    aria-label="每週授課堂數"
                    className="w-full accent-emerald-500 h-2 bg-zinc-800 rounded-lg cursor-pointer"
                  />
                  <div className="flex justify-between text-[10px] text-zinc-500 font-mono mt-1">
                    <span>1 堂 (兼職)</span>
                    <span>5 堂 (常規)</span>
                    <span>10 堂 (全職主教練)</span>
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-xs font-semibold text-zinc-300 mb-1.5">
                    <span>每堂平均課鐘費 (含人頭獎金)：</span>
                    <span className="font-mono text-emerald-400 font-bold">NT$ {hourlyRate.toLocaleString()}</span>
                  </div>
                  <input
                    type="range"
                    min="600"
                    max="2000"
                    step="100"
                    value={hourlyRate}
                    onChange={(e) => setHourlyRate(Number(e.target.value))}
                    aria-label="每堂平均課鐘費"
                    className="w-full accent-emerald-500 h-2 bg-zinc-800 rounded-lg cursor-pointer"
                  />
                </div>
              </div>
            </div>

            {/* Calculated Results */}
            <div className="w-full lg:w-80 rounded-2xl border border-zinc-800 bg-zinc-950 p-6 text-center shadow-xl">
              <span className="text-xs uppercase tracking-wider text-zinc-400 font-medium">投資年回報率 (ROI)</span>
              <div className="mt-3 text-4xl font-black text-emerald-400 font-mono">
                {classesToBreakEven} 堂課
              </div>
              <span className="text-xs text-zinc-300 font-medium mt-1 block">即可 100% 賺回年費</span>

              <div className="mt-6 space-y-2 border-t border-zinc-800/80 pt-4 text-xs">
                <div className="flex justify-between text-zinc-400">
                  <span>預估年度課鐘收入：</span>
                  <span className="font-mono text-white font-bold">NT$ {annualEarnings.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>軟體年費成本佔比：</span>
                  <span className="font-mono text-emerald-400 font-bold">
                    {((yearlyPlanPrice / annualEarnings) * 100).toFixed(1)}%
                  </span>
                </div>
                <div className="flex justify-between text-zinc-400">
                  <span>每年省下備課時間：</span>
                  <span className="font-mono text-amber-400 font-bold">約 {annualHoursSaved} 小時</span>
                </div>
              </div>

              <div className="mt-6">
                <Link
                  href="#download"
                  className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 px-4 py-2.5 text-xs font-bold text-zinc-950 transition-colors"
                >
                  <Flame className="h-4 w-4" />
                  免費試用 30 天開跑
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
