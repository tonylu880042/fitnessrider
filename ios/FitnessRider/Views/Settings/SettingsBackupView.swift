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

    public init(onDataChanged: @escaping () -> Void) {
        self.onDataChanged = onDataChanged
    }

    public var body: some View {
        NavigationStack {
            Form {
                // Section 1: Audio & Class Execution Settings
                Section("課堂與音訊體驗") {
                    Toggle("動作切換 3-2-1 倒數提示音", isOn: $settings.isCountdownBeepEnabled)

                    Toggle("曲目段落結束自動暫停", isOn: $settings.isAutoPauseBetweenSegmentsEnabled)

                    Toggle("課堂進行中螢幕強制常亮", isOn: $settings.keepScreenAwakeInHUD)
                }

                // Section 2: SQLite Backup & Restore
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

                // Section 3: Device Binding & License
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
                        Text(licenseService.isLicensed ? "有效 (剩餘 \(licenseService.remainingDays) 天)" : "已過期")
                            .foregroundColor(licenseService.isLicensed ? FitnessRiderTheme.topBarGreenDark : FitnessRiderTheme.accentRed)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("設備唯一識別碼 (Device ID)")
                            .font(.caption)
                            .foregroundColor(FitnessRiderTheme.textSecondary)

                        Text(DeviceIdentifierService.shared.deviceFingerprint)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                    }
                }

                // Section 4: Music Folder Path
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
        }
    }

    // MARK: - Actions

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
