import json
import zipfile
import os
import uuid

musics_dir = "/Users/tunghunglu/projects/fitnessrider/musics"
output_riderclass = "/Users/tunghunglu/projects/fitnessrider/demo_masterclass.riderclass"
class_id = "masterclass-10-tracks-demo"

segments_data = [
    {
        "index": 0,
        "title": "1. 暖身平路與基本體能 (Warm-Up)",
        "file": "Spring In My Step - Silent Partner.mp3",
        "durationMs": 118000,
        "bpm": 100.0,
        "zone": 1,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "SEATED_FLAT",
                "handPosition": 1,
                "targetRpm": 80,
                "resistanceLevel": "LEVEL 3",
                "message": "椅墊坐滿，肩膀放鬆，踏板順暢轉動",
                "reminders": ["培養基本的踩踏，建立基本體能", "眼光往前看", "身體放輕鬆"]
            },
            {
                "offsetMs": 60000,
                "posture": "STANDING_FLAT",
                "handPosition": 2,
                "targetRpm": 75,
                "resistanceLevel": "LEVEL 4",
                "message": "慢速站起，手肘微彎，穩定核心懸空",
                "reminders": ["站姿跑步可運用到更多的核心肌群", "不要甩肩膀", "身體不要搖晃"]
            }
        ]
    },
    {
        "index": 1,
        "title": "2. 提速踩踏與節奏跟進 (Cadence Build)",
        "file": "Hit the Switch - Silent Partner.mp3",
        "durationMs": 93000,
        "bpm": 115.0,
        "zone": 1,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "SEATED_FLAT",
                "handPosition": 1,
                "targetRpm": 90,
                "resistanceLevel": "LEVEL 4",
                "message": "坐回椅墊，跟隨放克節拍提速至 90 RPM",
                "reminders": ["速度慢慢加快", "盡量跟上節拍", "用腳底板出力"]
            }
        ]
    },
    {
        "index": 2,
        "title": "3. 站姿跑步核心穩定 (Standing Rhythm)",
        "file": "Marvin's Dance - Silent Partner.mp3",
        "durationMs": 94000,
        "bpm": 124.0,
        "zone": 2,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "STANDING_FLAT",
                "handPosition": 2,
                "targetRpm": 85,
                "resistanceLevel": "LEVEL 4",
                "message": "握把 2 號位，保持輕快跑步節奏",
                "reminders": ["此階段運動更多的核心肌群，可增加騎車的速度並鍛練耐力", "膝蓋和腳尖朝前"]
            }
        ]
    },
    {
        "index": 3,
        "title": "4. 坐姿重阻力爬坡 (Seated Heavy Climb)",
        "file": "Take That Back - Silent Partner.mp3",
        "durationMs": 159000,
        "bpm": 95.0,
        "zone": 2,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "SEATED_CLIMB",
                "handPosition": 1,
                "targetRpm": 65,
                "resistanceLevel": "LEVEL 6",
                "message": "右轉阻力鈕，深層挑戰臀肌與大腿後側",
                "reminders": ["挑戰下半身肌群，尤其是臀肌、腿後腱肌群的力量", "大腿出力", "不要駝背"]
            }
        ]
    },
    {
        "index": 4,
        "title": "5. 站姿重爬坡抽車 (Standing Mountain Climb)",
        "file": "Alternate - Vibe Tracks.mp3",
        "durationMs": 172000,
        "bpm": 128.0,
        "zone": 2,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "STANDING_CLIMB",
                "handPosition": 3,
                "targetRpm": 60,
                "resistanceLevel": "LEVEL 7",
                "message": "雙手握前端牛角 3 號位，利用槓桿爬陡坡",
                "reminders": ["站立的姿勢來爬坡，鍛練股四頭肌的力量", "讓身體往上伸直", "身體不要搖晃"]
            }
        ]
    },
    {
        "index": 5,
        "title": "6. 4拍跳躍動態抽車 (Jumps & Dynamics)",
        "file": "The Itch - NEFFEX.mp3",
        "durationMs": 166000,
        "bpm": 130.0,
        "zone": 3,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "JUMPS",
                "handPosition": 2,
                "targetRpm": 75,
                "resistanceLevel": "LEVEL 5",
                "message": "準備起身抽車，4 拍坐下、4 拍站立",
                "reminders": ["準備起身抽車，4拍坐下4拍站立", "流暢起伏，注意膝蓋軌跡", "核心收緊，帶動身體節奏"]
            }
        ]
    },
    {
        "index": 6,
        "title": "7. 高踏頻巡航耐力 (High Cadence Cruise)",
        "file": "Level Up - Quincas Moreira.mp3",
        "durationMs": 190000,
        "bpm": 125.0,
        "zone": 2,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "SEATED_FLAT",
                "handPosition": 1,
                "targetRpm": 95,
                "resistanceLevel": "LEVEL 4",
                "message": "想像面前有一大片平原，維持高速均速",
                "reminders": ["想像前方有一大片平原", "速度要維持，阻力不能掉", "調整呼吸"]
            }
        ]
    },
    {
        "index": 7,
        "title": "8. 極限大阻力對抗 (Power Hill Battle)",
        "file": "Gas Pedal - Diamond Ortiz.mp3",
        "durationMs": 206000,
        "bpm": 105.0,
        "zone": 3,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "STANDING_CLIMB",
                "handPosition": 3,
                "targetRpm": 60,
                "resistanceLevel": "LEVEL 8",
                "message": "阻力加重至 LEVEL 8，身體向上抗阻",
                "reminders": ["這個訓練要持續加強阻力，模擬爬山的情境", "肚子用力收緊核心"]
            }
        ]
    },
    {
        "index": 8,
        "title": "9. 無氧極速全力衝刺 (All-Out Sprint)",
        "file": "You Will Never See Me Coming - NEFFEX.mp3",
        "durationMs": 132000,
        "bpm": 140.0,
        "zone": 3,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "SPRINT",
                "handPosition": 3,
                "targetRpm": 110,
                "resistanceLevel": "LEVEL 5",
                "message": "最後一戰！全力衝刺，跟上極限節奏！",
                "reminders": ["全力衝刺，跟上最快節奏！", "注意呼吸，爆發踩踏！", "咬牙堅持最後幾秒鐘！"]
            }
        ]
    },
    {
        "index": 9,
        "title": "10. 緩和放鬆與心率回穩 (Cool-Down & Recovery)",
        "file": "Blue Skies - Silent Partner.mp3",
        "durationMs": 163000,
        "bpm": 90.0,
        "zone": 1,
        "cues": [
            {
                "offsetMs": 0,
                "posture": "RECOVERY",
                "handPosition": 1,
                "targetRpm": 70,
                "resistanceLevel": "LEVEL 2",
                "message": "阻力降到最輕，深呼吸，慢踩排乳酸",
                "reminders": ["深呼吸，心率慢慢降下來", "小口補充水分，肌肉放鬆", "快結束了，要進行緩和運動"]
            }
        ]
    }
]

