#!/usr/bin/env python3
import os
import sys
import time
import shlex
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

def swipe(x1, y1, x2, y2, duration=400, delay=0.5):
    adb(f"shell input swipe {x1} {y1} {x2} {y2} {duration}")
    if delay > 0:
        time.sleep(delay)

def main():
    print("=== Step 1: Pre-generating sleek tutorial banners ===")
    banners_cfg = [
        ("tut_b1.png", "【Android 課表音樂匯入教學】 輕鬆擴充與自訂飛輪曲目", "支援為既有段落更換音樂，或滑動至最右側點擊「+ 新增段落」建立全新曲目", 0.5, 7.5, 260, 680),
        ("tut_b2.png", "【步驟 01】 點選段落右上角「匯入音樂檔」按鈕", "點擊「匯入音樂檔」，系統將自動啟動 Android 原生安全檔案選擇器 (DocumentsUI)", 7.5, 15.0, 260, 680),
        ("tut_b3.png", "【步驟 02】 點擊左上側邊選單 · 瀏覽下載或音樂資料夾", "可自由選取 Android 手機／平板「下載 (Download)」、「音樂 (Music)」或雲端硬碟檔案", 15.0, 24.0, 260, 680),
        ("tut_b4.png", "【步驟 03】 點選欲載入之 MP3 / M4A / WAV 音樂檔案", "點選檔案後，系統自動將音訊複製至 App 專屬安全沙盒目錄並持久化保存", 24.0, 32.5, 260, 680),
        ("tut_b5.png", "【步驟 04】 自動串流 PCM 分析 · 800 點波形與 BPM 瞬時成型", "自動分析音訊時長、偵測音樂節奏 BPM，並以高精度 800 點振幅柱呈現音樂波形", 32.5, 41.5, 260, 680),
        ("tut_b6.png", "【步驟 05】 即時試聽確認 · 標記 Cue 點 · 點選「儲存」完成編排", "點擊試聽享受無損鎖調播放，隨時標記踏頻與姿勢，完成後點選儲存即刻開騎！", 41.5, 50.0, 260, 680),
    ]
    
    banner_items = []
    for fn, top, sub, st, et, bx, by in banners_cfg:
        p = generate_banner(top, sub, fn)
        banner_items.append((p, st, et, bx, by))
        print(f"Generated {fn}")

    print("=== Step 2: Preparing emulator files and app state ===")
    # Push sample music to Download folder
    adb(f"push {shlex.quote(os.path.join(MUSICS_DIR, 'Gas Pedal - Diamond Ortiz.mp3'))} /sdcard/Download/")
    adb("shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file:///sdcard/Download/Gas Pedal - Diamond Ortiz.mp3'")
    
    # Restart app to Class List
    adb("shell am force-stop com.fitnessrider.coach")
    adb("shell am start -n com.fitnessrider.coach/com.fitnessrider.MainActivity")
    time.sleep(3.5)

    print("=== Step 3: Starting screen recording on Android (50s) ===")
    raw_video_device = "/sdcard/tut_demo_raw.mp4"
    local_raw = os.path.join(OUTPUT_DIR, "tut_demo_raw.mp4")
    adb(f"shell rm -f {raw_video_device}")
    
    record_proc = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "1280x800", "--bit-rate", "8000000", "--time-limit", "51", raw_video_device])
    start_time = time.time()
    print(f"Recording started at {start_time}")

    def elapsed():
        return time.time() - start_time

    # [Scene 1: 0.0s ~ 7.5s] Enter Class Editor & Add New Segment
    print("[Scene 1] Class List -> Enter Editor -> Swipe to add segment...")
    time.sleep(1.5)
    tap(1212, 191, delay=2.0) # Tap "編輯" to enter editor
    swipe(1100, 180, 200, 180, duration=450, delay=1.2) # Swipe segments to right
    tap(1200, 199, delay=1.5) # Tap "+ 新增段落"
    
    while elapsed() < 7.5:
        time.sleep(0.05)

    # [Scene 2: 7.5s ~ 15.0s] Tap "匯入音樂檔" to open system file picker
    print("[Scene 2] Tap 匯入音樂檔...")
    tap(1220, 88, delay=2.0) # Tap "匯入音樂檔"
    
    while elapsed() < 15.0:
        time.sleep(0.05)

    # [Scene 3: 15.0s ~ 24.0s] Open Drawer & Select "下载 (Download)"
    print("[Scene 3] Open Drawer -> Tap 下载...")
    tap(44, 56, delay=1.8) # Tap drawer hamburger menu
    tap(188, 220, delay=2.0) # Tap "下载"
    
    while elapsed() < 24.0:
        time.sleep(0.05)

    # [Scene 4: 24.0s ~ 32.5s] Select Gas Pedal - Diamond Ortiz.mp3
    print("[Scene 4] Selecting Gas Pedal - Diamond Ortiz.mp3...")
    time.sleep(1.0)
    tap(130, 490, delay=2.5) # Tap Gas Pedal file in Downloads list
    
    while elapsed() < 32.5:
        time.sleep(0.05)

    # [Scene 5: 32.5s ~ 41.5s] Waveform & BPM Loaded
    print("[Scene 5] Observing loaded waveform and detected BPM...")
    while elapsed() < 41.5:
        time.sleep(0.05)

    # [Scene 6: 41.5s ~ 50.0s] Audio Preview & Save Class
    print("[Scene 6] Starting live audio preview...")
    tap(50, 454, delay=0.1) # Click 試聽 (Play)
    time.sleep(3.5)
    
    print("[Scene 6] Seeking on waveform...")
    tap(600, 370, delay=0.1) # Seek midway
    time.sleep(2.5)
    
    print("[Scene 6] Saving Class...")
    tap(1235, 28, delay=1.5) # Click 儲存
    
    while elapsed() < 50.0:
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

    print("=== Step 5: Synthesizing master audio matching demonstrated actions ===")
    track1_mp3 = os.path.join(MUSICS_DIR, "Spring In My Step - Silent Partner.mp3")
    gaspedal_mp3 = os.path.join(MUSICS_DIR, "Gas Pedal - Diamond Ortiz.mp3")

    master_audio = os.path.join(OUTPUT_DIR, "tut_master_audio.wav")
    
    # Audio segments:
    # Seg 1: 0 - 15.0s (15.0s) Ambient intro while opening editor
    s1 = os.path.join(OUTPUT_DIR, "tut_aud_s1.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 15.0 -i {shlex.quote(track1_mp3)} -af 'afade=t=in:ss=0:d=1.5,volume=0.2' {shlex.quote(s1)}", shell=True, check=True)

    # Seg 2: 15.0 - 32.5s (17.5s) Soft background while picking file
    s2 = os.path.join(OUTPUT_DIR, "tut_aud_s2.wav")
    subprocess.run(f"ffmpeg -y -ss 15.0 -t 17.5 -i {shlex.quote(track1_mp3)} -af 'afade=t=out:st=15:d=2.5,volume=0.15' {shlex.quote(s2)}", shell=True, check=True)

    # Seg 3: 32.5 - 41.5s (9.0s) Subtle anticipation as waveform analyzes
    s3 = os.path.join(OUTPUT_DIR, "tut_aud_s3.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 9.0 -i {shlex.quote(gaspedal_mp3)} -af 'afade=t=in:ss=0:d=2.0,volume=0.25' {shlex.quote(s3)}", shell=True, check=True)

    # Seg 4: 41.5 - 45.0s (3.5s) Live audio preview (0s ~ 3.5s of Gas Pedal)
    s4 = os.path.join(OUTPUT_DIR, "tut_aud_s4.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 3.5 -i {shlex.quote(gaspedal_mp3)} -af 'volume=0.85' {shlex.quote(s4)}", shell=True, check=True)

    # Seg 5: 45.0 - 48.0s (3.0s) Live audio seek (100s ~ 103s of Gas Pedal)
    s5 = os.path.join(OUTPUT_DIR, "tut_aud_s5.wav")
    subprocess.run(f"ffmpeg -y -ss 100 -t 3.0 -i {shlex.quote(gaspedal_mp3)} -af 'volume=0.85' {shlex.quote(s5)}", shell=True, check=True)

    # Seg 6: 48.0 - 50.5s (2.5s) Outro fade back to Class List
    s6 = os.path.join(OUTPUT_DIR, "tut_aud_s6.wav")
    subprocess.run(f"ffmpeg -y -ss 103 -t 2.5 -i {shlex.quote(gaspedal_mp3)} -af 'afade=t=out:st=0.5:d=2.0,volume=0.4' {shlex.quote(s6)}", shell=True, check=True)

    # Concat all audio segments
    concat_list = os.path.join(OUTPUT_DIR, "tut_audio_concat.txt")
    with open(concat_list, "w") as f:
        for seg in [s1, s2, s3, s4, s5, s6]:
            f.write(f"file '{os.path.basename(seg)}'\n")
            
    subprocess.run(f"cd {shlex.quote(OUTPUT_DIR)} && ffmpeg -y -f concat -safe 0 -i tut_audio_concat.txt -c copy {shlex.quote(master_audio)}", shell=True, check=True)
    print("Master tutorial audio synthesized successfully!")

    print("=== Step 6: Assembling Final MP4 with Video, Audio & Tutorial Banners ===")
    final_mp4 = os.path.join(PROJECT_DIR, "android_import_music_tutorial.mp4")
    brain_final_mp4 = "/Users/tunghunglu/.gemini/antigravity/brain/08dfeb38-0fa3-4fe7-8e96-e395da77c858/android_import_music_tutorial.mp4"

    # Build ffmpeg filter graph for overlaying 6 banners
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
    print(f"Final Android Import Music Tutorial Video Generated: {final_mp4}")
    print(f"Duration & Size: {res.stdout.strip()}")
    print("Done!")

if __name__ == "__main__":
    main()
