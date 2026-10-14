#!/usr/bin/env python3
"""錄製「FitnessRider 連接 Spotify 並整合歌單」教學影片（Android 實機）。

和其他 record_*.py 不同的地方：
- 對象是「實機」而不是模擬器。模擬器裝不了 Spotify，而且需要教練自己登入的帳號。
- 點擊位置一律用 uiautomator 現場找文字，不寫死座標——實機解析度、字級都和模擬器不同。
  只有 HUD 的播放鍵等用固定座標，因為 HUD 有持續動畫，uiautomator 會等很久才吐結果。
- 橫幅的時間軸是「錄的時候記下每一幕實際開始的秒數」，不是預先排好的。
  實機上每個動作花多久不固定，預排時間軸會讓字幕和畫面對不上。
- 分兩段錄：screenrecord 單檔上限 180 秒，而實機上每次 uiautomator 找字要 3～4 秒，整支流程會超過。
  第一段（編排課表）合成時加速播放，把找字的等待時間壓掉；
  第二段（上課）一定要原速，否則畫面上的課堂時鐘會跑得比真實時間快，看起來像壞掉。

事前條件（腳本不會幫你做，也做不到）：
- 平板已用 adb 連上（USB 或無線偵錯），Spotify 已登入。
- FitnessRider 已安裝，且「設定 → 串流音樂同步」已授權（通知存取權）。
- 平板橫放。媒體音量可以是 0，錄影不收音，成品另外配背景音樂。

用法：
    python3 tools/record_spotify_tutorial.py [--only part1|part2|compose] [adb 裝置序號]

--only part2 會沿用課表清單最上面那張課表，所以要先錄過 part1（或清單第一張就是要示範的串流課表）。
"""
import json
import os
import re
import subprocess
import sys
import time

from PIL import Image, ImageDraw, ImageFont

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR = os.path.join(PROJECT_DIR, "demo_output")
MUSIC_BED = os.path.join(PROJECT_DIR, "musics", "Blue Skies - Silent Partner.mp3")
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
ARGS = sys.argv[1:]
ONLY = ARGS[ARGS.index("--only") + 1] if "--only" in ARGS else None
POSITIONAL = [arg for index, arg in enumerate(ARGS) if arg != "--only" and (index == 0 or ARGS[index - 1] != "--only")]
DEVICE = POSITIONAL[0] if POSITIONAL else "192.168.0.245:41203"
FONT_PATH = "/System/Library/Fonts/STHeiti Medium.ttc"

APP = "com.fitnessrider.coach"
APP_ACTIVITY = f"{APP}/com.fitnessrider.MainActivity"
SPOTIFY = "com.spotify.music"
# 公開歌單（Today's Top Hits）。曲目會隨時間更換，所以下面用「清單第一首」而不是寫死歌名。
PLAYLIST_ID = "37i9dQZF1DXcBWIGoYBM5M"
PLAYLIST_URL = f"https://open.spotify.com/playlist/{PLAYLIST_ID}"
CLASS_TITLE = "Spotify Ride"  # adb input text 只能打 ASCII

RAW_ON_DEVICE = "/sdcard/fr_spotify_tutorial_raw.mp4"
MARKS_FILE = os.path.join(OUTPUT_DIR, "spotify_tutorial_marks.json")
FINAL = os.path.join(OUTPUT_DIR, "spotify_tutorial.mp4")
OUT_WIDTH = 1280
# 各段合成時的播放倍速。part2 不可以改，理由見檔頭。
PART_SPEED = {"part1": 1.6, "part2": 1.0}


def raw_path(part):
    return os.path.join(OUTPUT_DIR, f"spotify_tutorial_raw_{part}.mp4")

# HUD 上方控制列的固定座標（2000x1200 橫向）。HUD 不用 uiautomator，理由見檔頭。
HUD_PLAY_PAUSE = (1778, 80)
HUD_NEXT = (1940, 78)
HUD_OPEN_PLAYLIST = (1908, 180)
# Spotify 平板版左側是「正在播放」面板，同一個曲名會出現兩次；只點右側歌單裡的那一列，
# 點到左側那個會打開播放畫面而不是播歌。
SPOTIFY_LIST_MIN_X = 600

