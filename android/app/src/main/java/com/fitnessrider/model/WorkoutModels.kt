package com.fitnessrider.model

import java.util.UUID

data class WorkoutCue(
    val id: String = UUID.randomUUID().toString(),
    val segmentId: String = "",
    val offsetMs: Int = 0,
    val posture: PostureType = PostureType.SEATED_FLAT,
    val targetRpm: Int = 85,
    val resistanceLevel: String = "LEVEL 5",
    val message: String = "",
    val handPosition: HandPosition = posture.defaultHandPosition,
    val reminders: List<String> = emptyList()
)

data class WorkoutSegment(
    val id: String = UUID.randomUUID().toString(),
    val classId: String = "",
    val orderIndex: Int = 0,
    val title: String = "未命名段落",
    val musicFileName: String = "",
    val durationMs: Int = 300_000,
    val baseBpm: Double = 128.0,
    val playbackRate: Double = 1.0, // 0.85 ~ 1.15
    val intensityZone: Int = 2,
    val cues: List<WorkoutCue> = emptyList()
) {
    val effectiveBpm: Double get() = baseBpm * playbackRate
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
}

data class WorkoutClass(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "全新飛輪課表",
    val author: String = "教練",
    val createdAt: String = "",
    val totalDurationMs: Int = 0,
    val estimatedCalories: Double = 0.0,
    val segments: List<WorkoutSegment> = emptyList()
) {
    val formattedDuration: String
        get() {
            val totalSec = totalDurationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
}
