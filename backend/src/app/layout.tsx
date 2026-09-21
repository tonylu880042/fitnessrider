import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'FitnessRider - 專為室內飛輪教練打造的節奏中控引擎',
  description: '專業室內飛輪教學軟體，支援高音質無損變速不變調、波形 BPM 自動偵測、車架橫向大字 HUD 課堂倒數引導與跨平台課表一鍵交換。',
  keywords: ['飛輪', '飛輪教練', '室內單車', 'BPM分析', '音樂變速', 'Spinning', 'Indoor Cycling'],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-TW" className="dark scroll-smooth">
      <body className={`${geistSans.variable} ${geistMono.variable} bg-zinc-950 text-zinc-100 antialiased selection:bg-emerald-500 selection:text-zinc-950`}>
        {children}
      </body>
    </html>
  );
}
