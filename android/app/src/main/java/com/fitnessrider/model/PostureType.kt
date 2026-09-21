package com.fitnessrider.model

enum class PostureType(val rawValue: String, val localizedName: String, val defaultRpm: Int) {
    SEATED_FLAT("SEATED_FLAT", "坐姿平路", 85),
    STANDING_FLAT("STANDING_FLAT", "站姿平路", 80),
    SEATED_CLIMB("SEATED_CLIMB", "坐姿爬坡", 65),
    STANDING_CLIMB("STANDING_CLIMB", "站姿重爬坡", 60),
    JUMPS("JUMPS", "抽車節奏跳躍", 70),
    SPRINT("SPRINT", "極速全力衝刺", 110),
    RECOVERY("RECOVERY", "緩和放鬆", 75);

    companion object {
        fun fromRaw(raw: String): PostureType = entries.find { it.rawValue == raw } ?: SEATED_FLAT
    }
}
