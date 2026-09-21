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

os.makedirs(OUTPUT_DIR, exist_ok=True)

def generate_banner(text_top, text_sub, filename, width=740, height=68):
    """Generate a sleek glassmorphic banner with rounded corners."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Rounded background with dark translucent tint and subtle border
    bg_color = (18, 22, 26, 230)
    draw.rounded_rectangle([(0, 0), (width, height)], radius=14, fill=bg_color, outline=(255, 255, 255, 60), width=1)
    
    # Accent green stripe on left
    draw.rounded_rectangle([(4, 4), (12, height - 4)], radius=4, fill=(124, 179, 66, 255))
    
    font_top = ImageFont.truetype(FONT_PATH, 20)
    font_sub = ImageFont.truetype(FONT_PATH, 14)
    
    # Text
    draw.text((24, 11), text_top, font=font_top, fill=(255, 255, 255, 255))
    draw.text((24, 38), text_sub, font=font_sub, fill=(175, 215, 155, 255))
    
    out_path = os.path.join(OUTPUT_DIR, filename)
    img.save(out_path)
    return out_path

def adb(cmd):
    """Execute adb shell command."""
    return subprocess.run(f"adb {cmd}", shell=True, capture_output=True, text=True)

def tap(x, y, delay=0.5):
    adb(f"shell input tap {x} {y}")
    time.sleep(delay)

def swipe(x1, y1, x2, y2, duration=400, delay=0.5):
    adb(f"shell input swipe {x1} {y1} {x2} {y2} {duration}")
    time.sleep(delay)

def main():
    print("=== Step 1: Pre-generating sleek UI overlay banners ===")
    banners_cfg = [
        ("banner1.png", "【01 / 課表總覽】 10 首曲目完整編排 · 480 kcal · 三色強度配比彩條", "暖身、平路加速、站姿跑步、重爬坡、跳躍、衝刺與緩和收操完整結構", 0.5, 4.8, 270, 690),
        ("banner2.png", "【02 / 課表編輯器】 10 段曲目橫向滑動 · 參數與動作點即時預覽", "支援橫向滑動切換 10 段曲目，時長、目標 BPM 與動作點計數一目了然", 5.2, 13.2, 270, 690),
        ("banner3.png", "【03 / 姿勢與口訣庫】 7 大姿勢連動建議把位 · 40+ 專業口訣庫", "把手 1/2/3 號位幾何視覺高亮，教練口訣分門別類自由勾選與自訂", 13.8, 26.5, 270, 690),
        ("banner4.png", "【04 / HUD 實時座艙】 握把把位大圖示 · 舊版經典騎乘姿勢回歸 · 300dp 儀表", "圓圈左上方大把位指示、右方騎士姿勢動態圖示、姿勢效益說明彈窗，大螢幕投影更清晰", 28.0, 39.5, 270, 68),
        ("banner5.png", "【05 / 即時無縫跳曲】 站姿重爬坡 (把位 3 牛角) · 阻力 LEVEL 7", "座艙內點選左側曲目清單，ExoPlayer 無縫切換音樂並即時刷新教練指引", 40.0, 47.0, 270, 68),
        ("banner6.png", "【06 / 無氧極限衝刺】 110 RPM 爆發 · 把位 3 · 阻力 LEVEL 5", "強烈節奏音樂瞬間切入，特大字 110 RPM 踏頻指示，進入燃脂高潮", 47.5, 54.0, 270, 68),
        ("banner7.png", "【07 / 無損鎖調變速】 ±2% 即時踏頻微調 · ExoPlayer 專業鎖調", "即時加速 +2% / +4%，音訊時間無損延展（Pitch-Lock），音質絕不變調", 54.5, 61.5, 270, 68),
        ("banner8.png", "【08 / 跨平台匯出】 一鍵打包 .riderclass 課表包 · 支援 iOS/Android", "包含 10 首曲目音訊與完整動作點 JSON，教練間跨平台一鍵分享匯入", 62.5, 70.5, 270, 690),
    ]
    
    banner_items = []
    for fn, top, sub, st, et, bx, by in banners_cfg:
        p = generate_banner(top, sub, fn)
        banner_items.append((p, st, et, bx, by))
        print(f"Generated {fn}")

    print("=== Step 2: Ensuring emulator is freshly on Class List ===")
    # Check if app is on class list; if not, restart cleanly
    dump_res = subprocess.run("adb shell uiautomator dump /sdcard/pre_dump.xml && adb shell cat /sdcard/pre_dump.xml", shell=True, capture_output=True, text=True)
    if "45min" not in dump_res.stdout:
        print("Resetting app to Class List...")
        adb("shell am force-stop com.fitnessrider.coach")
        adb("shell am start -n com.fitnessrider.coach/com.fitnessrider.MainActivity")
        time.sleep(3.5)
    else:
        print("App is already on Class List with seeded class!")

    print("=== Step 3: Starting screen recording on Android ===")
    raw_video_device = "/sdcard/client_demo_raw.mp4"
    adb(f"shell rm -f {raw_video_device}")
    
    # 72s recording limit
    record_proc = subprocess.Popen(["adb", "shell", "screenrecord", "--size", "1280x800", "--bit-rate", "8000000", "--time-limit", "73", raw_video_device])
    start_time = time.time()
    print(f"Recording started at {start_time}")

    def elapsed():
        return time.time() - start_time

    # Scenario execution
    print("[Scene 1] Class Overview (0.0s ~ 5.0s)")
    time.sleep(4.5)

    print("[Scene 2] Segment Editor (5.0s ~ 13.5s)")
    tap(1212, 191, delay=1.5) # Enter editor
    time.sleep(1.0)
    swipe(950, 480, 320, 480, duration=450, delay=1.5) # Swipe to Seg 2
    swipe(950, 480, 320, 480, duration=450, delay=1.5) # Swipe to Seg 3
    swipe(320, 480, 950, 480, duration=450, delay=1.5) # Swipe back to Seg 1

    print("[Scene 3] Cue & Coaching Library Editor (13.5s ~ 27.0s)")
    tap(300, 440, delay=1.8) # Open Cue Editor dialog
    time.sleep(1.0)
    tap(500, 428, delay=1.0) # Position 2
    tap(635, 428, delay=1.0) # Position 3
    tap(440, 428, delay=1.0) # Position 1
    time.sleep(0.5)
    tap(844, 639, delay=1.8) # "選取口訣庫..."
    time.sleep(1.0)
    # Check 1 or 2 items
    tap(846, 672, delay=1.2) # "完成 (3)" in picker
    tap(857, 720, delay=1.2) # "確定" in cue dialog
    tap(1250, 32, delay=1.2) # "儲存" in editor -> returns to Class List
    time.sleep(1.0)

    print("[Scene 4] HUD Cockpit (27.0s ~ 62.0s)")
    hud_start_time = elapsed()
    print(f"HUD entered at elapsed={hud_start_time:.2f}s")
    tap(95, 191, delay=2.0) # "上課開騎" -> enters HUD
    time.sleep(4.0) # Observe circular ring and rolling reminders

    # Posture benefit dialog
    print("Opening Posture Benefit Dialog")
    tap(1040, 248, delay=3.5) # Click "騎乘姿勢 ⓘ"
    tap(638, 660, delay=1.5) # Click "我知道了" to close dialog

    while elapsed() < 40.0:
        time.sleep(0.05)

    # Track 5 Switch
    track5_switch_time = elapsed()
    print(f"Switching to Track 5 at elapsed={track5_switch_time:.2f}s")
    tap(100, 440, delay=2.0) # Tap Track 5 on left drawer
    time.sleep(4.5) # Enjoy Standing Climb 60 RPM, Hand Position 3

    while elapsed() < 47.5:
        time.sleep(0.05)

    # Track 9 Switch
    track9_switch_time = elapsed()
    print(f"Switching to Track 9 (Sprint) at elapsed={track9_switch_time:.2f}s")
    tap(100, 671, delay=2.0) # Tap Track 9 on left drawer
    time.sleep(4.0) # Enjoy All-Out Sprint 110 RPM, Hand Position 3

    while elapsed() < 54.5:
        time.sleep(0.05)

    # Tempo Stepping (+2%, +4%)
    tempo_step1_time = elapsed()
    print(f"Tempo step +2% at elapsed={tempo_step1_time:.2f}s")
    tap(881, 588, delay=2.8) # +2%
    tempo_step2_time = elapsed()
    print(f"Tempo step +4% at elapsed={tempo_step2_time:.2f}s")
    tap(881, 588, delay=3.5) # +2% again -> +4%

    while elapsed() < 62.0:
        time.sleep(0.05)

    # Exit HUD back to list
    hud_exit_time = elapsed()
    print(f"Exiting HUD at elapsed={hud_exit_time:.2f}s")
    tap(40, 32, delay=2.0) # Back arrow

    print("[Scene 5] Cross-Platform Export (62.0s ~ 71.0s)")
    while elapsed() < 64.5:
        time.sleep(0.05)

    tap(1176, 111, delay=3.5) # Tap Share to export .riderclass
    
    # Touch screen gently to force last frame update
    tap(600, 400, delay=1.0)
    
    print("Waiting for recording to complete...")
    record_proc.wait()
    total_time = elapsed()
    print(f"Recording completed! Total wall-clock time: {total_time:.2f}s")

    print("=== Step 4: Pulling raw video from device ===")
    local_raw = os.path.join(OUTPUT_DIR, "client_demo_raw.mp4")
    adb(f"pull {raw_video_device} {local_raw}")
    if not os.path.exists(local_raw) or os.path.getsize(local_raw) < 1000:
        print("ERROR: Failed to pull raw video!")
        sys.exit(1)
    print(f"Pulled {local_raw} ({os.path.getsize(local_raw)} bytes)")

    print("=== Step 5: Master audio synthesis & synchronization ===")
    track1_mp3 = os.path.join(MUSICS_DIR, "Spring In My Step - Silent Partner.mp3")
    track5_mp3 = os.path.join(MUSICS_DIR, "Alternate - Vibe Tracks.mp3")
    track9_mp3 = os.path.join(MUSICS_DIR, "You Will Never See Me Coming - NEFFEX.mp3")

    master_audio = os.path.join(OUTPUT_DIR, "master_audio.wav")
    
    # Seg 1: 0 - 27.5s (27.5s) Intro & Editor phase music (Track 1 at 25% volume)
    s1 = os.path.join(OUTPUT_DIR, "aud_s1.wav")
    subprocess.run(f"ffmpeg -y -ss 0 -t 27.5 -i '{track1_mp3}' -af 'afade=t=in:ss=0:d=1.5,volume=0.25' '{s1}'", shell=True, check=True)
    
    # Seg 2: 27.5 - 40.0s (12.5s) HUD Track 1 (Spring In My Step at 85% volume)
    s2 = os.path.join(OUTPUT_DIR, "aud_s2.wav")
    subprocess.run(f"ffmpeg -y -ss 27.5 -t 12.5 -i '{track1_mp3}' -af 'volume=0.85' '{s2}'", shell=True, check=True)
    
    # Seg 3: 40.0 - 47.5s (7.5s) HUD Track 5 (Alternate at 85% volume)
    s3 = os.path.join(OUTPUT_DIR, "aud_s3.wav")
    subprocess.run(f"ffmpeg -y -ss 15 -t 7.5 -i '{track5_mp3}' -af 'afade=t=in:ss=0:d=0.4,volume=0.85' '{s3}'", shell=True, check=True)
    
    # Seg 4: 47.5 - 54.5s (7.0s) HUD Track 9 (NEFFEX sprint at 85% volume)
    s4 = os.path.join(OUTPUT_DIR, "aud_s4.wav")
    subprocess.run(f"ffmpeg -y -ss 20 -t 7.0 -i '{track9_mp3}' -af 'afade=t=in:ss=0:d=0.4,volume=0.85' '{s4}'", shell=True, check=True)

    # Seg 5: 54.5 - 57.5s (3.0s) HUD Track 9 tempo 102%
    s5 = os.path.join(OUTPUT_DIR, "aud_s5.wav")
    subprocess.run(f"ffmpeg -y -ss 27 -t 3.06 -i '{track9_mp3}' -af 'atempo=1.02,volume=0.85' '{s5}'", shell=True, check=True)

    # Seg 6: 57.5 - 62.5s (5.0s) HUD Track 9 tempo 104%
    s6 = os.path.join(OUTPUT_DIR, "aud_s6.wav")
    subprocess.run(f"ffmpeg -y -ss 30.06 -t 5.2 -i '{track9_mp3}' -af 'atempo=1.04,volume=0.85' '{s6}'", shell=True, check=True)

    # Seg 7: 62.5 - 72.0s (9.5s) Outro fade back in Class List
    s7 = os.path.join(OUTPUT_DIR, "aud_s7.wav")
    subprocess.run(f"ffmpeg -y -ss 40 -t 9.5 -i '{track1_mp3}' -af 'afade=t=out:st=5:d=4.5,volume=0.3' '{s7}'", shell=True, check=True)

    # Concat all audio segments
    concat_list = os.path.join(OUTPUT_DIR, "audio_concat.txt")
    with open(concat_list, "w") as f:
        for seg in [s1, s2, s3, s4, s5, s6, s7]:
            f.write(f"file '{seg}'\n")
            
    subprocess.run(f"ffmpeg -y -f concat -safe 0 -i '{concat_list}' -c copy '{master_audio}'", shell=True, check=True)
    print("Master audio synthesized successfully!")

    print("=== Step 6: Assembling Final MP4 with Video, Audio & Overlay Banners ===")
    final_mp4 = os.path.join(PROJECT_DIR, "client_demo.mp4")
    brain_final_mp4 = "/Users/tunghunglu/.gemini/antigravity/brain/353c88a3-ab2e-4ab8-a5d8-9980e2b846ec/client_demo.mp4"

    # Build ffmpeg filter graph for overlaying 8 banners
    input_args = [f"-i '{local_raw}'"]
    for p, st, et, bx, by in banner_items:
        input_args.append(f"-i '{p}'")
    input_args.append(f"-i '{master_audio}'")

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
        f"-c:a aac -b:a 192k -shortest '{final_mp4}'"
    )

    print("Running FFmpeg composition command...")
    subprocess.run(ffmpeg_cmd, shell=True, check=True)

    # Copy to brain dir
    subprocess.run(f"cp '{final_mp4}' '{brain_final_mp4}'", shell=True, check=True)

    # Verify output file
    res = subprocess.run(f"ffprobe -v error -show_entries format=duration,size -of default=noprint_wrappers=1:nokey=1 '{final_mp4}'", shell=True, capture_output=True, text=True)
    print(f"Final Demo Video Generated: {final_mp4}")
    print(f"Duration & Size: {res.stdout.strip()}")
    print("Done!")

if __name__ == "__main__":
    main()
