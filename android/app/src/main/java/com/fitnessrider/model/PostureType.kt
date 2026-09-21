package com.fitnessrider.model

enum class PostureType(
    val rawValue: String,
    val localizedName: String,
    val defaultRpm: Int,
    val defaultHandPosition: HandPosition,
    val trainingGoalDescription: String
) {
    SEATED_FLAT(
        rawValue = "SEATED_FLAT",
        localizedName = "坐姿平路",
        defaultRpm = 85,
        defaultHandPosition = HandPosition.POSITION_1,
        trainingGoalDescription = "騎車的最基本姿勢，幫忙建立騎車的基本力量以及基本體能"
    ),
    STANDING_FLAT(
        rawValue = "STANDING_FLAT",
        localizedName = "站姿平路",
        defaultRpm = 80,
        defaultHandPosition = HandPosition.POSITION_2,
        trainingGoalDescription = "運用到更多核心肌群的穩定，增加騎車速度並鍛鍊耐力"
    ),
    SEATED_CLIMB(
        rawValue = "SEATED_CLIMB",
        localizedName = "坐姿爬坡",
        defaultRpm = 65,
        defaultHandPosition = HandPosition.POSITION_1,
        trainingGoalDescription = "以較高的阻力挑戰下半身，尤其是臀肌、腿後腱肌群的力量"
    ),
    STANDING_CLIMB(
        rawValue = "STANDING_CLIMB",
        localizedName = "站姿重爬坡",
        defaultRpm = 60,
        defaultHandPosition = HandPosition.POSITION_3,
        trainingGoalDescription = "站立姿勢來爬更重的坡，鍛鍊股四頭肌的力量與爬坡爆發力"
    ),
    JUMPS(
        rawValue = "JUMPS",
        localizedName = "抽車節奏跳躍",
        defaultRpm = 70,
        defaultHandPosition = HandPosition.POSITION_2,
        trainingGoalDescription = "藉由規律的坐姿與站姿抽車交替，強化核心肌群與動態心肺爆發力"
    ),
    SPRINT(
        rawValue = "SPRINT",
        localizedName = "極速全力衝刺",
        defaultRpm = 110,
        defaultHandPosition = HandPosition.POSITION_3,
        trainingGoalDescription = "在平路或微坡以最快踩踏極限衝刺，激發無氧耐力與乳酸耐受力"
    ),
    RECOVERY(
        rawValue = "RECOVERY",
        localizedName = "緩和放鬆",
        defaultRpm = 75,
        defaultHandPosition = HandPosition.POSITION_1,
        trainingGoalDescription = "以輕阻力舒緩踩踏，幫助心率回穩、排解肌肉乳酸並恢復體能"
    );

    companion object {
        fun fromRaw(raw: String): PostureType = entries.find { it.rawValue == raw } ?: SEATED_FLAT
    }
}
