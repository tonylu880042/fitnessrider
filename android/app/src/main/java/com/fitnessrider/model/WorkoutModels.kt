package com.fitnessrider.model

import java.util.UUID
import kotlin.math.roundToInt

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
    val playbackRate: Double = 1.0,
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

fun WorkoutClass.withRecalculatedTotals(): WorkoutClass {
    val totalMs = segments.sumOf { it.durationMs }
    var calories = 0.0
    for (seg in segments) {
        val minutes = seg.durationMs / 60_000.0
        val ratePerMin = when (seg.intensityZone) {
            1 -> 7.0
            2 -> 9.0
            3 -> 11.0
            4 -> 13.5
            5 -> 16.0
            else -> 10.0
        }
        calories += minutes * ratePerMin
    }
    val roundedCalories = (calories * 10.0).roundToInt() / 10.0
    return copy(totalDurationMs = totalMs, estimatedCalories = roundedCalories)
}
