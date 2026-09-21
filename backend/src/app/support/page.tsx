import Link from 'next/link';
import Navbar from '@/components/Navbar';
import Footer from '@/components/Footer';
import { ArrowLeft, Headphones, Mail, HelpCircle, Smartphone, RefreshCw, MessageSquare, ShieldCheck } from 'lucide-react';

export const metadata = {
  title: '技術支援與客戶服務中心 (Support) - FitnessRider',
  description: 'FitnessRider 官方技術支援、飛輪教練常見問題故障排除、跨設備轉移與客服聯繫管道。',
};

export default function SupportPage() {
  const troubleshootingItems = [
    {
      icon: <Headphones className="h-5 w-5 text-emerald-400" />,
      title: '藍牙音訊與音樂播放疑難排除',
      description:
        '若課堂中音樂無聲，請檢查設備側邊實體靜音開關（iPad/iPhone）或控制中心音量；若使用藍牙音響或耳機有微小延遲，可於 App「系統設定 > 課堂與音訊」中確認 3-2-1 提示音與 Crossfade 平滑轉場設定。',
    },
    {
      icon: <RefreshCw className="h-5 w-5 text-amber-400" />,
      title: '跨設備更換與 30 天冷卻機制',
      description:
        '當您更換新 iPad 或 Android 平板時，於過期或登入頁點選「🔄 轉移設備授權」，輸入原帳密或原 VIP 序號即可一鍵遷入。若系統顯示冷卻天數限制且您有緊急授課需求（如硬體損壞更換），請透過下方信箱聯繫專案處理。',
    },
    {
      icon: <Smartphone className="h-5 w-5 text-cyan-400" />,
      title: '系統最低相容性需求',
      description:
        '本系統最佳使用體驗為 11 吋或 13 吋平板橫向安裝於車把支架。最低系統需求為 iPadOS 17.0 以上、Android 14.0 以上。iPhone 與 Android 手機亦可開啟隨身檢視課表。',
    },
    {
      icon: <ShieldCheck className="h-5 w-5 text-indigo-400" />,
      title: '訂閱管理與退訂說明',
      description:
        'FitnessRider 所有付費訂閱皆由 Apple App Store 或 Google Play 官方統一處理。您可隨時至 iOS「設定 > Apple ID > 訂閱項目」或 Android「Google Play 商店 > 個人頭像 > 付款與訂閱」隨時查詢與取消自動續訂。',
    },
  ];

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 selection:bg-emerald-500 selection:text-zinc-950">
      <Navbar />
      <main className="mx-auto max-w-5xl px-4 py-16 sm:px-6 lg:px-8">
        <Link
          href="/"
          className="inline-flex items-center gap-2 text-sm text-emerald-400 hover:text-emerald-300 mb-8 font-medium transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          返回首頁
        </Link>

        {/* Header */}
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3.5 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400">
            <HelpCircle className="h-7 w-7" />
          </div>
          <div>
            <h1 className="text-3xl sm:text-4xl font-extrabold text-white">技術支援與客服中心 (Support)</h1>
            <p className="text-sm text-zinc-400 mt-1">
              專為飛輪教練打造的即時技術支援、疑難排除與官方聯繫窗口
            </p>
          </div>
        </div>

        {/* Quick Contact Box */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-14">
          <div className="rounded-3xl border border-emerald-500/30 bg-gradient-to-br from-emerald-950/20 via-zinc-900 to-zinc-950 p-6 sm:p-8">
            <div className="flex items-center gap-3 mb-4">
              <Mail className="h-6 w-6 text-emerald-400" />
              <h3 className="text-lg font-bold text-white">官方電子郵件客服</h3>
            </div>
            <p className="text-sm text-zinc-400 leading-relaxed mb-6">
              遇到任何 App 異常、設備轉移申訴、付費問題或功能建議，歡迎直接發信至官方專屬技術團隊，我們將於 24 小時內回覆。
            </p>
            <a
              href="mailto:support@fitnessrider.app"
              className="inline-flex items-center gap-2 rounded-xl bg-emerald-500 px-5 py-2.5 text-sm font-bold text-zinc-950 hover:bg-emerald-400 transition-colors"
            >
              <Mail className="h-4 w-4" />
              support@fitnessrider.app
            </a>
          </div>

          <div className="rounded-3xl border border-zinc-800 bg-zinc-900/40 p-6 sm:p-8">
            <div className="flex items-center gap-3 mb-4">
              <MessageSquare className="h-6 w-6 text-teal-400" />
              <h3 className="text-lg font-bold text-white">線上問題回報須知</h3>
            </div>
            <p className="text-sm text-zinc-400 leading-relaxed mb-4">
              為加速問題處理，聯繫時請隨信附上以下資訊：
            </p>
            <ul className="text-xs text-zinc-400 space-y-2 list-disc pl-5">
              <li>您的設備型號（如 iPad Pro 11-inch、Galaxy Tab S9）</li>
              <li>目前系統版本（如 iOS 18.2、Android 14）</li>
              <li>本機設備識別碼（於 App「系統設定」最下方點擊「複製」）</li>
              <li>問題簡短描述或錯誤訊息畫面截圖</li>
            </ul>
          </div>
        </div>

        {/* Troubleshooting Cards */}
        <div className="mb-14 border-t border-zinc-800/80 pt-10">
          <h2 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
            <span>常見技術問題快速排除</span>
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {troubleshootingItems.map((item, index) => (
              <div
                key={index}
                className="rounded-2xl border border-zinc-800 bg-zinc-900/30 p-6 hover:border-zinc-700 transition-colors"
              >
                <div className="flex items-center gap-3 mb-3">
                  <div className="p-2 rounded-lg bg-zinc-800">{item.icon}</div>
                  <h3 className="text-base font-bold text-white">{item.title}</h3>
                </div>
                <p className="text-sm text-zinc-400 leading-relaxed">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Legal Links Banner */}
        <div className="rounded-2xl border border-zinc-800/80 bg-zinc-900/20 p-6 text-center text-xs text-zinc-500">
          <p className="mb-2">相關法律協議與條款公開連結：</p>
          <div className="flex justify-center gap-6">
            <Link href="/privacy" className="text-emerald-400 hover:underline">
              隱私權政策 (Privacy Policy)
            </Link>
            <Link href="/terms" className="text-emerald-400 hover:underline">
              使用者服務條款與 EULA (Terms of Service)
            </Link>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}
