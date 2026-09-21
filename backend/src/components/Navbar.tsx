'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Activity, Menu, X, ArrowRight } from 'lucide-react';

export default function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 text-zinc-950 shadow-lg shadow-emerald-500/20 group-hover:scale-105 transition-transform">
            <Activity className="h-6 w-6 stroke-[2.5]" />
          </div>
          <div className="flex flex-col">
            <span className="text-lg font-black tracking-wider text-white font-sans">
              FITNESS<span className="text-emerald-400">RIDER</span>
            </span>
            <span className="text-[10px] tracking-widest text-zinc-400 font-medium uppercase -mt-1">
              PRO CYCLING ENGINE
            </span>
          </div>
        </Link>

        <nav className="hidden md:flex items-center gap-8">
          <Link href="#features" className="text-sm font-medium text-zinc-300 hover:text-emerald-400 transition-colors">
            核心亮點
          </Link>
          <Link href="#hud-preview" className="text-sm font-medium text-zinc-300 hover:text-emerald-400 transition-colors">
            課堂中控台
          </Link>
          <Link href="#pricing" className="text-sm font-medium text-zinc-300 hover:text-emerald-400 transition-colors">
            訂閱方案
          </Link>
          <Link href="#faq" className="text-sm font-medium text-zinc-300 hover:text-emerald-400 transition-colors">
            常見問答
          </Link>
        </nav>

        <div className="hidden md:flex items-center gap-4">
          <Link
            href="#download"
            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-400 px-4 py-2 text-sm font-bold text-zinc-950 shadow-md shadow-emerald-500/25 hover:from-emerald-400 hover:to-teal-300 hover:shadow-emerald-500/40 transition-all"
          >
            立即下載體驗
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>

        <button
          type="button"
          aria-label="Toggle navigation menu"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="rounded-lg p-2 text-zinc-400 hover:bg-zinc-900 md:hidden"
        >
          {mobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
        </button>
      </div>

      {mobileMenuOpen && (
        <div className="border-b border-zinc-800 bg-zinc-950 px-4 py-4 md:hidden animate-in slide-in-from-top-2">
          <div className="flex flex-col gap-3">
            <Link
              href="#features"
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm font-medium text-zinc-300 hover:text-emerald-400 py-1"
            >
              核心亮點
            </Link>
            <Link
              href="#hud-preview"
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm font-medium text-zinc-300 hover:text-emerald-400 py-1"
            >
              課堂中控台
            </Link>
            <Link
              href="#pricing"
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm font-medium text-zinc-300 hover:text-emerald-400 py-1"
            >
              訂閱方案
            </Link>
            <Link
              href="#faq"
              onClick={() => setMobileMenuOpen(false)}
              className="text-sm font-medium text-zinc-300 hover:text-emerald-400 py-1"
            >
              常見問答
            </Link>
            <Link
              href="#download"
              onClick={() => setMobileMenuOpen(false)}
              className="mt-2 flex items-center justify-center gap-2 rounded-xl bg-emerald-500 py-2.5 text-sm font-bold text-zinc-950"
            >
              立即下載 App
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
