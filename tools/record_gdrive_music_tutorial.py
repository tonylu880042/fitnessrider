#!/usr/bin/env python3
"""
Record a tutorial video showing how to import 10 Google Drive music tracks
into the FitnessRider Coach app via the in-app Music Library + DocumentsUI picker.

Correct flow:
  ClassList → "+" new class → ClassEditorScreen
  → "匯入音樂檔" → MusicLibraryScreen (Dialog)
  → "+ 匯入新檔" → DocumentsUI picker (already in 下载)
  → open drawer → tap 下载 (for tutorial clarity)
  → long-press first track → tap remaining 9 → "选择"
  → MusicLibraryScreen (匯入中...) → auto-dismiss → ClassEditorScreen
  → preview / seek / save

Emulator: 1280x800 landscape, grid view, DocumentsUI in 下载
"""
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

# ── Banner generator ─────────────────────────────────────────────
def generate_banner(text_top, text_sub, filename, width=820, height=72):
    """Generate a sleek glassmorphic banner with rounded corners."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    bg_color = (18, 22, 26, 240)
    draw.rounded_rectangle([(0, 0), (width, height)], radius=14,
                           fill=bg_color, outline=(255, 255, 255, 70), width=1)
    # Accent green stripe on left (#84BF09)
    draw.rounded_rectangle([(4, 4), (12, height - 4)], radius=4,
                           fill=(132, 191, 9, 255))

    font_top = ImageFont.truetype(FONT_PATH, 20)
    font_sub = ImageFont.truetype(FONT_PATH, 13)

    draw.text((24, 12), text_top, font=font_top, fill=(255, 255, 255, 255))
    draw.text((24, 41), text_sub, font=font_sub, fill=(185, 225, 120, 255))

    out_path = os.path.join(OUTPUT_DIR, filename)
    img.save(out_path)
    return out_path

# ── ADB helpers ──────────────────────────────────────────────────
def adb(cmd):
    return subprocess.run(f"adb {cmd}", shell=True, capture_output=True, text=True)

def tap(x, y, delay=0.5):
    adb(f"shell input tap {x} {y}")
    if delay > 0:
        time.sleep(delay)

def swipe(x1, y1, x2, y2, duration=400, delay=0.5):
    adb(f"shell input swipe {x1} {y1} {x2} {y2} {duration}")
    if delay > 0:
        time.sleep(delay)

def screenshot(path):
    adb(f"shell screencap -p /sdcard/_frame.png")
    adb(f"pull /sdcard/_frame.png {shlex.quote(path)}")

# ── Grid-view track card centres (verified from uiautomator dump) ──
# Row 0 (folder): GoogleDrive_Tracks [24,248][193,296] → not a track
# Row 1 (7 tracks, item_root Y 304~526):
TRACK_CENTRES_R1 = [
    (109, 415),   # You Will Never See Me Coming  [24,304][193,526]
    (286, 415),   # The Itch                       [201,304][370,526]
    (463, 415),   # Hit the Switch                 [378,304][547,526]
    (640, 415),   # Blue Skies                     [555,304][724,526]
    (817, 415),   # Alternate                      [732,304][901,526]
    (994, 415),   # Level Up                       [909,304][1078,526]
    (1171, 415),  # Marvin's Dance                 [1086,304][1256,526]
]
# Row 2 (3 tracks, item_root Y 534~756):
TRACK_CENTRES_R2 = [
    (109, 645),   # Spring In My Step              [24,534][193,756]
    (286, 645),   # Take That Back                 [201,534][370,756]
    (463, 645),   # Gas Pedal                      [378,534][547,756]
]
ALL_TRACKS = TRACK_CENTRES_R1 + TRACK_CENTRES_R2   # 10 tracks total

# ── Main ─────────────────────────────────────────────────────────
def main():
    TOTAL_DURATION = 80  # seconds

    # ━━ Step 1: Generate overlay banners ━━━━━━━━━━━━━━━━━━━━━━━━━
    print("=== Step 1: Generating tutorial banners ===")
    banners_cfg = [
        # (filename, title, subtitle, start_s, end_s, overlay_x, overlay_y)
        ("gdrive_b1.png",
         "【Google Drive 雲端音樂匯入教學】 一鍵載入飛輪課表曲目",
         "支援 Google 雲端硬碟、本機下載資料夾與隨身碟，100% 離線穩定授課",
         0.5, 8.0, 230, 700),
        ("gdrive_b2.png",
         "【步驟 01】 進入課表編輯器 → 點選「匯入音樂檔」",
         "自動開啟應用程式音樂庫，可管理已匯入的曲目或匯入新檔案",
         8.0, 18.0, 230, 700),
        ("gdrive_b3.png",
         "【步驟 02】 點選「匯入新檔」→ 開啟系統檔案選擇器",
         "選擇「下載」資料夾，即可看到從 Google 雲端硬碟批量下載的音樂",
         18.0, 30.0, 230, 700),
        ("gdrive_b4.png",
         "【步驟 03】 長按啟動多選模式 → 批量勾選歌曲",
         "長按第一首進入多選狀態，依序點選其餘曲目後按右上角「選擇」確認",
         30.0, 46.0, 230, 700),
        ("gdrive_b5.png",
         "【步驟 04】 高精度 PCM 音訊分析 → 波形與 BPM 踏頻自動偵測",
         "自動偵測音樂節奏 BPM（如 107 BPM），繪製 800 點等比例振幅波形",
         46.0, 64.0, 230, 700),
        ("gdrive_b6.png",
         "【步驟 05】 即時試聽確認 → 拖曳波形定位 → 儲存課表即刻開騎",
         "享受專業無損鎖調播放，標記阻力與站騎姿勢，點擊「儲存」完成！",
         64.0, 78.0, 230, 700),
    ]
    banner_items = []
    for fn, top, sub, st, et, bx, by in banners_cfg:
        p = generate_banner(top, sub, fn)
        banner_items.append((p, st, et, bx, by))
        print(f"  Generated {fn}")

    # ━━ Step 2: Prepare emulator ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    print("=== Step 2: Preparing emulator state ===")
    adb("shell am force-stop com.fitnessrider.coach")
    adb("shell am force-stop com.google.android.documentsui")
    time.sleep(1)

    # Clear previously imported music so MusicLibraryScreen starts empty
    adb("shell run-as com.fitnessrider.coach rm -rf /data/data/com.fitnessrider.coach/files/Music/* 2>/dev/null")

    # Launch app fresh
    adb("shell am start -n com.fitnessrider.coach/com.fitnessrider.MainActivity")
    time.sleep(6.0)

    # Capture initial frame for verification
    screenshot(os.path.join(OUTPUT_DIR, "verify_classlist.png"))

    # ━━ Step 3: Start screen recording ━━━━━━━━━━━━━━━━━━━━━━━━━━
    print(f"=== Step 3: Starting screen recording ({TOTAL_DURATION}s) ===")
    raw_video_device = "/sdcard/gdrive_tut_raw.mp4"
    local_raw = os.path.join(OUTPUT_DIR, "gdrive_tut_raw.mp4")
    adb(f"shell rm -f {raw_video_device}")

    record_proc = subprocess.Popen([
        "adb", "shell", "screenrecord",
        "--size", "1280x800",
        "--bit-rate", "8000000",
        "--time-limit", str(TOTAL_DURATION + 2),
        raw_video_device
    ])
    start_time = time.time()
    print(f"  Recording started at {start_time:.0f}")

    def elapsed():
        return time.time() - start_time

    def wait_until(t):
        while elapsed() < t:
            time.sleep(0.05)

    # ──────────────────────────────────────────────────────────────
    # Scene 1 (0~8s): ClassList → tap "+" → ClassEditorScreen
    # ──────────────────────────────────────────────────────────────
    print("[Scene 1] ClassList → Create new class")
    time.sleep(2.5)
    tap(1240, 28, delay=2.5)          # "新增課表" button [1228,16][1252,40]
    # Now in ClassEditorScreen
    wait_until(8.0)

    # ──────────────────────────────────────────────────────────────
    # Scene 2 (8~18s): Tap "匯入音樂檔" → MusicLibraryScreen dialog
    # ──────────────────────────────────────────────────────────────
    print("[Scene 2] Tap 匯入音樂檔 → MusicLibraryScreen")
    tap(1213, 88, delay=3.0)          # "匯入音樂檔" button
    # MusicLibraryScreen dialog appears with tabs
    time.sleep(2.0)
    wait_until(15.0)

    # ──────────────────────────────────────────────────────────────
    # Scene 3 (15~30s): Tap "+ 匯入新檔" → DocumentsUI → drawer → 下载
    # ──────────────────────────────────────────────────────────────
    print("[Scene 3] Tap + 匯入新檔 → DocumentsUI")
    tap(1219, 52, delay=3.5)          # "匯入新檔" button [1186,40][1252,64]
    # DocumentsUI picker opens (should already be in 下载 from memory)

    # Open drawer for tutorial clarity
    print("[Scene 3] Opening drawer...")
    tap(44, 56, delay=2.0)            # Hamburger menu icon [16,32][72,80]
    print("[Scene 3] Tap 下载...")
    tap(160, 220, delay=3.0)          # 下载 drawer item [0,192][320,248]
    # Now we see the grid of 10 tracks + 1 folder
    wait_until(30.0)

    # ──────────────────────────────────────────────────────────────
    # Scene 4 (30~46s): Long-press first → select more → 选择
    # ──────────────────────────────────────────────────────────────
    print("[Scene 4] Multi-selecting tracks...")
    time.sleep(0.5)

    # Long-press first track to enter multi-select
    cx, cy = ALL_TRACKS[0]
    swipe(cx, cy, cx, cy, duration=1200, delay=1.5)
    print("  Long-pressed track 1")

    # Single-tap tracks 2 and 3 with clear delay
    for i, (cx, cy) in enumerate(ALL_TRACKS[1:3], start=2):
        tap(cx, cy, delay=0.8)
        print(f"  Selected track {i}")

    time.sleep(1.5)
    print("[Scene 4] Tapping 选择...")
    tap(1198, 48, delay=3.0)          # "选择" button [1172,24][1224,72]

    # ──────────────────────────────────────────────────────────────
    # Scene 5 (46~64s): Import processing → waveform + BPM analysis
    #   App copies files, analyses PCM waveforms, detects BPM.
    #   MusicLibraryScreen shows "匯入中..." then auto-dismisses
    #   → ClassEditorScreen shows segments with waveform/BPM.
    # ──────────────────────────────────────────────────────────────
    print("[Scene 5] Waiting for import & waveform analysis...")
    wait_until(64.0)

    # ──────────────────────────────────────────────────────────────
    # Scene 6 (64~78s): Preview, seek waveform, save
    # ──────────────────────────────────────────────────────────────
    print("[Scene 6] Audio preview & save")
    # Tap 試聽 (Play) button
    tap(50, 454, delay=4.0)           # "試聽" play button

    # Seek on waveform
    print("[Scene 6] Seeking on waveform...")
    tap(500, 370, delay=3.0)          # Seek to middle

    # Save
    print("[Scene 6] Saving class...")
    tap(1235, 28, delay=2.0)          # "儲存" button

    wait_until(TOTAL_DURATION)

    # ━━ Step 4: Pull raw video ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    print("Waiting for screenrecord to finish...")
    record_proc.wait()
    print(f"  Recording finished ({elapsed():.1f}s)")

    print("=== Step 4: Pulling raw video ===")
    adb(f"pull {raw_video_device} {local_raw}")
    if not os.path.exists(local_raw) or os.path.getsize(local_raw) < 1000:
        print("ERROR: Failed to pull raw video!")
        sys.exit(1)
    print(f"  Pulled {local_raw} ({os.path.getsize(local_raw)} bytes)")

    # ━━ Step 5: Synthesize background audio ━━━━━━━━━━━━━━━━━━━━━
    print("=== Step 5: Synthesizing audio ===")
    track1 = os.path.join(MUSICS_DIR, "Spring In My Step - Silent Partner.mp3")
    track2 = os.path.join(MUSICS_DIR, "Take That Back - Silent Partner.mp3")

    master_audio = os.path.join(OUTPUT_DIR, "gdrive_master_audio.wav")

    segs = [
        # (output_name, source, start_s, duration_s, af_filter)
        ("s1.wav", track1, 0,    30.0, "afade=t=in:ss=0:d=1.5,volume=0.2"),
        ("s2.wav", track1, 30.0, 16.0, "afade=t=out:st=13:d=3,volume=0.18"),
        ("s3.wav", track2, 0,    18.0, "afade=t=in:ss=0:d=2,volume=0.25"),
        ("s4.wav", track2, 18,    4.0, "volume=0.85"),            # preview play
        ("s5.wav", track2, 60,    3.0, "volume=0.85"),            # seek play
        ("s6.wav", track2, 63,    9.0, "afade=t=out:st=5:d=4,volume=0.4"),  # outro
    ]

    seg_paths = []
    for name, src, ss, dur, af in segs:
        out = os.path.join(OUTPUT_DIR, name)
        subprocess.run(
            f"ffmpeg -y -ss {ss} -t {dur} -i {shlex.quote(src)} "
            f"-af '{af}' {shlex.quote(out)}",
            shell=True, check=True
        )
        seg_paths.append(out)

    concat_list = os.path.join(OUTPUT_DIR, "gdrive_audio_concat.txt")
    with open(concat_list, "w") as f:
        for seg in seg_paths:
            f.write(f"file '{os.path.basename(seg)}'\n")

    subprocess.run(
        f"cd {shlex.quote(OUTPUT_DIR)} && ffmpeg -y -f concat -safe 0 "
        f"-i gdrive_audio_concat.txt -c copy {shlex.quote(master_audio)}",
        shell=True, check=True
    )
    print("  Audio synthesized!")

    # ━━ Step 6: Compose final video with banners + audio ━━━━━━━
    print("=== Step 6: Assembling final MP4 ===")
    final_mp4 = os.path.join(PROJECT_DIR, "google_drive_music_import_tutorial.mp4")
    brain_mp4 = (
        "/Users/tunghunglu/.gemini/antigravity/brain/"
        "9b613c2d-4475-409e-9421-afb75644330e/"
        "google_drive_music_import_tutorial.mp4"
    )

    # Build ffmpeg inputs
    input_args = [f"-i {shlex.quote(local_raw)}"]
    for p, st, et, bx, by in banner_items:
        input_args.append(f"-i {shlex.quote(p)}")
    input_args.append(f"-i {shlex.quote(master_audio)}")

    # Build overlay filter chain
    filter_parts = ["[0:v]fps=30[v0]"]
    last_v = "v0"
    for i, (p, st, et, bx, by) in enumerate(banner_items):
        out_v = f"v{i+1}"
        filter_parts.append(
            f"[{last_v}][{i+1}:v]overlay=x={bx}:y={by}:"
            f"enable='between(t,{st},{et})'[{out_v}]"
        )
        last_v = out_v

    filter_str = ";".join(filter_parts)
    audio_idx = len(banner_items) + 1

    ffmpeg_cmd = (
        f"ffmpeg -y {' '.join(input_args)} "
        f'-filter_complex "{filter_str}" '
        f'-map "[{last_v}]" -map {audio_idx}:a '
        f"-c:v libx264 -preset fast -crf 19 -pix_fmt yuv420p "
        f"-c:a aac -b:a 192k -shortest {shlex.quote(final_mp4)}"
    )

    print("  Running FFmpeg...")
    subprocess.run(ffmpeg_cmd, shell=True, check=True)

    # Copy to brain dir for artifact
    subprocess.run(f"cp {shlex.quote(final_mp4)} {shlex.quote(brain_mp4)}",
                   shell=True, check=True)

    # Verify
    res = subprocess.run(
        f"ffprobe -v error -show_entries format=duration,size "
        f"-of default=noprint_wrappers=1:nokey=1 {shlex.quote(final_mp4)}",
        shell=True, capture_output=True, text=True
    )
    print(f"\n✅ Final Tutorial Video: {final_mp4}")
    print(f"   Duration & Size: {res.stdout.strip()}")
    print("Done!")

if __name__ == "__main__":
    main()