BANNERS = {
    "prep": ("【事前準備】設定 →「串流音樂同步」開啟通知存取權",
             "只需設定一次。開啟後，課堂畫面才能跟上 Spotify 實際播放的歌曲與進度"),
    "play": ("【步驟 1】在 Spotify 播放課程要用的第一首歌",
             "歌曲由 Spotify 播放，FitnessRider 負責計時、換段與動作提示"),
    "add": ("【步驟 2】新增課表 → 新增段落 →「串流音樂」→「帶入正在播放的歌」",
            "曲名與長度自動填入，再按「加入課表」。請用帶入的方式建立，曲名才對得上"),
    "more": ("【步驟 3】在 Spotify 切到下一首，重複同樣步驟加入其餘歌曲",
             "每首歌一個段落，之後可照常為每段編排姿勢、轉速與提示"),
    "link": ("【步驟 4】貼上 Spotify 歌單的分享連結，按「儲存」",
             "在 Spotify 歌單按「分享 → 複製連結」取得。上課時可一鍵開啟這張歌單"),
    "wait": ("【步驟 5】上課開騎：時鐘會等音樂開始才走",
             "畫面提示「請在串流 App 開始播放，或按播放鍵開始」"),
    "open": ("【步驟 6】按「開啟歌單」到 Spotify 播放，再回到 FitnessRider",
             "音樂一開始，課堂時鐘自動開始，並對齊 Spotify 的播放進度"),
    "ctrl": ("【課堂中】暫停、播放、上一首、下一首、±10 秒都會同步控制 Spotify",
             "教練只需要操作 FitnessRider，不必來回切換 App"),
    "follow": ("【課堂中】在 Spotify 換歌，課堂畫面自動跳到對應段落",
               "播到課表以外的歌會提示「不在課表中」，段落不會被帶走"),
}


def adb(*args, check=False):
    return subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True, text=True, check=check)


def sh(command):
    return adb("shell", command).stdout


def key(code, delay=0.6):
    sh(f"input keyevent {code}")
    time.sleep(delay)


def tap_xy(point, delay=1.0):
    sh(f"input tap {point[0]} {point[1]}")
    time.sleep(delay)


def ui_nodes():
    sh("uiautomator dump /sdcard/fr_ui.xml >/dev/null 2>&1")
    xml = adb("exec-out", "cat", "/sdcard/fr_ui.xml").stdout
    pattern = r'<node[^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    return [(t, d, (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
            for t, d, x1, y1, x2, y2 in re.findall(pattern, xml)]


def tap_text(label, nth=1, delay=1.2, required=True, min_x=0):
    seen = 0
    for text, desc, x, y in ui_nodes():
        if label in (text, desc) and x >= min_x:
            seen += 1
            if seen == nth:
                tap_xy((x, y), delay)
                return True
    if required:
        raise RuntimeError(f"畫面上找不到「{label}」")
    return False


def keyboard_shown():
    return "mInputShown=true" in sh("dumpsys input_method | grep mInputShown")


def focused_package():
    out = sh("dumpsys window | grep mCurrentFocus")
    match = re.search(r"u0 ([\w.]+)/", out)
    return match.group(1) if match else ""


def show_app(delay=2.5):
    sh(f"am start -n {APP_ACTIVITY} >/dev/null 2>&1")
    time.sleep(delay)


def show_spotify_playlist(delay=3.5):
    sh(f"am start -a android.intent.action.VIEW -d spotify:playlist:{PLAYLIST_ID} {SPOTIFY} >/dev/null 2>&1")
    time.sleep(delay)


def first_playlist_track():
    """歌單頁上第一首歌的曲名。Spotify 每一列歌曲都帶一顆「更多有關歌曲 X 的選項」按鈕。"""
    for _, desc, _, _ in ui_nodes():
        match = re.match(r"更多有關歌曲 (.+) 的選項", desc)
        if match:
            return match.group(1)
    raise RuntimeError("Spotify 歌單頁上找不到歌曲列，請確認已登入且歌單可開啟")


def spotify_state():
    out = sh("dumpsys media_session")
    block = out.split(f"package={SPOTIFY}", 1)
    if len(block) < 2:
        return None
    match = re.search(r"state=PlaybackState \{state=(\w+?)(?:\(\d+\))?,", block[1])
    return match.group(1) if match else None


