import SwiftUI

public struct ClassListView: View {
    @State private var classes: [WorkoutClass] = []
    @State private var selectedClassForEdit: WorkoutClass?
    @State private var selectedClassForHUD: WorkoutClass?
    @State private var connectRider3DForHUD: Bool = false
    @State private var pendingClassFor3D: WorkoutClass?
    @State private var rider3dScenes: [Rider3DScene] = []
    @State private var selectedSceneId: String = "1"
    @State private var isLoadingScenes: Bool = false
    @State private var isEnteringTraining: Bool = false
    @State private var rider3dErrorMessage: String?
    @State private var rider3dFailedStep: String?
    @State private var isShowingNewClassSheet: Bool = false
    @State private var isShowingSettingsSheet: Bool = false
    @State private var shareURL: URL?
    @State private var isShowingShareSheet: Bool = false
    @State private var classToDelete: WorkoutClass?
    @State private var isShowingDeleteAlert: Bool = false
    @State private var isWarningDismissed: Bool = false
    @State private var isShowingShareErrorAlert: Bool = false
    @State private var shareErrorMessage: String = ""
    @ObservedObject private var trackRepair = TrackDurationRepair.shared

    private var remainingDays: Int {
        VersionLifecycleManager.shared.remainingDays()
    }

    public init() {}

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                TopNavBar(
                    title: "課表清單",
                    leading: {
                        Button {
                            isShowingSettingsSheet = true
                        } label: {
                            Image(systemName: "gearshape.fill")
                                .font(.system(size: 20))
                        }
                    },
                    trailing: {
                        Button {
                            createNewClass()
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 22))
                        }
                    }
                )

                if !isWarningDismissed && !VersionLifecycleManager.shared.isVIP && remainingDays >= 1 && remainingDays <= 7 {
                    HStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(Color(red: 0.52, green: 0.39, blue: 0.02))
                            .font(.system(size: 20))

                        VStack(alignment: .leading, spacing: 2) {
                            Text("試用即將到期提醒")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(red: 0.52, green: 0.39, blue: 0.02))
                            Text("目前全功能免費試用將於 \(remainingDays) 天後到期。可前往設定輸入授權碼啟用專業版。")
                                .font(.system(size: 12))
                                .foregroundColor(Color(red: 0.52, green: 0.39, blue: 0.02).opacity(0.85))
                        }

                        Spacer()

                        Button {
                            UIApplication.shared.open(VersionLifecycleManager.updateURL)
                        } label: {
                            Text("更新")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(FitnessRiderTheme.topBarGreen)
                                .cornerRadius(6)
                        }

                        Button {
                            isWarningDismissed = true
                        } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(red: 0.52, green: 0.39, blue: 0.02))
                        }
                    }
                    .padding(12)
                    .background(Color(red: 1.0, green: 0.95, blue: 0.80))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(red: 1.0, green: 0.93, blue: 0.73), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }

                Group {
                    Text(trackRepair.progress.map { "正在讀取曲目長度 \($0.done)/\($0.total)" } ?? " ")
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                }

                if classes.isEmpty {
                    emptyStateView
                } else {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            ForEach(classes) { workoutClass in
                                classCard(for: workoutClass)
                            }
                        }
                        .padding(20)
                    }
                }
            }
            .background(FitnessRiderTheme.canvasWhite)
            .onAppear {
                loadClasses()
                TrackDurationRepair.shared.start(segments: classes.flatMap { $0.segments })
            }
            .onChange(of: trackRepair.persistedCount) { _ in
                loadClasses()
            }
            .onChange(of: trackRepair.progress == nil) { _ in
                loadClasses()
            }
            .fullScreenCover(item: $selectedClassForHUD, onDismiss: { loadClasses() }) { workoutClass in
                WorkoutHUDView(workoutClass: workoutClass, connectRider3D: connectRider3DForHUD)
            }
            .sheet(item: $pendingClassFor3D) { targetClass in
                rider3DSceneSelectionSheet(for: targetClass)
            }
            .sheet(item: $selectedClassForEdit) { workoutClass in
                ClassEditorView(workoutClass: workoutClass) { updatedClass in
                    ClassRepository.shared.saveClass(updatedClass)
                    loadClasses()
                }
            }
            .sheet(isPresented: $isShowingSettingsSheet) {
                SettingsBackupView {
                    loadClasses()
                }
            }
            .sheet(isPresented: $isShowingShareSheet) {
                if let url = shareURL {
                    ShareActivityView(activityItems: [url])
                }
            }
            .alert("確認刪除課表", isPresented: $isShowingDeleteAlert) {
                Button("取消", role: .cancel) {}
                Button("刪除", role: .destructive) {
                    if let toDelete = classToDelete {
                        ClassRepository.shared.deleteClass(byId: toDelete.id)
                        loadClasses()
                    }
                }
            } message: {
                Text("確定要刪除「\(classToDelete?.title ?? "")」嗎？此動作無法復原。")
            }
            .alert("課表匯出失敗", isPresented: $isShowingShareErrorAlert) {
                Button("確定", role: .cancel) {}
            } message: {
                Text(shareErrorMessage)
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "figure.indoor.cycle")
                .font(.system(size: 64))
                .foregroundColor(FitnessRiderTheme.topBarGreen)

            Text("尚未建立任何飛輪課表")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)

            Text("點擊右上角「+」或前往設定匯入示範課表包")
                .font(.system(size: 15))
                .foregroundColor(FitnessRiderTheme.textSecondary)

            Button {
                createNewClass()
            } label: {
                Label("新增第一堂課表", systemImage: "plus")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(FitnessRiderTheme.topBarGreen)
                    .cornerRadius(8)
            }
            Spacer()
        }
    }

    private func classCard(for workoutClass: WorkoutClass) -> some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(workoutClass.title)
                        .font(.system(size: 19, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    HStack(spacing: 12) {
                        Label("\(workoutClass.formattedDuration) (\(workoutClass.totalDurationMs / 1000) 秒)", systemImage: "clock.fill")
                        Label("\(Int(workoutClass.estimatedCalories)) kcal", systemImage: "flame.fill")
                    }
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                Spacer()

                Button {
                    shareClassPackage(workoutClass)
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                        .padding(8)
                }

                Button {
                    classToDelete = workoutClass
                    isShowingDeleteAlert = true
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 17))
                        .foregroundColor(FitnessRiderTheme.accentRed)
                        .padding(8)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(FitnessRiderTheme.cardHeaderBackground)

            Divider()
                .background(FitnessRiderTheme.cardBorder)

            HStack(spacing: 18) {
                Button {
                    let settings = AppSettings.shared
                    if isRider3DActive(
                        isRider3DApp: AppVariant.isRider3D,
                        isEnabled: settings.isRider3DEnabled,
                        hostInput: settings.rider3dHost
                    ) {
                        loadRider3DScenes(for: workoutClass)
                    } else {
                        connectRider3DForHUD = false
                        selectedClassForHUD = workoutClass
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 16, weight: .bold))
                        Text("上課開騎")
                            .font(.system(size: 15, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(FitnessRiderTheme.topBarGreen)
                    .cornerRadius(24)
                    .shadow(color: FitnessRiderTheme.topBarGreen.opacity(0.35), radius: 4, x: 0, y: 2)
                }

                VStack(alignment: .leading, spacing: 6) {
                    SegmentProgressBar(segments: workoutClass.segments, height: 10)

                    Text("\(workoutClass.segments.count) 首曲目段落・共 \(workoutClass.segments.reduce(0) { $0 + $1.cues.count }) 個動作節點")
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                Spacer()

                Button {
                    selectedClassForEdit = workoutClass
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "pencil")
                        Text("編輯")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(FitnessRiderTheme.topBarGreen.opacity(0.12))
                    .cornerRadius(6)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(Color.white)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
    }

    private func loadClasses() {
        self.classes = ClassRepository.shared.fetchAllClasses()
    }

    private func loadRider3DScenes(for workoutClass: WorkoutClass) {
        pendingClassFor3D = workoutClass
        isLoadingScenes = true
        isEnteringTraining = false
        rider3dErrorMessage = nil
        rider3dFailedStep = nil
        let host = AppSettings.shared.rider3dHost
        Task {
            let res = await Rider3DClient.fetchScenes(hostInput: host)
            isLoadingScenes = false
            if res.isSuccess, let scenes = res.data {
                rider3dScenes = scenes
                selectedSceneId = selectInitialRider3DSceneId(
                    scenes: scenes,
                    lastSceneId: AppSettings.shared.rider3dLastSceneId
                )
            } else {
                rider3dScenes = []
                rider3dFailedStep = "scenes"
                rider3dErrorMessage = res.errorMessage ?? "無法取得 3D 場景清單"
            }
        }
    }

    private func startRider3DEnterTraining(for workoutClass: WorkoutClass) {
        let trimmed = selectedSceneId.trimmingCharacters(in: .whitespacesAndNewlines)
        let sceneId = trimmed.isEmpty ? "1" : trimmed
        AppSettings.shared.rider3dLastSceneId = sceneId
        isEnteringTraining = true
        rider3dErrorMessage = nil
        rider3dFailedStep = nil
        let durationMs = workoutClass.totalDurationMs > 0
            ? workoutClass.totalDurationMs
            : workoutClass.segments.reduce(0) { $0 + $1.durationMs }
        let host = AppSettings.shared.rider3dHost
        let classId = workoutClass.id.uuidString
        let classTitle = workoutClass.title
        Task {
            let res = await Rider3DClient.enterTraining(
                hostInput: host,
                classId: classId,
                classTitle: classTitle,
                classDurationMs: durationMs,
                sceneId: sceneId
            )
            isEnteringTraining = false
            if res.isSuccess {
                pendingClassFor3D = nil
                connectRider3DForHUD = true
                selectedClassForHUD = workoutClass
            } else {
                rider3dFailedStep = "enter"
                rider3dErrorMessage = res.errorMessage ?? "無法連線至 3D 主機啟動訓練"
            }
        }
    }

    @ViewBuilder
    private func rider3DSceneSelectionSheet(for targetClass: WorkoutClass) -> some View {
        NavigationStack {
            Form {
                if isLoadingScenes {
                    Section {
                        Text("正在從 3D 主機讀取場景清單...")
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                } else if isEnteringTraining {
                    Section {
                        Text("正在通知 3D 主機啟動場景...")
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                } else if let errMsg = rider3dErrorMessage {
                    Section("3D 主機連線失敗") {
                        Text(errMsg)
                            .foregroundColor(FitnessRiderTheme.accentRed)
                        Text("您可以點擊「重試」，或選擇「不連 3D 直接上課」單獨播放課表。")
                            .font(.caption)
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Button {
                            if rider3dFailedStep == "enter" {
                                startRider3DEnterTraining(for: targetClass)
                            } else {
                                loadRider3DScenes(for: targetClass)
                            }
                        } label: {
                            Label("重試", systemImage: "arrow.clockwise")
                                .fontWeight(.bold)
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        }
                    }
                } else if rider3dScenes.isEmpty {
                    Section("選擇 3D 騎乘場景") {
                        Text("主機未提供特定場景清單，將使用預設場景 (1) 啟動。")
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                } else {
                    Section("選擇 3D 騎乘場景") {
                        ForEach(rider3dScenes) { scene in
                            Button {
                                selectedSceneId = scene.id
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(scene.name)
                                            .fontWeight(.bold)
                                            .foregroundColor(FitnessRiderTheme.textPrimary)
                                        if !scene.description.isEmpty {
                                            Text(scene.description)
                                                .font(.caption)
                                                .foregroundColor(FitnessRiderTheme.textSecondary)
                                        }
                                    }
                                    Spacer()
                                    if selectedSceneId == scene.id {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                                    }
                                }
                            }
                        }
                    }
                }

                Section {
                    if rider3dErrorMessage == nil {
                        Button {
                            startRider3DEnterTraining(for: targetClass)
                        } label: {
                            Label("進入 3D 課堂", systemImage: "play.fill")
                                .fontWeight(.bold)
                                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        }
                        .disabled(isLoadingScenes || isEnteringTraining)
                    }

                    Button {
                        pendingClassFor3D = nil
                        connectRider3DForHUD = false
                        selectedClassForHUD = targetClass
                    } label: {
                        Text("不連 3D 直接上課")
                            .fontWeight(.semibold)
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                    .disabled(isLoadingScenes || isEnteringTraining)
                }
            }
            .navigationTitle("3D 課堂設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        pendingClassFor3D = nil
                    }
                    .disabled(isLoadingScenes || isEnteringTraining)
                }
            }
        }
    }

    private func createNewClass() {
        let newClass = WorkoutClass(
            id: UUID(),
            title: "新飛輪課表",
            author: "Coach",
            createdAt: Date(),
            totalDurationMs: 0,
            estimatedCalories: 0,
            segments: []
        )
        ClassRepository.shared.saveClass(newClass)
        loadClasses()
        selectedClassForEdit = newClass
    }

    private func shareClassPackage(_ workoutClass: WorkoutClass) {
        do {
            let fileURL = try RiderClassArchiveService.shared.exportRiderClass(for: workoutClass)
            self.shareURL = fileURL
            self.isShowingShareSheet = true
        } catch {
            shareErrorMessage = error.localizedDescription
            isShowingShareErrorAlert = true
        }
    }
}

struct ShareActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
