import SwiftUI

public struct ClassListView: View {
    @State private var classes: [WorkoutClass] = []
    @State private var selectedClassForEdit: WorkoutClass?
    @State private var selectedClassForHUD: WorkoutClass?
    @State private var isShowingNewClassSheet: Bool = false
    @State private var isShowingSettingsSheet: Bool = false
    @State private var shareURL: URL?
    @State private var isShowingShareSheet: Bool = false
    @State private var classToDelete: WorkoutClass?
    @State private var isShowingDeleteAlert: Bool = false

    public init() {}

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Signature #84BF09 Top Bar
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

                // Content List
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
            }
            // Navigation Destinations & Sheets
            .fullScreenCover(item: $selectedClassForHUD) { workoutClass in
                WorkoutHUDView(workoutClass: workoutClass)
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
        }
    }

    // MARK: - Subviews

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
            // Upper Card (Light Gray Header)
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

                // Share .riderclass Button
                Button {
                    shareClassPackage(workoutClass)
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                        .padding(8)
                }

                // Delete Button
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

            // Lower Card (Actions & Visual Segments)
            HStack(spacing: 18) {
                // Play Button (Starts Execution HUD)
                Button {
                    selectedClassForHUD = workoutClass
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

                // Segment Ratio Bar & Summary
                VStack(alignment: .leading, spacing: 6) {
                    SegmentProgressBar(segments: workoutClass.segments, height: 10)

                    Text("\(workoutClass.segments.count) 首曲目段落・共 \(workoutClass.segments.reduce(0) { $0 + $1.cues.count }) 個動作節點")
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                Spacer()

                // Edit Button
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

    // MARK: - Actions

    private func loadClasses() {
        self.classes = ClassRepository.shared.fetchAllClasses()
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
            print("Failed to export riderclass: \(error)")
        }
    }
}

// UIKit Share Sheet Wrapper
struct ShareActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
