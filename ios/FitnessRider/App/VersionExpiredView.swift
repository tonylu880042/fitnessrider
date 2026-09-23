import SwiftUI
import UIKit

public enum ExpirationReason {
    case trialEnded
    case mustUpdate
}

public struct VersionExpiredView: View {
    @ObservedObject private var manager = VersionLifecycleManager.shared
    public var reason: ExpirationReason
    public var onUpdate: (() -> Void)? = nil

    @State private var isShowingActivationAlert: Bool = false
    @State private var isShowingDeviceTransferSheet: Bool = false
    @State private var isShowingPaywallSheet: Bool = false
    @State private var enteredLicenseCode: String = ""
    @State private var statusAlertMessage: String = ""
    @State private var isShowingStatusAlert: Bool = false
    @State private var isCopiedDeviceId: Bool = false

    public init(reason: ExpirationReason = .trialEnded, onUpdate: (() -> Void)? = nil) {
        self.reason = reason
        self.onUpdate = onUpdate
    }

    public var body: some View {
        ZStack {
            FitnessRiderTheme.canvasWhite
                .ignoresSafeArea()

            VStack(spacing: 18) {
                ZStack {
                    Circle()
                        .fill(FitnessRiderTheme.accentRed.opacity(0.12))
                        .frame(width: 76, height: 76)
                    Image(systemName: "hourglass.bottomhalf.filled")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.accentRed)
                }

                Text(reason == .mustUpdate ? "需要更新版本" : "免費試用已結束")
                    .font(.system(size: 24, weight: .black))
                    .foregroundColor(FitnessRiderTheme.textPrimary)

                Text(reason == .mustUpdate
                     ? "偵測到有新版本可用，這個版本已不再支援使用。\n\n請更新至最新版本後繼續使用；您的授權與課表資料都不會受影響。"
                     : "感謝體驗 FitnessRider！您的免費試用期已結束。\n\n如需繼續在課堂中使用專業中控、無損變速與震動回饋，請輸入授權碼開通 VIP，或更新至最新版本。")
                    .font(.system(size: 13))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.horizontal, 20)

                Divider()
                    .padding(.horizontal, 20)

                VStack(spacing: 8) {
                    HStack {
                        Text("授權狀態")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(reason == .mustUpdate ? "版本已不支援" : "試用期滿")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.accentRed)
                    }

                    HStack {
                        Text("試用起算日")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(manager.trialStartDateFormatted)
                            .font(.system(size: 13, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }

                    HStack {
                        Text("目前版本")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Spacer()
                        Text(manager.appVersionString)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }

                    Divider().padding(.vertical, 2)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("設備識別碼 (Device ID)")
                                .font(.system(size: 11))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                            Spacer()
                            Button {
                                UIPasteboard.general.string = DeviceIdentifierService.shared.deviceFingerprint
                                isCopiedDeviceId = true
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                    isCopiedDeviceId = false
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: isCopiedDeviceId ? "checkmark.circle.fill" : "doc.on.doc")
                                        .font(.system(size: 11))
                                    Text(isCopiedDeviceId ? "已複製" : "複製")
                                        .font(.system(size: 11, weight: .bold))
                                }
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            }
                        }

                        Text(DeviceIdentifierService.shared.deviceFingerprint)
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                .padding(14)
                .background(FitnessRiderTheme.cardHeaderBackground)
                .cornerRadius(12)
                .padding(.horizontal, 20)

                VStack(spacing: 10) {
                    if reason == .trialEnded {
                        Button {
                            isShowingActivationAlert = true
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "key.fill")
                                    .font(.system(size: 16, weight: .bold))
                                Text("輸入授權碼開通 VIP")
                                    .font(.system(size: 15, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(FitnessRiderTheme.topBarGreen)
                            .cornerRadius(12)
                            .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 2)
                        }

                        Button {
                            isShowingPaywallSheet = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "creditcard.fill")
                                    .font(.system(size: 14))
                                Text("查看 VIP 付費方案與價格")
                                    .font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                        }

                        Button {
                            isShowingDeviceTransferSheet = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.system(size: 14))
                                Text("舊機換新機？轉移既有授權")
                                    .font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .frame(maxWidth: .infinity)
                            .frame(height: 36)
                        }
                    }

                    Button {
                        if let customAction = onUpdate {
                            customAction()
                        } else {
                            UIApplication.shared.open(VersionLifecycleManager.updateURL)
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: reason == .mustUpdate ? 16 : 15, weight: reason == .mustUpdate ? .bold : .medium))
                            Text("前往更新最新版本")
                                .font(.system(size: reason == .mustUpdate ? 15 : 14, weight: reason == .mustUpdate ? .bold : .medium))
                        }
                        .foregroundColor(reason == .mustUpdate ? .white : FitnessRiderTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: reason == .mustUpdate ? 48 : 36)
                        .background(reason == .mustUpdate ? FitnessRiderTheme.topBarGreen : Color.clear)
                        .cornerRadius(12)
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.vertical, 28)
            .frame(maxWidth: 460)
            .background(Color.white)
            .cornerRadius(24)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 16, x: 0, y: 8)
            .padding(20)
        }
        .sheet(isPresented: $isShowingDeviceTransferSheet) {
            DeviceTransferSheet()
        }
        .sheet(isPresented: $isShowingPaywallSheet) {
            PaywallView {
                isShowingActivationAlert = true
            }
        }
        .alert("輸入授權序號或推廣代碼", isPresented: $isShowingActivationAlert) {
            TextField("如: 26FR-NR 或 FRVIP-...", text: $enteredLicenseCode)
                .textInputAutocapitalization(.characters)
            Button("開通 / 兌換") {
                let code = enteredLicenseCode
                Task {
                    let res = await LicenseVerificationService.shared.activateCode(code: code)
                    statusAlertMessage = res.1
                    isShowingStatusAlert = true
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("輸入推廣培訓專屬代碼（如 26FR-NR）享 30 天免費體驗，或輸入 VIP 授權序號：")
        }
        .alert("系統通知", isPresented: $isShowingStatusAlert) {
            Button("確定", role: .cancel) {}
        } message: {
            Text(statusAlertMessage)
        }
    }
}
