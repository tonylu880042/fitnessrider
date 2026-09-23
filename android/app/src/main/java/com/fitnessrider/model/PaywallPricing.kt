package com.fitnessrider.model

object PaywallPricing {
    const val MONTHLY_PRICE_TWD = 390
    const val QUARTERLY_PRICE_TWD = 890
    const val YEARLY_PRICE_TWD = 2390
    const val YEARLY_SAVINGS_TWD = MONTHLY_PRICE_TWD * 12 - YEARLY_PRICE_TWD
    fun formatTwd(amount: Int): String = "NT$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(amount)

    val YEARLY_BADGE_TEXT = "🔥 飛輪教練首選・現省 ${formatTwd(YEARLY_SAVINGS_TWD)}"
    val BENEFITS = listOf(
        "無限課表建立",
        "無損變速播放",
        "全功能 HUD",
        "課表備份匯出"
    )
}
