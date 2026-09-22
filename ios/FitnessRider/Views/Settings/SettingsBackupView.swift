import SwiftUI
import UniformTypeIdentifiers

public struct SettingsBackupView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var licenseService = LicenseVerificationService.shared

    public let onDataChanged: () -> Void

    @State private var shareURL: URL?
    @State private var isShowingShareSheet: Bool = false
    @State private var isShowingRestorePicker: Bool = false
    @State private var isShowingRiderClassPicker: Bool = false
    @State private var alertMessage: String = ""
    @State private var isShowingAlert: Bool = false
    @State private var isShowingActivationAlert: Bool = false
    @State private var isShowingDeviceTransferSheet: Bool = false
    @State private var licenseCodeInput: String = ""
    @State private var isCopiedDeviceId: Bool = false

    public init(onDataChanged: @escaping () -> Void) {
        self.onDataChanged = onDataChanged
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section("課堂與音訊體驗") {
                    Toggle("動作切換 3-2-1 倒數提示音", isOn: $settings.isCountdownBeepEnabled)

                    Toggle("動作切換車把觸覺震動回饋", isOn: $settings.isHapticFeedbackEnabled)

                    Toggle("曲目段落結束自動暫停", isOn: $settings.isAutoPauseBetweenSegmentsEnabled)

                    VStack(alignment: .leading, spacing: 4) {
                        Picker("曲目平滑切換 (Crossfade)", selection: $settings.crossfadeDurationSeconds) {
                            ForEach(AppSettings.crossfadeOptionsSeconds, id: \.self) { seconds in
                                Text(Self.crossfadeOptionLabel(seconds)).tag(seconds)
                            }
                        }
                        .disabled(settings.isAutoPauseBetweenSegmentsEnabled)

                        if settings.isAutoPauseBetweenSegmentsEnabled {
                            Text("啟用「段落結束自動暫停」時，將自動停用 Crossfade")
                                .font(.caption2)
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                        }
                    }

                    Toggle("課堂進行中螢幕強制常亮", isOn: $settings.keepScreenAwakeInHUD)
                }

                Section("資料庫備份與還原 (SQLite)") {
                    Button {
                        exportDatabaseBackup()
                    } label: {
                        Label("備份整個資料庫 (.sqlite)", systemImage: "arrow.down.doc.fill")
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }

                    Button {
                        isShowingRestorePicker = true
                    } label: {
                        Label("還原資料庫 (.sqlite)", systemImage: "arrow.counterclockwise.circle.fill")
                            .foregroundColor(FitnessRiderTheme.accentOrange)
                    }

                    Button {
                        isShowingRiderClassPicker = true
                    } label: {
                        Label("匯入完整課表包 (.riderclass)", systemImage: "shippingbox.fill")
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }
                }

                Section("授權狀態與單機綁定") {
                    HStack {
                        Text("授權方案")
                        Spacer()
                        Text(licenseService.planType)
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .fontWeight(.bold)
                    }

                    HStack {
                        Text("授權狀態")
                        Spacer()
                        Text(licenseService.isLicensed ? "有效 (剩餘 \(licenseService.remainingDays) 天)" : "試用已結束")
                            .foregroundColor(licenseService.isLicensed ? FitnessRiderTheme.topBarGreenDark : FitnessRiderTheme.accentRed)
                    }

                    Button {
                        isShowingActivationAlert = true
                    } label: {
                        Label("輸入授權碼開通 / 啟用 VIP", systemImage: "key.fill")
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .fontWeight(.semibold)
                    }

                    Button {
                        isShowingDeviceTransferSheet = true
                    } label: {
                        Label("轉移設備授權 (換新機)", systemImage: "arrow.triangle.2.circlepath")
                            .foregroundColor(FitnessRiderTheme.accentOrange)
                            .fontWeight(.semibold)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("設備唯一識別碼 (Device ID)")
                                .font(.caption)
                                .foregroundColor(FitnessRiderTheme.textSecondary)
                            Spacer()
                            Button {
                                UIPasteboard.general.string = DeviceIdentifierService.shared.deviceFingerprint
                                isCopiedDeviceId = true
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                    isCopiedDeviceId = false
                                }
                            } label: {
                                Text(isCopiedDeviceId ? "已複製" : "點擊複製")
                                    .font(.caption2)
                                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            }
                        }

                        Text(DeviceIdentifierService.shared.deviceFingerprint)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }
                }

                Section("本機音樂庫目錄") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("支援透過 Mac Finder / iTunes 檔案共享直接將 MP3/M4A 拖曳至以下專屬資料夾：")
                            .font(.caption)
                            .foregroundColor(FitnessRiderTheme.textSecondary)

                        Text(SQLiteDatabase.shared.musicDirectoryURL.path)
                            .font(.system(.caption2, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }
                }
            }
            .navigationTitle("系統設定與備份")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        dismiss()
                    }
                    .fontWeight(.bold)
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }
            .sheet(isPresented: $isShowingShareSheet) {
                if let url = shareURL {
                    ShareActivityView(activityItems: [url])
                }
            }
            .sheet(isPresented: $isShowingDeviceTransferSheet) {
                DeviceTransferSheet()
            }
            .fileImporter(
                isPresented: $isShowingRestorePicker,
                allowedContentTypes: [.data],
                allowsMultipleSelection: false
            ) { result in
                handleRestoreSQLite(result)
            }
            .fileImporter(
                isPresented: $isShowingRiderClassPicker,
                allowedContentTypes: [UTType(filenameExtension: "riderclass") ?? .data, .data],
                allowsMultipleSelection: false
            ) { result in
                handleImportRiderClass(result)
            }
            .alert("系統通知", isPresented: $isShowingAlert) {
                Button("確定", role: .cancel) {}
            } message: {
                Text(alertMessage)
            }
            .alert("輸入授權序號或推廣代碼", isPresented: $isShowingActivationAlert) {
                TextField("如: 26FR-NR 或 FRVIP-...", text: $licenseCodeInput)
                    .textInputAutocapitalization(.characters)
                Button("開通 / 兌換") {
                    let code = licenseCodeInput
                    Task {
                        let res = await licenseService.activateCode(code: code)
                        alertMessage = res.1
                        isShowingAlert = true
                    }
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text("輸入推廣培訓專屬代碼（如 26FR-NR）享 30 天免費體驗，或輸入 VIP 授權序號：")
            }
        }
    }

    private static func crossfadeOptionLabel(_ seconds: Double) -> String {
        switch seconds {
        case 0.0: return "關閉 (0 秒)"
        case 2.0: return "2 秒 (預設)"
        default: return "\(Int(seconds)) 秒"
        }
    }

    private func exportDatabaseBackup() {
        if let exportURL = SQLiteBackupService.shared.exportBackupFile() {
            self.shareURL = exportURL
            self.isShowingShareSheet = true
        } else {
            alertMessage = "資料庫備份失敗"
            isShowingAlert = true
        }
    }

    private func handleRestoreSQLite(_ result: Result<[URL], Error>) {
        do {
            let urls = try result.get()
            guard let fileURL = urls.first else { return }
            _ = fileURL.startAccessingSecurityScopedResource()
            defer { fileURL.stopAccessingSecurityScopedResource() }

            try SQLiteBackupService.shared.restoreFromFile(at: fileURL)
            onDataChanged()
            alertMessage = "資料庫已成功還原！"
            isShowingAlert = true
        } catch {
            alertMessage = "資料庫還原失敗: \(error.localizedDescription)"
            isShowingAlert = true
        }
    }

    private func handleImportRiderClass(_ result: Result<[URL], Error>) {
        do {
            let urls = try result.get()
            guard let fileURL = urls.first else { return }
            _ = fileURL.startAccessingSecurityScopedResource()
            defer { fileURL.stopAccessingSecurityScopedResource() }

            let imported = try RiderClassArchiveService.shared.importRiderClass(from: fileURL)
            onDataChanged()
            alertMessage = "已成功匯入課表「\(imported.title)」及隨附之所有音訊曲目！"
            isShowingAlert = true
        } catch {
            alertMessage = "匯入課表包失敗: \(error.localizedDescription)"
            isShowingAlert = true
        }
    }
}
