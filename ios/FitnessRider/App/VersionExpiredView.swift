import SwiftUI
import UIKit

public struct VersionExpiredView: View {
    @ObservedObject private var manager = VersionLifecycleManager.shared
    public var onUpdate: (() -> Void)? = nil

    public init(onUpdate: (() -> Void)? = nil) {
        self.onUpdate = onUpdate
    }

    public var body: some View {
        ZStack {
            FitnessRiderTheme.canvasWhite
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Warning Icon
                ZStack {
                    Circle()
                        .fill(FitnessRiderTheme.accentRed.opacity(0.12))
                        .frame(width: 80, height: 80)
                    Image(systemName: "clock.badge.exclamationmark")
                        .font(.system(size: 38, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.accentRed)
                }

                Text("版本已過期")
                    .font(.system(size: 26, weight: .black))
                    .foregroundColor(FitnessRiderTheme.textPrimary)

                Text("為確保課堂中控穩定度、最新音樂分析演算法與各項功能體驗，每個發行版本的有效使用期限固定為 30 天。\n\n此版本已超過使用期限，請更新至最新版本後繼續使用。")
                    .font(.system(size: 14))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.horizontal, 24)

                Divider()
                    .padding(.horizontal, 24)

                // Version details box
                VStack(spacing: 8) {
                    HStack {
                        Text("目前版本")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(manager.appVersionString)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }

                    HStack {
                        Text("建置時間")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(manager.buildDateFormatted)
                            .font(.system(size: 13, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }

                    HStack {
                        Text("到期日期")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(manager.expirationDateFormatted)
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.accentRed)
                    }
                }
                .padding(16)
                .background(FitnessRiderTheme.cardHeaderBackground)
                .cornerRadius(12)
                .padding(.horizontal, 24)

                Spacer().frame(height: 10)

                // Primary Action Button
                Button {
                    if let customAction = onUpdate {
                        customAction()
                    } else {
                        UIApplication.shared.open(VersionLifecycleManager.updateURL)
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 18, weight: .bold))
                        Text("前往更新最新版本")
                            .font(.system(size: 16, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(FitnessRiderTheme.topBarGreen)
                    .cornerRadius(12)
                    .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 2)
                }
                .padding(.horizontal, 24)
            }
            .padding(.vertical, 32)
            .frame(maxWidth: 460)
            .background(Color.white)
            .cornerRadius(24)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 8)
            .padding(24)
        }
    }
}
