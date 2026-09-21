import Navbar from '@/components/Navbar';
import Hero from '@/components/Hero';
import Features from '@/components/Features';
import Pricing from '@/components/Pricing';
import Faq from '@/components/Faq';
import DownloadSection from '@/components/DownloadSection';
import Footer from '@/components/Footer';

export const metadata = {
  title: 'FitnessRider - 專為室內飛輪教練打造的節奏中控引擎',
  description: '專業室內飛輪訓練軟體，支援高音質無損變速不變調、波形 BPM 自動偵測、車架橫向大字 HUD 課堂倒數引導與跨平台課表一鍵交換。',
};

export default function Home() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 selection:bg-emerald-500 selection:text-zinc-950">
      <Navbar />
      <main>
        <Hero />
        <Features />
        <Pricing />
        <Faq />
        <DownloadSection />
      </main>
      <Footer />
    </div>
  );
}
