import { Apple, Play, Sparkles } from 'lucide-react';

export default function DownloadSection() {
  return (
    <section id="download" className="py-20 relative overflow-hidden bg-gradient-to-b from-zinc-950 via-zinc-900 to-zinc-950">
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 text-center">
        <div className="rounded-3xl border border-emerald-500/30 bg-gradient-to-br from-emerald-950/30 via-zinc-900/90 to-zinc-950 p-8 sm:p-14 shadow-2xl shadow-emerald-500/10 backdrop-blur-xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/40 bg-emerald-500/10 px-3 py-1 text-xs font-bold text-emerald-400 mb-6">
            <Sparkles className="h-3.5 w-3.5" />
            <span>立即下載・解鎖全新教學效率</span>
          </div>

          <h2 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            準備好帶領下一堂撼動全場的飛輪課了嗎？
          </h2>

          <p className="mx-auto mt-4 max-w-xl text-sm sm:text-base text-zinc-400">
            支援 iPad 與 Android 平板。首次啟動即享有 <strong className="text-emerald-400">30 天全功能免費試用</strong>，不需預先綁定信用卡，開箱即騎。
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <div className="flex items-center gap-3 rounded-2xl border border-zinc-700 bg-zinc-800/80 px-6 py-3.5 text-left text-white hover:border-emerald-500 hover:bg-zinc-800 transition-all cursor-pointer shadow-md">
              <Apple className="h-8 w-8 text-white fill-current" />
              <div>
                <span className="block text-[10px] text-zinc-400 uppercase font-medium">Download for</span>
                <span className="text-base font-bold font-sans">iPadOS / iOS</span>
              </div>
            </div>

            <div className="flex items-center gap-3 rounded-2xl border border-zinc-700 bg-zinc-800/80 px-6 py-3.5 text-left text-white hover:border-emerald-500 hover:bg-zinc-800 transition-all cursor-pointer shadow-md">
              <Play className="h-7 w-7 text-emerald-400 fill-current" />
              <div>
                <span className="block text-[10px] text-zinc-400 uppercase font-medium">Get it on</span>
                <span className="text-base font-bold font-sans">Google Play</span>
              </div>
            </div>
          </div>

          <p className="mt-6 text-xs text-zinc-500">
            * 系統建議最低環境：iPadOS 17+ / Android 14+ 平板橫向使用。
          </p>
        </div>
      </div>
    </section>
  );
}