def add_now_playing_segment():
    tap_text("新增段落", delay=1.8)
    tap_text("串流音樂", delay=1.5)
    tap_text("帶入正在播放的歌", delay=2.2)
    tap_text("加入課表", delay=2.0)


def make_banner(name, top, sub, width=1000, height=84):
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([(0, 0), (width, height)], radius=16, fill=(18, 22, 26, 235),
                           outline=(255, 255, 255, 65), width=1)
    draw.rounded_rectangle([(5, 5), (14, height - 5)], radius=4, fill=(132, 191, 9, 255))
    draw.text((28, 12), top, font=ImageFont.truetype(FONT_PATH, 24), fill=(255, 255, 255, 255))
    draw.text((28, 49), sub, font=ImageFont.truetype(FONT_PATH, 17), fill=(185, 225, 120, 255))
    path = os.path.join(OUTPUT_DIR, f"spotify_banner_{name}.png")
    image.save(path)
    return path


def record(part, body):
    """錄一段：body(scene) 負責操作，並在每一幕開始時呼叫 scene(幕名)。回傳這一段的時間軸。"""
    sh(f"rm -f {RAW_ON_DEVICE}")
    recorder = subprocess.Popen(
        [ADB, "-s", DEVICE, "shell", "screenrecord", "--bit-rate", "10000000", "--time-limit", "180", RAW_ON_DEVICE]
    )
    time.sleep(1.5)
    started = time.time()
    marks = []

    def scene(name):
        marks.append([name, round(time.time() - started, 2)])
        print(f"   [{marks[-1][1]:6.1f}s] {name}")

    try:
        body(scene)
    finally:
        duration = round(time.time() - started, 2)
        sh("pkill -INT screenrecord")
        recorder.wait(timeout=30)
        time.sleep(1.5)
    if duration > 178:
        raise RuntimeError(f"{part} 錄了 {duration:.0f} 秒，超過 screenrecord 的 180 秒上限，後段沒有錄到")
    adb("pull", RAW_ON_DEVICE, raw_path(part), check=True)
    sh(f"rm -f {RAW_ON_DEVICE}")
    return {"marks": marks, "duration": duration}


def part1(scene):
    scene("prep")
    tap_text("設定", delay=4.5)
    tap_text("返回", delay=1.5)

    scene("play")
    show_spotify_playlist()
    tap_text(first_playlist_track(), delay=4.0, min_x=SPOTIFY_LIST_MIN_X)

    scene("add")
    show_app()
    tap_text("新增課表", delay=2.0)
    tap_text("新飛輪課表", delay=1.0)
    key(123, 0.2)
    for _ in range(8):
        key(67, 0.05)
    sh(f"input text {CLASS_TITLE.replace(' ', '%s')}")
    time.sleep(1.0)
    # 鍵盤開著時返回鍵只會收鍵盤；沒開時會直接離開編輯器，所以一定要先確認。
    if keyboard_shown():
        key(4, 1.2)
    add_now_playing_segment()
    time.sleep(1.0)

    scene("more")
    for _ in range(2):
        key(87, 3.0)
        add_now_playing_segment()
    time.sleep(1.0)

    scene("link")
    tap_text("串流歌單連結", delay=1.5)
    tap_text("Spotify、KKBOX 的分享連結", delay=0.8)
    sh(f"input text {PLAYLIST_URL}")
    time.sleep(1.8)
    tap_text("儲存", delay=2.0)
    tap_text("儲存", delay=3.5)


def rewind_spotify_to_first_track():
    """回到歌單第一首並暫停，part2 一開始才看得到「時鐘等音樂開始」。"""
    show_spotify_playlist()
    tap_text(first_playlist_track(), delay=2.5, min_x=SPOTIFY_LIST_MIN_X)
    key(127, 1.5)
    show_app()


