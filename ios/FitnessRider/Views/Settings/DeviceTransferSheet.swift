import SwiftUI

public struct DeviceTransferSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var licenseService = LicenseVerificationService.shared

    @State private var transferMode: Int = 0 // 0: VIP 序號, 1: 會員帳密
    @State private var licenseCodeInput: String = ""
    @State private var emailInput: String = ""
    @State private var passwordInput: String = ""

    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var remainingCooldownDays: Int? = nil
    @State private var successMessage: String? = nil

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Header Policy Banner
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "shield.lefthalf.filled")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            Text("單一設備綁定與防共用政策")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        }

                        Text("• FitnessRider 授權限定單一設備使用。\n• 當您換新平板時，可將既有授權遷入本設備，舊設備將自動停用。\n• 為防止授權共用作弊，每 30 天內最多僅允許轉移一次設備。")
                            .font(.system(size: 12))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                            .lineSpacing(4)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(FitnessRiderTheme.topBarGreen.opacity(0.08))
                    .cornerRadius(12)

                    // Transfer Mode Picker
                    Picker("轉移方式", selection: $transferMode) {
                        Text("VIP 序號轉移").tag(0)
                        Text("會員帳密轉移").tag(1)
                    }
                    .pickerStyle(.segmented)

                    // Input Form Fields
                    VStack(spacing: 14) {
                        if transferMode == 0 {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("原 VIP 授權序號")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(FitnessRiderTheme.textPrimary)

                                TextField("例如: RIDER-VIP-2026-PASS", text: $licenseCodeInput)
                                    .textFieldStyle(.roundedBorder)
                                    .autocapitalization(.allCharacters)
                                    .disableAutocorrection(true)
                                    .font(.system(.body, design: .monospaced))

                                Text("請輸入舊設備原本開通使用中的 VIP 授權碼。")
                                    .font(.caption2)
                                    .foregroundColor(FitnessRiderTheme.textSecondary)
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("會員 Email 帳號")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(FitnessRiderTheme.textPrimary)

                                TextField("coach@fitnessrider.app", text: $emailInput)
                                    .textFieldStyle(.roundedBorder)
                                    .keyboardType(.emailAddress)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                            }

                            VStack(alignment: .leading, spacing: 6) {
                                Text("會員密碼")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(FitnessRiderTheme.textPrimary)

                                SecureField("請輸入密碼", text: $passwordInput)
                                    .textFieldStyle(.roundedBorder)
                            }
                        }
                    }
                    .padding(16)
                    .background(FitnessRiderTheme.cardHeaderBackground)
                    .cornerRadius(12)

                    // Current Device Fingerprint Box
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("目前本機識別碼 (新設備)")
                                .font(.system(size: 11))
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                            Spacer()
                            Text(DeviceIdentifierService.shared.deviceModel)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        }

                        Text(DeviceIdentifierService.shared.deviceFingerprint)
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                            .lineLimit(1)
                    }
                    .padding(12)
                    .background(Color.black.opacity(0.03))
                    .cornerRadius(8)

                    // Cooldown & Error Banner
                    if let cooldown = remainingCooldownDays {
                        VStack(spacing: 6) {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(FitnessRiderTheme.accentOrange)
                                Text("換機次數受限 (30 天冷卻保護)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(FitnessRiderTheme.accentOrange)
                            }

                            Text("距離下次可更換設備尚有 \(cooldown) 天。\n如為硬體損壞或特殊教學突發狀況，請聯繫官方客服專案處理。")
                                .font(.system(size: 12))
                                .foregroundColor(FitnessRiderTheme.textPrimary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity)
                        .background(FitnessRiderTheme.accentOrange.opacity(0.12))
                        .cornerRadius(10)
                    } else if let error = errorMessage {
                        HStack(spacing: 6) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(FitnessRiderTheme.accentRed)
                            Text(error)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(FitnessRiderTheme.accentRed)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity)
                        .background(FitnessRiderTheme.accentRed.opacity(0.1))
                        .cornerRadius(8)
                    }

                    // Success Banner
                    if let success = successMessage {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            Text(success)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity)
                        .background(FitnessRiderTheme.topBarGreen.opacity(0.15))
                        .cornerRadius(10)
                    }

                    // Confirm Action Button
                    Button {
                        executeTransfer()
                    } label: {
                        HStack(spacing: 8) {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.system(size: 16, weight: .bold))
                                Text("確認轉移授權至本設備")
                                    .font(.system(size: 15, weight: .bold))
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(isLoading ? Color.gray : FitnessRiderTheme.topBarGreenDark)
                        .cornerRadius(12)
                    }
                    .disabled(isLoading)
                }
                .padding(20)
            }
            .navigationTitle("跨設備授權轉移")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }
        }
    }

    private func executeTransfer() {
        errorMessage = nil
        remainingCooldownDays = nil
        successMessage = nil
        isLoading = true

        Task {
            let result: DeviceTransferResult
            if transferMode == 0 {
                let code = licenseCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !code.isEmpty else {
                    await MainActor.run {
                        isLoading = false
                        errorMessage = "請輸入原 VIP 授權序號"
                    }
                    return
                }
                result = await licenseService.transferDeviceWithLicenseCode(code: code)
            } else {
                let email = emailInput.trimmingCharacters(in: .whitespacesAndNewlines)
                let pass = passwordInput
                guard !email.isEmpty && !pass.isEmpty else {
                    await MainActor.run {
                        isLoading = false
                        errorMessage = "請輸入完整帳號與密碼"
                    }
                    return
                }
                result = await licenseService.transferDeviceWithAccount(email: email, password: pass)
            }

            await MainActor.run {
                isLoading = false
                if result.success {
                    successMessage = result.message
                    licenseService.refreshLicenseState()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                        dismiss()
                    }
                } else {
                    if let cooldown = result.remainingCooldownDays {
                        remainingCooldownDays = cooldown
                    } else {
                        errorMessage = result.message
                    }
                }
            }
        }
    }
}
