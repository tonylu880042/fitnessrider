import Link from 'next/link';
import { Activity } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="border-t border-zinc-800 bg-zinc-950 py-12 text-zinc-400">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 text-zinc-950">
              <Activity className="h-5 w-5 stroke-[2.5]" />
            </div>
            <span className="text-base font-black tracking-wider text-white">
              FITNESS<span className="text-emerald-400">RIDER</span>
            </span>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-6 text-xs sm:text-sm">
            <Link href="/#features" className="hover:text-emerald-400 transition-colors">
              核心特色
            </Link>
            <Link href="/#pricing" className="hover:text-emerald-400 transition-colors">
              訂閱定價
            </Link>
            <Link href="/#faq" className="hover:text-emerald-400 transition-colors">
              常見問題
            </Link>
            <Link href="/support" className="hover:text-emerald-400 transition-colors text-zinc-300 font-medium">
              技術支援與客服
            </Link>
            <Link href="/privacy" className="hover:text-emerald-400 transition-colors text-zinc-300 font-medium">
              隱私權政策 (Privacy Policy)
            </Link>
            <Link href="/terms" className="hover:text-emerald-400 transition-colors text-zinc-300 font-medium">
              服務條款與 EULA (Terms)
            </Link>
          </div>
        </div>

        <div className="mt-8 border-t border-zinc-800/80 pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-zinc-500">
          <p>© {new Date().getFullYear()} FitnessRider. All rights reserved. 專為專業飛輪教練打造。</p>
          <p>聯絡信箱：support@fitnessrider.app</p>
        </div>
      </div>
    </footer>
  );
}
