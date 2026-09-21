package com.fitnessrider.model

data class CoachingReminderCategory(
    val id: String,
    val title: String,
    val reminders: List<String>
)

object CoachingReminderLibrary {
    val generalMotion: List<String> = listOf(
        "踩動踏板",
        "眼光往前看",
        "身體放輕鬆",
        "肩膀放鬆",
        "速度慢慢加快",
        "用腳底板出力",
        "避免踮腳尖踩踏",
        "膝蓋和腳尖朝前",
        "手肘微彎",
        "保持身體穩定",
        "大腿出力",
        "不要駝背",
        "肚子用力收緊核心",
        "阻力要維持，速度要加快",
        "盡量跟上節拍",
        "開始初階的阻力訓練，阻力會慢慢加重",
        "阻力持續增加",
        "想像前方有一大片平原",
        "速度慢慢放緩",
        "快結束了，要進行緩和運動"
    )

    val generalBody: List<String> = listOf(
        "我們要讓身體熱起來",
        "身體要微微出汗",
        "慢慢站起來，要穩定身體",
        "加強一點速度與阻力，身體應該要出汗了",
        "坐回椅墊休息一下",
        "你會很喘，這是正常的",
        "累了可以放慢速度休息",
        "調整呼吸",
        "依自己的身體狀態來調整速度和阻力"
    )

    fun specificReminders(posture: PostureType): List<String> {
        return when (posture) {
            PostureType.SEATED_FLAT -> listOf(
                "培養基本的踩踏，建立基本體能",
                "開始暖身",
                "用一段平路來放鬆一下身體",
                "踏板持續踩，速度可放慢",
                "椅墊坐滿",
                "坐回椅墊",
                "擦汗喝水",
                "坐到椅墊上",
                "輕鬆踩踏"
            )
            PostureType.SEATED_CLIMB -> listOf(
                "挑戰下半身肌群，尤其是臀肌、腿後腱肌群的力量",
                "以較高的阻力來訓練下半身，尤其是臀肌、腿後腱肌群的力量",
                "開始爬斜坡",
                "有點陡的坡，大腿要記得用力",
                "這個訓練要持續加強阻力，模擬爬山的情境"
            )
            PostureType.STANDING_FLAT -> listOf(
                "站姿跑步可運用到更多的核心肌群",
                "此階段運動更多的核心肌群，可增加騎車的速度並鍛練耐力",
                "如果不行，可以坐著騎",
                "不要甩肩膀",
                "身體不要搖晃"
            )
            PostureType.STANDING_CLIMB -> listOf(
                "站立的姿勢來爬坡，鍛練股四頭肌的力量",
                "如果不行，可以坐著騎",
                "開始爬斜坡",
                "有點陡的坡，大腿要記得用力",
                "這個訓練要持續加強阻力，模擬爬山的情境",
                "現在試著踩踏",
                "讓身體往上伸直",
                "身體不要搖晃",
                "慢慢站起來"
            )
            PostureType.SPRINT -> listOf(
                "全力衝刺，跟上最快節奏！",
                "核心收緊，骨盆保持穩定",
                "注意呼吸，爆發踩踏！",
                "咬牙堅持最後幾秒鐘！"
            )
            PostureType.JUMPS -> listOf(
                "準備起身抽車，4拍坐下4拍站立",
                "流暢起伏，注意膝蓋軌跡",
                "核心收緊，帶動身體節奏"
            )
            PostureType.RECOVERY -> listOf(
                "深呼吸，心率慢慢降下來",
                "小口補充水分，肌肉放鬆",
                "輕鬆踩動踏板，不要驟停"
            )
        }
    }

    fun categories(posture: PostureType): List<CoachingReminderCategory> {
        return listOf(
            CoachingReminderCategory(
                id = "specific",
                title = "${posture.localizedName} 專屬指令",
                reminders = specificReminders(posture)
            ),
            CoachingReminderCategory(
                id = "general_motion",
                title = "踩踏動作技術",
                reminders = generalMotion
            ),
            CoachingReminderCategory(
                id = "general_body",
                title = "身體感受與體感",
                reminders = generalBody
            )
        )
    }
}