def part2(scene):
    scene("wait")
    tap_text("上課開騎", delay=10.0)

    scene("open")
    tap_xy(HUD_OPEN_PLAYLIST, 4.0)
    if focused_package() != SPOTIFY:
        raise RuntimeError("按「開啟歌單」後沒有直接進到 Spotify（可能跳出了選擇開啟工具）")
    tap_text(first_playlist_track(), delay=3.5, min_x=SPOTIFY_LIST_MIN_X)
    show_app(6.0)

    scene("ctrl")
    tap_xy(HUD_PLAY_PAUSE, 3.0)
    tap_xy(HUD_PLAY_PAUSE, 3.5)
    tap_xy(HUD_NEXT, 5.0)

    scene("follow")
    key(87, 5.0)
    key(87, 6.5)


def render_part(part, timeline):
    """單段：縮放、變速、疊上橫幅，輸出無聲的中間檔。橫幅時間要跟著倍速一起換算。"""
    speed = PART_SPEED[part]
    marks, duration = timeline["marks"], timeline["duration"]
    inputs = ["-t", f"{duration:.2f}", "-i", raw_path(part)]
    filters = [f"[0:v]scale={OUT_WIDTH}:-2,setpts=PTS/{speed},fps=30[v0]"]
    last = "v0"
    for index, (name, start) in enumerate(marks):
        end = marks[index + 1][1] if index + 1 < len(marks) else duration
        top, sub = BANNERS[name]
        inputs += ["-i", make_banner(name, top, sub)]
        filters.append(
            f"[{last}][{index + 1}:v]overlay=(W-w)/2:H-h-20:"
            f"enable='between(t,{start / speed:.2f},{end / speed:.2f})'[v{index + 1}]"
        )
        last = f"v{index + 1}"
    output = os.path.join(OUTPUT_DIR, f"spotify_tutorial_{part}.mp4")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", *inputs, "-filter_complex", ";".join(filters),
         "-map", f"[{last}]", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "21", output],
        check=True,
    )
    return output, duration / speed


def compose(timelines):
    rendered = [render_part(part, timelines[part]) for part in ("part1", "part2")]
    total = sum(length for _, length in rendered)
    fade_out = max(total - 2.5, 0)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error",
         "-i", rendered[0][0], "-i", rendered[1][0], "-stream_loop", "-1", "-i", MUSIC_BED,
         "-filter_complex",
         f"[0:v][1:v]concat=n=2:v=1:a=0[v];"
         f"[2:a]volume=0.18,afade=t=in:st=0:d=1.5,afade=t=out:st={fade_out:.2f}:d=2.5[a]",
         "-map", "[v]", "-map", "[a]", "-t", f"{total:.2f}",
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "21", "-c:a", "aac", "-b:a", "128k",
         "-movflags", "+faststart", FINAL],
        check=True,
    )
    return total


def load_timelines():
    if not os.path.exists(MARKS_FILE):
        return {}
    with open(MARKS_FILE, encoding="utf-8") as handle:
        return json.load(handle)


def save_timelines(timelines):
    with open(MARKS_FILE, "w", encoding="utf-8") as handle:
        json.dump(timelines, handle, ensure_ascii=False, indent=2)


def require_class_list():
    show_app()
    if focused_package() != APP:
        sys.exit("FitnessRider 沒有在前景，請先把它開到課表清單")
    if not any(text == "課表清單" for text, _, _, _ in ui_nodes()):
        sys.exit("FitnessRider 不在課表清單畫面，請先手動回到課表清單")
    tap_text("關閉提示", required=False)


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    timelines = load_timelines()
    if ONLY != "compose":
        if adb("get-state").stdout.strip() != "device":
            sys.exit(f"adb 連不到裝置 {DEVICE}")
        if ONLY in (None, "part1"):
            print("== part1：編排課表")
            key(127)
            require_class_list()
            timelines["part1"] = record("part1", part1)
            save_timelines(timelines)
        if ONLY in (None, "part2"):
            print("== part2：上課")
            rewind_spotify_to_first_track()
            require_class_list()
            try:
                timelines["part2"] = record("part2", part2)
            finally:
                key(127, 0.5)
            save_timelines(timelines)
            print("平板目前停在課堂畫面、Spotify 已暫停；請手動退出課堂。")
    if "part1" not in timelines or "part2" not in timelines:
        sys.exit("還缺一段沒錄，無法合成")
    total = compose(timelines)
    print(f"完成：{FINAL}（{total:.0f} 秒）")


if __name__ == "__main__":
    main()
