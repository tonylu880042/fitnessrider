import Link from 'next/link';
import { Play, Sparkles, ArrowDown } from 'lucide-react';

export default function Hero() {
  return (
    <section className="relative overflow-hidden pt-12 pb-20 md:pt-20 md:pb-28">
      {/* Background Gradients */}
      <div className="pointer-events-none absolute -top-40 left-1/2 -z-10 -translate-x-1/2 transform-gpu blur-3xl sm:-top-80">
        <div
          className="aspect-[1155/678] w-[68rem] bg-gradient-to-tr from-emerald-600/30 via-teal-500/20 to-cyan-500/30 opacity-40"
          style={{
            clipPath:
              'polygon(74.1% 44.1%, 100% 61.6%, 97.5% 26.9%, 85.5% 0.1%, 80.7% 2%, 72.5% 32.5%, 60.2% 62.4%, 52.4% 68.1%, 47.5% 58.3%, 45.2% 34.5%, 27.5% 76.7%, 0.1% 64.9%, 17.9% 100%, 27.6% 76.8%, 76.1% 97.7%, 74.1% 44.1%)',
          }}
        />
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 text-center">
        {/* Badge */}
        <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3.5 py-1.5 text-xs font-semibold text-emerald-400 backdrop-blur-md mb-6">
          <Sparkles className="h-3.5 w-3.5 text-emerald-400 animate-pulse" />
          <span>2026 全新雙原生引擎・專為飛輪教練研發</span>
        </div>

        {/* Title */}
        <h1 className="text-4xl font-extrabold tracking-tight text-white sm:text-6xl lg:text-7xl font-sans">
          掌控音樂節奏，
          <br className="hidden sm:inline" />
          帶出<span className="bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400 bg-clip-text text-transparent">熱血沸騰</span>的飛輪課堂
        </h1>

        {/* Subtitle */}
        <p className="mx-auto mt-6 max-w-2xl text-base sm:text-lg text-zinc-400 leading-relaxed">
          告別手忙腳亂的紙本小抄與切歌靜音。
          <strong className="text-zinc-200 font-semibold"> FitnessRider</strong> 整合高音質無損變速、波形 BPM 精準對拍與大字動態倒數 HUD，新用戶享 <strong className="text-emerald-400">7 天全功能免費試用</strong>，讓教練專注於帶動全場踩踏激情。
        </p>

        {/* CTA Buttons */}
        <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            href="#download"
            className="w-full sm:w-auto flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-400 px-8 py-3.5 text-base font-bold text-zinc-950 shadow-lg shadow-emerald-500/25 hover:from-emerald-400 hover:to-teal-300 hover:shadow-emerald-500/40 hover:-translate-y-0.5 transition-all"
          >
            <Play className="h-5 w-5 fill-current" />
            免費下載體驗 (7 天 VIP 試用)
          </Link>
          <Link
            href="#pricing"
            className="w-full sm:w-auto flex items-center justify-center gap-2 rounded-xl border border-zinc-700/80 bg-zinc-900/60 px-6 py-3.5 text-base font-semibold text-zinc-300 hover:bg-zinc-800 hover:text-white transition-colors"
          >
            查看教練訂閱方案
            <ArrowDown className="h-4 w-4 text-zinc-400" />
          </Link>
        </div>

        {/* Promo code badge for courses */}
        <div className="mt-4 flex items-center justify-center gap-2 text-xs text-zinc-400">
          <span>🎓 搭配培訓推廣課程？App 內輸入 2026 年度專屬代碼</span>
          <span className="font-mono font-black text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/30">26FR-NR</span>
          <span>即享 30 天全功能免費體驗！</span>
        </div>

        {/* Key Points */}
        <div className="mt-8 flex flex-wrap justify-center gap-x-8 gap-y-3 text-xs sm:text-sm text-zinc-400">
          <div className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            0.7x ~ 1.3x 變速不變調
          </div>
          <div className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            1~3 秒雙軌等能量 Crossfade
          </div>
          <div className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            支援 iPad & Android 橫向車架
          </div>
          <div className="flex items-center gap-1.5">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            100% 離線播音，地下室零延遲
          </div>
        </div>

        {/* Mockup Showcase */}
        <div id="hud-preview" className="relative mt-14 sm:mt-20">
          <div className="relative mx-auto max-w-5xl rounded-3xl border border-zinc-700/60 bg-zinc-900/90 p-3 sm:p-5 shadow-2xl shadow-emerald-950/40 backdrop-blur-xl">
            {/* iPad Frame Mockup */}
            <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-950 p-4 sm:p-8 text-left">
              {/* Header Bar */}
              <div className="flex flex-wrap items-center justify-between border-b border-zinc-800/80 pb-4 mb-6 gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs uppercase tracking-widest text-emerald-400 font-bold">LIVE WORKOUT HUD</span>
                    <span className="rounded-md bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-mono text-emerald-300">
                      Crossfade 2s
                    </span>
                    <span className="rounded-md bg-cyan-500/10 border border-cyan-500/30 px-2 py-0.5 text-[10px] font-mono text-cyan-300">
                      3-2-1 嗶聲 ON
                    </span>
                    <span className="rounded-md bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 text-[10px] font-mono text-amber-300">
                      車把震動 ON
                    </span>
                  </div>
                  <h3 className="text-lg sm:text-xl font-black text-white mt-1">45min 高強度燃脂間歇衝刺</h3>
                </div>
                <div className="flex items-center gap-3">
                  <div className="rounded-lg bg-emerald-500/10 border border-emerald-500/30 px-3 py-1 text-xs font-mono font-bold text-emerald-400">
                    預估卡路里: 420.5 kcal
                  </div>
                  <div className="rounded-lg bg-zinc-800 px-3 py-1 text-xs font-mono text-zinc-300">
                    總時長: 45:00
                  </div>
                </div>
              </div>

              {/* Main HUD Display Area */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-center">
                {/* Countdown Ring */}
                <div className="flex flex-col items-center justify-center p-6 rounded-2xl bg-zinc-900/50 border border-zinc-800/60">
                  <div className="relative flex h-40 w-40 items-center justify-center rounded-full border-4 border-emerald-500/20 shadow-inner shadow-emerald-500/20">
                    <div className="absolute inset-0 rounded-full border-4 border-emerald-400 border-t-transparent animate-spin [animation-duration:8s]" />
                    <div className="text-center">
                      <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">段落倒數</span>
                      <div className="text-4xl font-black text-white font-mono">02:45</div>
                      <span className="text-[11px] text-emerald-400 font-medium">區段 3 / 8</span>
                    </div>
                  </div>
                  <span className="mt-4 text-xs font-medium text-zinc-400">當前階段：連續陡坡 (Zone 4)</span>
                </div>

                {/* Target RPM & Posture Indicator */}
                <div className="flex flex-col justify-between h-full p-6 rounded-2xl bg-gradient-to-br from-zinc-900/80 to-zinc-950 border border-zinc-800/80">
                  <div>
                    <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">當前指令 (CURRENT CUE)</span>
                    <div className="mt-2 flex items-baseline gap-2">
                      <span className="text-6xl font-black text-white font-mono">85</span>
                      <span className="text-lg font-bold text-emerald-400">RPM</span>
                    </div>
                  </div>

                  <div className="mt-4 space-y-2">
                    <div className="flex items-center justify-between text-xs text-zinc-300">
                      <span>姿勢：<strong className="text-amber-400 font-bold">站姿爬坡 (Standing Climb)</strong></span>
                      <span>阻力：<strong className="text-white font-bold">微調 2 圈</strong></span>
                    </div>
                    {/* Intensity Bar */}
                    <div className="h-2.5 w-full rounded-full bg-zinc-800 overflow-hidden flex">
                      <div className="h-full bg-blue-500 w-1/5" />
                      <div className="h-full bg-teal-500 w-1/5" />
                      <div className="h-full bg-emerald-500 w-1/5" />
                      <div className="h-full bg-amber-500 w-1/5 shadow-lg shadow-amber-500/50 ring-1 ring-white" />
                      <div className="h-full bg-red-500/30 w-1/5" />
                    </div>
                    <div className="flex justify-between text-[10px] text-zinc-500 font-mono">
                      <span>Zone 1</span>
                      <span className="text-amber-400 font-bold">Zone 4 (高強度)</span>
                      <span>Zone 5</span>
                    </div>
                  </div>
                </div>

                {/* Next Cue Alert & Audio Bar */}
                <div className="flex flex-col justify-between h-full p-6 rounded-2xl bg-zinc-900/50 border border-zinc-800/60">
                  {/* Next Alert */}
                  <div className="rounded-xl bg-amber-500/10 border border-amber-500/30 p-4">
                    <div className="flex items-center justify-between text-xs font-bold text-amber-400">
                      <span>⚡ 下一動作預告 (NEXT CUE)</span>
                      <span className="font-mono bg-amber-400/20 px-1.5 py-0.5 rounded text-[10px]">00:15 秒後</span>
                    </div>
                    <p className="mt-1 text-sm font-semibold text-white">坐姿平路緩和衝刺・目標 100 RPM</p>
                  </div>

                  {/* Audio Controls Mockup */}
                  <div className="mt-4 pt-4 border-t border-zinc-800">
                    <div className="flex items-center justify-between text-xs text-zinc-400 mb-2">
                      <span className="truncate">🎵 Track 03 - Titanium (Remix)</span>
                      <span className="font-mono text-emerald-400">128 BPM (1.05x)</span>
                    </div>
                    {/* Fake Waveform */}
                    <div className="flex items-end gap-0.5 h-8 w-full">
                      {[30, 45, 60, 80, 50, 95, 70, 85, 40, 60, 75, 90, 100, 65, 80, 45, 70, 85, 90, 60, 40, 75, 90, 85, 55, 65, 80, 95, 70, 50].map((h, i) => (
                        <div
                          key={i}
                          className={`flex-1 rounded-full ${i < 18 ? 'bg-emerald-400' : 'bg-zinc-700'}`}
                          style={{ height: `${h}%` }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
