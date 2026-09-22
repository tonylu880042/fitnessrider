import SwiftUI

@main
struct FitnessRiderApp: App {
    @StateObject private var lifecycleManager = VersionLifecycleManager.shared
    @StateObject private var licenseService = LicenseVerificationService.shared
    @State private var importedClassNotice: String?
    @State private var isShowingImportNotice: Bool = false

    init() {
        VersionLifecycleManager.shared.evaluateExpirationOnLaunch()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if licenseService.mustUpdate {
                    VersionExpiredView(reason: .mustUpdate)
                } else if lifecycleManager.isExpiredOnLaunch {
                    VersionExpiredView(reason: .trialEnded)
                } else {
                    ClassListView()
                        .onOpenURL { url in
                            handleIncomingDocument(url)
                        }
                        .alert("匯入成功", isPresented: $isShowingImportNotice) {
                            Button("確定", role: .cancel) {}
                        } message: {
                            Text(importedClassNotice ?? "")
                        }
                }
            }
            .task {
                let buildNumber = Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0") ?? 0
                await licenseService.refreshFromServer(currentBuildNumber: buildNumber)
                lifecycleManager.evaluateExpirationOnLaunch()
            }
        }
    }

    private func handleIncomingDocument(_ url: URL) {
        _ = url.startAccessingSecurityScopedResource()
        defer { url.stopAccessingSecurityScopedResource() }

        if url.pathExtension.lowercased() == "riderclass" {
            do {
                let imported = try RiderClassArchiveService.shared.importRiderClass(from: url)
                importedClassNotice = "已成功由外部開啟並匯入課表「\(imported.title)」！"
                isShowingImportNotice = true
            } catch {
                print("Failed to import external riderclass: \(error)")
            }
        } else if url.pathExtension.lowercased() == "sqlite" {
            do {
                try SQLiteBackupService.shared.restoreFromFile(at: url)
                importedClassNotice = "已成功還原備份資料庫！"
                isShowingImportNotice = true
            } catch {
                print("Failed to restore external sqlite: \(error)")
            }
        }
    }
}
