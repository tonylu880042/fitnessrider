package com.fitnessrider.model

enum class HandPosition(
    val value: Int,
    val shortTitle: String,
    val localizedName: String,
    val gripDescription: String
) {
    POSITION_1(
        value = 1,
        shortTitle = "1 號位",
        localizedName = "1號位 (平把中段)",
        gripDescription = "雙手平放於中段近身把手，手肘微彎，專注核心穩定與基礎踩踏"
    ),
    POSITION_2(
        value = 2,
        shortTitle = "2 號位",
        localizedName = "2號位 (橫桿轉折)",
        gripDescription = "雙手握於把手兩側轉折處，支撐上半身重心，準備進行高轉速跑步抽車"
    ),
    POSITION_3(
        value = 3,
        shortTitle = "3 號位",
        localizedName = "3號位 (前端牛角)",
        gripDescription = "雙手握在把手最前端牛角突起處，利用槓桿原理發力，站立對抗重阻力"
    );

    companion object {
        fun fromValue(value: Int): HandPosition = entries.find { it.value == value } ?: POSITION_1
    }
}
