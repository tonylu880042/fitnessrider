#!/usr/bin/env python3
import os
import sys
import time
import subprocess
from PIL import Image, ImageFont, ImageDraw

PROJECT_DIR = "/Users/tunghunglu/projects/fitnessrider"
MUSICS_DIR = os.path.join(PROJECT_DIR, "musics")
OUTPUT_DIR = os.path.join(PROJECT_DIR, "demo_output")
FONT_PATH = "/System/Library/Fonts/STHeiti Medium.ttc"
if not os.path.exists(FONT_PATH):
    FONT_PATH = "/System/Library/Fonts/PingFang.ttc"

os.makedirs(OUTPUT_DIR, exist_ok=True)

def generate_banner(text_top, text_sub, filename, width=760, height=68):
    """Generate a sleek glassmorphic banner with rounded corners."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Rounded background with dark translucent tint and subtle border
    bg_color = (18, 22, 26, 235)
    draw.rounded_rectangle([(0, 0), (width, height)], radius=14, fill=bg_color, outline=(255, 255, 255, 65), width=1)
    
    # Accent green stripe on left (#84BF09)
    draw.rounded_rectangle([(4, 4), (12, height - 4)], radius=4, fill=(132, 191, 9, 255))
    
    font_top = ImageFont.truetype(FONT_PATH, 19)
    font_sub = ImageFont.truetype(FONT_PATH, 13)
    
    # Text
    draw.text((24, 11), text_top, font=font_top, fill=(255, 255, 255, 255))
    draw.text((24, 38), text_sub, font=font_sub, fill=(185, 225, 120, 255))
    
    out_path = os.path.join(OUTPUT_DIR, filename)
    img.save(out_path)
    return out_path

def adb(cmd):
    """Execute adb shell command."""
    return subprocess.run(f"adb {cmd}", shell=True, capture_output=True, text=True)

def tap(x, y, delay=0.5):
    adb(f"shell input tap {x} {y}")
    if delay > 0:
        time.sleep(delay)

import shlex

def main(skip_record=False):
    local_raw = os.path.join(OUTPUT_DIR, "m3_demo_raw.mp4")
    print("=== Step 1: Pre-generating sleek UI overlay banners for Module M3 ===")
    banners_cfg = [
        ("m3_banner1.png", "【M3.1 / 串流波形繪製】 800 點高精度振幅柱 · 時間格線 · 動作 Cue 點", "以 musics 音樂檔實時解碼，生成 800 點振幅柱列與時間刻度，持久化至 Room 快取", 0.5, 7.5, 260, 680),
        ("m3_banner2.png", "【M3.1 / 即時音訊試聽】 高對比度播放軸 (Playhead) · 秒數精確同步", "點擊試聽按鈕播放音樂，紅線游標與倒三角指標平滑前進，即時顯示當前時間碼", 7.5, 15.5, 260, 680),
        ("m3_banner3.png", "【M3.1 / 波形互動拖曳 Seek】 任意點擊或滑動 · 即時精準定位音訊播放點", "支援在波形畫布任意位置點選跳轉，播放進度與音訊立即 Seek 至目標時間點", 15.5, 23.5, 260, 680),
        ("m3_banner4.png", "【M3.2 / 無損鎖調變速】 ±2% 踏頻即時微調 · ExoPlayer 專業鎖調", "即時加速 +2% / +4%，音訊時間無損延展（Pitch-Lock），音質絕不失真變調", 23.5, 30.5, 260, 680),
        ("m3_banner5.png", "【M3.2 / Tap-Tempo 測速校準】 大尺寸節拍按鍵 · 即時動態滾動平均 BPM", "隨音樂重拍節奏連續點擊，即時動態計算 BPM，逾 2.5 秒無點擊自動重設計算", 30.5, 41.5, 260, 68),
        ("m3_banner6.png", "【M3.2 / BPM 步進微調與套用】 -5 / -1 / +1 / +5 步進器 · 一鍵同步段落", "細緻增減至目標 BPM，點擊「套用 BPM」即時更新段落數值與控制列", 41.5, 49.5, 260, 680),
        ("m3_banner7.png", "【M3.1 / 多曲目串流切換】 秒級切換音樂波形指紋 · SQLite/Room 本地極速快取", "流暢切換 10 首示範音樂，即時呈現獨立波形特徵，儲存課表持久化保存", 49.5, 58.0, 260, 680),
    ]
    
    banner_items = []
    for fn, top, sub, st, et, bx, by in banners_cfg:
        p = generate_banner(top, sub, fn)
        banner_items.append((p, st, et, bx, by))
        print(f"Generated {fn}")

    if not skip_record or not os.path.exists(local_raw) or os.path.getsize(local_raw) < 1000:
        print("=== Step 2: Preparing app state (Resetting to Class List) ===")
        adb("shell am force-stop com.fitnessrider.coach")
        adb("shell am start -n com.fitnessrider.coach/com.fitnessrider.MainActivity")
        time.sleep(3.5)
        
        # Enter Class Editor (edit button bounds [1176,167][1248,215], center ~1212, 191)
        print("Navigating to Class Editor...")
        tap(1212, 191, delay=2.0)

        print("=== Step 3: Starting screen recording on Android (58s) ===")
        raw_video_device = "/sdcard/m3_demo_raw.mp4"
        adb(f"shell rm -f {raw_video_device}")
        
        record_proc = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "1280x800", "--bit-rate", "8000000", "--time-limit", "59", raw_video_device])
        start_time = time.time()
        print(f"Recording started at {start_time}")

        def elapsed():
            return time.time() - start_time

        # [Scene 1: 0.0s ~ 7.5s] Waveform & Time grid & Cues inspection
        print("[Scene 1] Observing 800-point Waveform, timecode grid and Cues...")
        while elapsed() < 7.5:
            time.sleep(0.05)

        # [Scene 2: 7.5s ~ 15.5s] Play preview & Playhead movement
        print("[Scene 2] Starting Audio Preview...")
        tap(50, 454, delay=0.1) # Click 試聽 (Play)
        
        while elapsed() < 15.5:
            time.sleep(0.05)

        # [Scene 3: 15.5s ~ 23.5s] Waveform scrubbing & seeking
        print("[Scene 3] Seeking to ~01:00 (midway of waveform)...")
        tap(640, 370, delay=0.1) # Seek midway
        time.sleep(3.5)
        
        print("[Scene 3] Seeking to ~00:30...")
        tap(340, 370, delay=0.1) # Seek 25%
        while elapsed() < 23.5:
            time.sleep(0.05)

        # [Scene 4: 23.5s ~ 30.5s] Pitch-lock tempo stepping (+2%, +4%)
        print("[Scene 4] Tempo stepping +2%...")
        tap(240, 454, delay=0.1) # +2%
        time.sleep(3.0)
        
        print("[Scene 4] Tempo stepping +4%...")
        tap(240, 454, delay=0.1) # +2% again -> +4%
        while elapsed() < 30.5:
            time.sleep(0.05)

        # [Scene 5: 30.5s ~ 41.5s] Tap-Tempo calibration
        print("[Scene 5] Opening BPM Calibration modal...")
        tap(433, 454, delay=1.5) # Click 校正
        
        # Rhythmic taps (~500ms intervals -> ~120 BPM)
        print("Performing rhythmic Tap-Tempo clicks...")
        tap(640, 390, delay=0.5)
        tap(640, 390, delay=0.5)
        tap(640, 390, delay=0.5)
        tap(640, 390, delay=0.5)
        tap(640, 390, delay=0.5)
        time.sleep(1.0)
        
        while elapsed() < 41.5:
            time.sleep(0.05)

        # [Scene 6: 41.5s ~ 49.5s] Stepper fine-tuning and Apply
        print("[Scene 6] Stepper fine-tuning (+1, +1, -1)...")
        tap(697, 545, delay=1.0) # +1
        tap(697, 545, delay=1.0) # +1
        tap(583, 545, delay=1.2) # -1
        
        print("[Scene 6] Applying calibrated BPM...")
        tap(840, 621, delay=1.5) # Click 套用 BPM
        
        while elapsed() < 49.5:
            time.sleep(0.05)

        # [Scene 7: 49.5s ~ 58.0s] Switching tracks & Saving class
        print("[Scene 7] Switching to Track 2 (Hit the Switch)...")
        tap(280, 180, delay=2.5) # Track 2
        
        print("[Scene 7] Switching to Track 3 (Marvin's Dance)...")
        tap(460, 180, delay=2.5) # Track 3
        
        print("[Scene 7] Switching to Track 5 (Alternate)...")
        tap(830, 180, delay=2.5) # Track 5
        
        print("[Scene 7] Saving Class...")
        tap(1235, 28, delay=1.0) # Click 儲存
        
        while elapsed() < 58.0:
            time.sleep(0.05)

        print("Waiting for recording process to conclude...")
        record_proc.wait()
        total_time = elapsed()
        print(f"Recording finished! Total elapsed time: {total_time:.2f}s")

        print("=== Step 4: Pulling raw video from emulator ===")
        adb(f"pull {raw_video_device} {local_raw}")
        if not os.path.exists(local_raw) or os.path.getsize(local_raw) < 1000:
            print("ERROR: Failed to pull raw video!")
            sys.exit(1)
        print(f"Pulled {local_raw} ({os.path.getsize(local_raw)} bytes)")
    else:
        print(f"Using existing raw video: {local_raw} ({os.path.getsize(local_raw)} bytes)")

    print("=== Step 5: Synthesizing master audio matching demonstrated actions ===")
    track1_mp3 = os.path.join(MUSICS_DIR, "Spring In My Step - Silent Partner.mp3")
    track2_mp3 = os.path.join(MUSICS_DIR, "Hit the Switch - Silent Partner.mp3")
    track3_mp3 = os.path.join(MUSICS_DIR, "Marvin's Dance - Silent Partner.mp3")
    track5_mp3 = os.path.join(MUSICS_DIR, "Alternate - Vibe Tracks.mp3")

    master_audio = os.path.join(OUTPUT_DIR, "m3_master_audio.wav")
    
    # Audio segments using shlex.quote to prevent any escaping issues
    s1 = os.path.join(OUTPUT_DIR, "m3_aud_s1.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 7.5 -i {shlex.quote(track1_mp3)} -af 'afade=t=in:ss=0:d=1.5,volume=0.2' {shlex.quote(s1)}", shell=True, check=True)

    s2 = os.path.join(OUTPUT_DIR, "m3_aud_s2.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 8.0 -i {shlex.quote(track1_mp3)} -af 'volume=0.85' {shlex.quote(s2)}", shell=True, check=True)

    s3 = os.path.join(OUTPUT_DIR, "m3_aud_s3.wav")
    subprocess.run(f"ffmpeg -y -ss 59 -t 3.5 -i {shlex.quote(track1_mp3)} -af 'volume=0.85' {shlex.quote(s3)}", shell=True, check=True)

    s4 = os.path.join(OUTPUT_DIR, "m3_aud_s4.wav")
    subprocess.run(f"ffmpeg -y -ss 29.5 -t 4.5 -i {shlex.quote(track1_mp3)} -af 'volume=0.85' {shlex.quote(s4)}", shell=True, check=True)

    s5 = os.path.join(OUTPUT_DIR, "m3_aud_s5.wav")
    subprocess.run(f"ffmpeg -y -ss 34 -t 3.06 -i {shlex.quote(track1_mp3)} -af 'atempo=1.02,volume=0.85' {shlex.quote(s5)}", shell=True, check=True)

    s6 = os.path.join(OUTPUT_DIR, "m3_aud_s6.wav")
    subprocess.run(f"ffmpeg -y -ss 37 -t 4.16 -i {shlex.quote(track1_mp3)} -af 'atempo=1.04,volume=0.85' {shlex.quote(s6)}", shell=True, check=True)

    s7 = os.path.join(OUTPUT_DIR, "m3_aud_s7.wav")
    subprocess.run(f"ffmpeg -y -ss 42 -t 19.0 -i {shlex.quote(track1_mp3)} -af 'volume=0.45' {shlex.quote(s7)}", shell=True, check=True)

    s8 = os.path.join(OUTPUT_DIR, "m3_aud_s8.wav")
    subprocess.run(f"ffmpeg -y -ss 5 -t 2.5 -i {shlex.quote(track2_mp3)} -af 'afade=t=in:ss=0:d=0.3,volume=0.8' {shlex.quote(s8)}", shell=True, check=True)

    s9 = os.path.join(OUTPUT_DIR, "m3_aud_s9.wav")
    subprocess.run(f"ffmpeg -y -ss 8 -t 2.5 -i {shlex.quote(track3_mp3)} -af 'afade=t=in:ss=0:d=0.3,volume=0.8' {shlex.quote(s9)}", shell=True, check=True)

    s10 = os.path.join(OUTPUT_DIR, "m3_aud_s10.wav")
    subprocess.run(f"ffmpeg -y -ss 12 -t 4.0 -i {shlex.quote(track5_mp3)} -af 'afade=t=in:ss=0:d=0.3,afade=t=out:st=2:d=2.0,volume=0.8' {shlex.quote(s10)}", shell=True, check=True)

    # Concat all audio segments
    concat_list = os.path.join(OUTPUT_DIR, "m3_audio_concat.txt")
    with open(concat_list, "w") as f:
        for seg in [s1, s2, s3, s4, s5, s6, s7, s8, s9, s10]:
            f.write(f"file '{os.path.basename(seg)}'\n")
            
    subprocess.run(f"cd {shlex.quote(OUTPUT_DIR)} && ffmpeg -y -f concat -safe 0 -i m3_audio_concat.txt -c copy {shlex.quote(master_audio)}", shell=True, check=True)
    print("Master audio synthesized successfully!")

    print("=== Step 6: Assembling Final MP4 with Video, Audio & Overlay Banners ===")
    final_mp4 = os.path.join(PROJECT_DIR, "m3_feature_demo.mp4")
    brain_final_mp4 = "/Users/tunghunglu/.gemini/antigravity/brain/08dfeb38-0fa3-4fe7-8e96-e395da77c858/m3_feature_demo.mp4"

    # Build ffmpeg filter graph for overlaying 7 banners
    input_args = [f"-i {shlex.quote(local_raw)}"]
    for p, st, et, bx, by in banner_items:
        input_args.append(f"-i {shlex.quote(p)}")
    input_args.append(f"-i {shlex.quote(master_audio)}")

    filter_complex = []
    # 1. Normalize video fps to 30 CFR
    filter_complex.append("[0:v]fps=30[v0]")
    last_v = "v0"
    for i, (p, st, et, bx, by) in enumerate(banner_items):
        out_v = f"v{i+1}"
        filter_complex.append(f"[{last_v}][{i+1}:v]overlay=x={bx}:y={by}:enable='between(t,{st},{et})'[{out_v}]")
        last_v = out_v

    filter_str = ";".join(filter_complex)
    audio_idx = len(banner_items) + 1

    ffmpeg_cmd = (
        f"ffmpeg -y {' '.join(input_args)} "
        f"-filter_complex \"{filter_str}\" "
        f"-map \"[{last_v}]\" -map {audio_idx}:a "
        f"-c:v libx264 -preset fast -crf 19 -pix_fmt yuv420p "
        f"-c:a aac -b:a 192k -shortest {shlex.quote(final_mp4)}"
    )

    print("Running FFmpeg composition command...")
    subprocess.run(ffmpeg_cmd, shell=True, check=True)

    # Copy to brain dir
    subprocess.run(f"cp {shlex.quote(final_mp4)} {shlex.quote(brain_final_mp4)}", shell=True, check=True)

    # Verify output file
    res = subprocess.run(f"ffprobe -v error -show_entries format=duration,size -of default=noprint_wrappers=1:nokey=1 {shlex.quote(final_mp4)}", shell=True, capture_output=True, text=True)
    print(f"Final M3 Feature Demo Video Generated: {final_mp4}")
    print(f"Duration & Size: {res.stdout.strip()}")
    print("Done!")

if __name__ == "__main__":
    skip = "--skip-record" in sys.argv
    main(skip_record=skip)
