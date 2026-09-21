import Link from 'next/link';
import Navbar from '@/components/Navbar';
import Footer from '@/components/Footer';
import { ArrowLeft, FileText } from 'lucide-react';

export const metadata = {
  title: '使用者服務條款與 EULA (Terms of Service) - FitnessRider',
  description: 'FitnessRider 服務使用條款、單機授權政策與終端使用者授權協議。',
};

export default function TermsOfService() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <Navbar />
      <main className="mx-auto max-w-4xl px-4 py-16 sm:px-6 lg:px-8">
        <Link
          href="/"
          className="inline-flex items-center gap-2 text-sm text-emerald-400 hover:text-emerald-300 mb-8 font-medium"
        >
          <ArrowLeft className="h-4 w-4" />
          返回首頁
        </Link>

        <div className="flex items-center gap-3 mb-6">
          <div className="p-3 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400">
            <FileText className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-3xl font-extrabold text-white">使用者服務條款與 EULA (Terms)</h1>
            <p className="text-xs text-zinc-500 mt-1">最後更新日期：2026 年 9 月 21 日</p>
          </div>
        </div>

        <div className="prose prose-invert max-w-none space-y-8 text-zinc-300 text-sm leading-relaxed border-t border-zinc-800 pt-8">
          <section>
            <h2 className="text-lg font-bold text-white mb-3">1. 條款接受與服務範圍</h2>
            <p>
              歡迎使用 FitnessRider！當您下載、安裝或使用本應用程式及相關網站服務時，即代表您同意受本使用者服務條款（以下簡稱「本條款」）約束。若您不同意本條款之全部或部分內容，請立即停止使用本服務。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">2. 單一設備授權綁定條款 (Single-Device Policy)</h2>
            <p>
              為維護商業軟體的授權合規性，<strong>一組付費帳號於同一時間僅限授權並綁定於一台實體主要設備（如特定 iPad 或 Android 平板）</strong>：
            </p>
            <ul className="list-disc pl-5 space-y-2 text-zinc-400 mt-2">
              <li>嚴禁將單一帳號交由多名教練或在多台教學設備間同時共享使用。</li>
              <li>
                <strong>設備更換政策：</strong>若因更換新平板或硬體損壞需轉移綁定，您可於 App 內提出「轉移設備」申請。為防範不當共用行為，<strong>系統設定每 30 天允許進行一次設備轉移</strong>。
              </li>
            </ul>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">3. 訂閱方案與自動續約條款</h2>
            <ul className="list-disc pl-5 space-y-2 text-zinc-400">
              <li>
                <strong>試用期：</strong>新用戶首次啟用即享 30 天全功能免費 VIP 試用期（免先綁定信用卡），試用期滿後將自動停止進階功能權益，絕無未經同意之自動扣款。
              </li>
              <li>
                <strong>扣款與續約：</strong>所有訂閱（月繳 NT$ 390、季繳 NT$ 890、年繳 NT$ 2,390）均由 Apple App Store 或 Google Play 帳戶收取。確認購買後，系統將於當前週期結束前 24 小時內自動扣款續約。
              </li>
              <li>
                <strong>取消訂閱：</strong>您可於當前週期結束前隨時至設備的「設定 &gt; 訂閱項目」關閉自動續約。取消後您仍可繼續享有服務權益直至該期結束，已支付之款項恕不按日退還。
              </li>
            </ul>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">4. 音樂版權與內容責任</h2>
            <p>
              FitnessRider 為音訊播放與訓練節奏輔助工具，<strong>本系統本身不內建亦不提供任何受版權保護之商業音樂檔案</strong>。使用者在實體商業場所（如健身房、舞蹈教室）授課時，應自行確保所使用音訊檔案之合法來源與公開播送/公開演出之權限，使用者應自負其所屬場域之音樂授權責任。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">5. 運動安全與健康免責聲明</h2>
            <p>
              飛輪運動為高強度體能訓練。本系統所提供之踏頻指示 (RPM)、阻力等級、心率強度區間及預估卡路里僅供運動輔助參考，非醫療診斷或處方。教練於課堂進行中應隨時注意學員之身體反應，使用者因自身運動引發之不適或意外傷害，本系統概不承擔醫療或賠償責任。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">6. 聯繫客服</h2>
            <p>
              如對本服務條款有任何疑問，請聯繫官方信箱：<br />
              <a href="mailto:support@fitnessrider.app" className="text-emerald-400 hover:underline">
                support@fitnessrider.app
              </a>
            </p>
          </section>
        </div>
      </main>
      <Footer />
    </div>
  );
}
