import Foundation

public enum PaywallPricing {
    public static let monthlyPriceTWD = 390
    public static let quarterlyPriceTWD = 890
    public static let yearlyPriceTWD = 2390
    public static let yearlySavingsTWD = monthlyPriceTWD * 12 - yearlyPriceTWD
    public static func formatTwd(_ amount: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        return "NT$" + (formatter.string(from: NSNumber(value: amount)) ?? String(amount))
    }

    public static let yearlyBadgeText = "🔥 飛輪教練首選・現省 \(formatTwd(yearlySavingsTWD))"
    public static let benefits = [
        "無限課表建立",
        "無損變速播放",
        "全功能 HUD",
        "課表備份匯出"
    ]
}
