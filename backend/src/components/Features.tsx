import { Sliders, Activity, Clock, Share2, ShieldCheck, Zap } from 'lucide-react';

export default function Features() {
  const features = [
    {
      icon: <Sliders className="h-6 w-6 text-emerald-400" />,
      title: '高音質無損變速 (TimePitch)',
      description:
        '飛輪教學最怕歌好聽但節奏太快或太慢。內建雙原生音訊引擎，支援 0.7x 至 1.3x 連續無損微調，踩踏節奏與鼓點 100% 契合，且人聲音調完全不變尖失真。',
      badge: '原生雙引擎',
    },
    {
      icon: <Activity className="h-6 w-6 text-teal-400" />,
      title: '視覺化波形與自動 BPM 分析',
      description:
        '匯入 MP3/AAC 即刻繪製高解析度音訊振幅波形，智慧演算法秒測原始歌曲拍頻 (BPM)。支援手動 Tap-Tempo 輕敲校正，時間軸拖曳標註動作提示更直覺。',
      badge: '純數學峰值偵測',
    },
    {
      icon: <Clock className="h-6 w-6 text-cyan-400" />,
      title: '橫向大字課堂 HUD 中控台',
      description:
        '專為置放於飛輪車把設計。具備動態環形剩餘秒數倒數、Zone 1~5 強度色塊與超大字目標 RPM；5~10 秒提前閃爍「下一動作預告」，再也不怕忘記口令。',
      badge: '專注授課體驗',
    },
    {
      icon: <Share2 className="h-6 w-6 text-emerald-400" />,
      title: '跨平台共通課表 JSON 交換',
      description:
        '採用開放標準化的 workout_class.json 格式。教練在 iPad 上精心排好的 45 分鐘課表，可透過 AirDrop、LINE 或檔案 App 直接匯入 Android 平板使用，交流無礙。',
      badge: 'iPad & Android 互通',
    },
    {
      icon: <Zap className="h-6 w-6 text-amber-400" />,
      title: '雙軌平滑 Crossfade 零冷場',
      description:
        '課堂切歌最忌諱長達數秒的尷尬靜音。內建雙軌播放器，支援曲目切換前 1~5 秒交錯淡入淡出，保持全場踩踏熱度不間斷。',
      badge: '無縫接歌',
    },
    {
      icon: <ShieldCheck className="h-6 w-6 text-indigo-400" />,
      title: '100% 離線穩定授課，不卡頓',
      description:
        '健身房地下室收訊不佳？課堂音樂與課表完全儲存於設備本機，離線即可順暢播放教學，不用擔心網路斷線中斷課堂。',
      badge: '離線可用',
    },
  ];

  return (
    <section id="features" className="py-20 bg-zinc-950/60 border-t border-zinc-800/80">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto">
          <h2 className="text-xs uppercase tracking-widest text-emerald-400 font-bold">FEEL THE BEAT・MASTER THE RIDE</h2>
          <p className="mt-3 text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            六大極致功能，重新定義你的飛輪備課體驗
          </p>
          <p className="mt-4 text-zinc-400 text-sm sm:text-base">
            不再需要一邊騎車一邊手忙腳亂看紙條。從挑歌編排、節奏微調到課堂引導，一次滿足專業飛輪教練的所有挑剔需求。
          </p>
        </div>

        <div className="mt-16 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {features.map((feature, index) => (
            <div
              key={index}
              className="relative group rounded-2xl border border-zinc-800/80 bg-zinc-900/40 p-7 hover:bg-zinc-900/80 hover:border-zinc-700 transition-all hover:shadow-xl hover:shadow-emerald-950/20"
            >
              <div className="flex items-center justify-between mb-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-zinc-800/80 group-hover:scale-110 transition-transform">
                  {feature.icon}
                </div>
                <span className="rounded-full bg-zinc-800/80 px-2.5 py-0.5 text-[11px] font-semibold text-zinc-300 font-mono">
                  {feature.badge}
                </span>
              </div>
              <h3 className="text-lg font-bold text-white group-hover:text-emerald-400 transition-colors">
                {feature.title}
              </h3>
              <p className="mt-2.5 text-sm text-zinc-400 leading-relaxed">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
