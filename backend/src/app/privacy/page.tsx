import Link from 'next/link';
import Navbar from '@/components/Navbar';
import Footer from '@/components/Footer';
import { ArrowLeft, Shield } from 'lucide-react';

export const metadata = {
  title: '隱私權政策 (Privacy Policy) - FitnessRider',
  description: 'FitnessRider 隱私權保護政策與個人資料處理規範。',
};

export default function PrivacyPolicy() {
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
            <Shield className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-3xl font-extrabold text-white">隱私權政策 (Privacy Policy)</h1>
            <p className="text-xs text-zinc-500 mt-1">最後更新日期：2026 年 9 月 21 日</p>
          </div>
        </div>

        <div className="prose prose-invert max-w-none space-y-8 text-zinc-300 text-sm leading-relaxed border-t border-zinc-800 pt-8">
          <section>
            <h2 className="text-lg font-bold text-white mb-3">1. 引言與承諾</h2>
            <p>
              FitnessRider（以下簡稱「本系統」、「我們」）深知個人隱私與資料安全的重要性。本隱私權政策旨在透明說明我們如何搜集、使用、保存及保護您在使用 FitnessRider 應用程式及相關服務時所提供的資訊。我們嚴格遵守相關隱私保護法規，絕不販售您的個人資訊予任何第三方。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">2. 我們搜集的資訊種類與用途</h2>
            <ul className="list-disc pl-5 space-y-2 text-zinc-400">
              <li>
                <strong className="text-zinc-200">帳號基本資料：</strong>
                當您註冊帳號時，我們會收集您的電子郵件地址與教練暱稱，作為身分識別、登入驗證及訂閱狀態查詢之用。
              </li>
              <li>
                <strong className="text-zinc-200">設備唯一識別碼 (Device Fingerprint)：</strong>
                為保障正版單機授權政策，防止單一帳號在多台設備同時被非授權共享，系統會在您登入時讀取設備供應商識別碼（iOS 之 <code>identifierForVendor</code> 或 Android 之 <code>ANDROID_ID</code>）及設備型號。此資訊僅用於伺服器端比對是否為同一綁定設備，不具備跨應用程式追蹤之功能。
              </li>
              <li>
                <strong className="text-zinc-200">本機音樂與課表資料：</strong>
                您在 App 內匯入的音樂檔案（MP3、AAC）與編排之課表資料，<strong>100% 僅儲存於您設備的本機沙盒中</strong>。我們不會將您的任何音訊檔案上傳至雲端伺服器。
              </li>
            </ul>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">3. 支付與金流安全</h2>
            <p>
              本系統之內購訂閱服務係透過 Apple App Store 與 Google Play 官方支付系統處理，並採用經業界嚴格認證的 RevenueCat 進行跨平台收據驗證。<strong>我們不會接觸、紀錄或儲存您的信用卡號碼、銀行帳戶或任何敏感金融資訊</strong>。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">4. 資料保存與安全性</h2>
            <p>
              所有帳號密碼皆採用業界標準的 bcrypt 單向強加密演算法雜湊後保存；所有與伺服器之通訊均透過嚴格的 HTTPS (TLS 1.3) 加密傳輸通道進行。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">5. 您的權利 (權限行使與帳號刪除)</h2>
            <p>
              您隨時有權向我們查詢、閱覽、補充或更正您的個人資料。若您希望刪除 FitnessRider 帳號及相關之設備綁定紀錄，可透過下方聯絡信箱向客服提出「帳號刪除請求」，我們將於確認身分後 7 個工作日內自伺服器完全抹除您的帳號與設備關聯資料。
            </p>
          </section>

          <section>
            <h2 className="text-lg font-bold text-white mb-3">6. 聯繫我們</h2>
            <p>
              如果您對本隱私權政策有任何疑問、意見或行使權益之請求，歡迎透過官方客服信箱與我們聯繫：<br />
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