total_duration_ms = sum(s["durationMs"] for s in segments_data)

formatted_segments = []
for s in segments_data:
    seg_id = f"seg-{s['index']+1}"
    cues = []
    for c_idx, c in enumerate(s["cues"]):
        cue_id = f"cue-{s['index']+1}-{c_idx+1}"
        cues.append({
            "id": cue_id,
            "segmentId": seg_id,
            "offsetMs": c["offsetMs"],
            "posture": c["posture"],
            "targetRpm": c["targetRpm"],
            "resistanceLevel": c["resistanceLevel"],
            "message": c["message"],
            "handPosition": c["handPosition"],
            "reminders": c["reminders"]
        })
    formatted_segments.append({
        "id": seg_id,
        "classId": class_id,
        "orderIndex": s["index"],
        "title": s["title"],
        "musicFileName": s["file"],
        "durationMs": s["durationMs"],
        "baseBpm": s["bpm"],
        "playbackRate": 1.0,
        "intensityZone": s["zone"],
        "cues": cues
    })

workout_class = {
    "id": class_id,
    "title": "45min 頂級教練燃脂間歇示範課 (All-Star Masterclass)",
    "author": "Tony Coach",
    "createdAt": "2026-09-21T14:00:00Z",
    "totalDurationMs": total_duration_ms,
    "estimatedCalories": 480.0,
    "segments": formatted_segments
}

# Create .riderclass zip archive
with zipfile.ZipFile(output_riderclass, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("workout_class.json", json.dumps(workout_class, ensure_ascii=False, indent=2))
    for s in segments_data:
        file_path = os.path.join(musics_dir, s["file"])
        if os.path.exists(file_path):
            zf.write(file_path, arcname=s["file"])

print(f"Generated {output_riderclass} successfully!")
print(f"Total Segments: {len(formatted_segments)}")
print(f"Total Duration: {total_duration_ms/1000}s ({total_duration_ms/60000:.1f} mins)")
