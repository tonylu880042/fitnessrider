import SwiftUI

public struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    public var onActivateTapped: () -> Void

    public init(onActivateTapped: @escaping () -> Void) {
        self.onActivateTapped = onActivateTapped
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 8) {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 40))
                            .foregroundColor(FitnessRiderTheme.topBarGreen)
                        Text("解鎖 FitnessRider 專業教練工具")
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 8)

                    PricingPlanCard(planName: "月繳", priceTWD: PaywallPricing.monthlyPriceTWD, periodLabel: "/ 月")
                    PricingPlanCard(planName: "季繳", priceTWD: PaywallPricing.quarterlyPriceTWD, periodLabel: "/ 季")
                    PricingPlanCard(
                        planName: "年繳",
                        priceTWD: PaywallPricing.yearlyPriceTWD,
                        periodLabel: "/ 年",
                        badgeText: PaywallPricing.yearlyBadgeText,
                        highlighted: true
                    )

                    VStack(alignment: .leading, spacing: 8) {
                        Text("VIP 專屬權益")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                        ForEach(PaywallPricing.benefits, id: \.self) { benefit in
                            HStack(spacing: 8) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                                Text(benefit)
                                    .font(.system(size: 13))
                                    .foregroundColor(FitnessRiderTheme.textPrimary)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(FitnessRiderTheme.cardHeaderBackground)
                    .cornerRadius(12)

                    Button {
                        dismiss()
                        onActivateTapped()
                    } label: {
                        Text("🔑 輸入授權序號開通")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(FitnessRiderTheme.topBarGreen)
                            .cornerRadius(12)
                    }

                    Text("商店訂閱功能尚未開放，請先透過授權序號開通 VIP；未來商店上架後可直接於此完成訂閱。")
                        .font(.system(size: 11))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
            }
            .navigationTitle("VIP 專業方案")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉") { dismiss() }
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }
        }
    }
}

private struct PricingPlanCard: View {
    let planName: String
    let priceTWD: Int
    let periodLabel: String
    var badgeText: String? = nil
    var highlighted: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let badgeText {
                Text(badgeText)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
            }
            HStack(alignment: .firstTextBaseline) {
                Text(planName)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                Spacer()
                Text(PaywallPricing.formatTwd(priceTWD))
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(FitnessRiderTheme.textPrimary)
                Text(" \(periodLabel)")
                    .font(.system(size: 13))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(highlighted ? FitnessRiderTheme.topBarGreen.opacity(0.08) : FitnessRiderTheme.cardBackground)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(highlighted ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder, lineWidth: highlighted ? 2 : 1)
        )
    }
}
