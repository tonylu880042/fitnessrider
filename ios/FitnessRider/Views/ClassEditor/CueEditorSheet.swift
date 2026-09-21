import SwiftUI

public struct CueEditorSheet: View {
    @Environment(\.dismiss) private var dismiss

    @State private var cue: WorkoutCue
    @State private var isShowingRemindersPicker: Bool = false
    public let onSave: (WorkoutCue) -> Void

    public init(cue: WorkoutCue, onSave: @escaping (WorkoutCue) -> Void) {
        self._cue = State(initialValue: cue)
        self.onSave = onSave
    }

    public var body: some View {
        NavigationStack {
            Form {
                // Time Code Section
                Section("動作開始時間") {
                    HStack {
                        Text("時間點")
                        Spacer()
                        let sec = cue.offsetMs / 1000
                        Text(String(format: "%02d:%02d (%d 秒)", sec / 60, sec % 60, sec))
                            .font(.system(.body, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }
                }

                // Posture Selection
                Section("騎乘姿勢") {
                    Picker("姿勢", selection: $cue.posture) {
                        ForEach(PostureType.allCases) { posture in
                            Label(posture.localizedName, systemImage: posture.sfSymbol)
                                .tag(posture)
                        }
                    }
                    .pickerStyle(.inline)
                    .onChange(of: cue.posture) { newPosture in
                        cue.handPosition = newPosture.defaultHandPosition
                    }

                    Text(cue.posture.trainingGoalDescription)
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                // Hand Position Section
                Section("握把把位指引") {
                    Picker("把位", selection: $cue.handPosition) {
                        ForEach(HandPosition.allCases) { pos in
                            Text(pos.shortTitle).tag(pos)
                        }
                    }
                    .pickerStyle(.segmented)

                    HStack(spacing: 12) {
                        HandPositionBadge(position: cue.handPosition, isCompact: true)
                        Text(cue.handPosition.gripDescription)
                            .font(.system(size: 12))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                    .padding(.vertical, 4)
                }

                // RPM & Resistance
                Section("目標踏頻與阻力") {
                    Stepper("目標 RPM: \(cue.targetRpm)", value: $cue.targetRpm, in: 40...140, step: 5)

                    HStack {
                        Text("建議阻力")
                        Spacer()
                        TextField("如 LEVEL 6 或 重爬坡阻力", text: $cue.resistanceLevel)
                            .multilineTextAlignment(.trailing)
                    }
                }

                // Coaching Reminders Library Section
                Section("教練專業口訣提示 (\(cue.reminders.count) 條)") {
                    if cue.reminders.isEmpty {
                        Text("尚未選取口訣，課堂中將預設輪播姿勢訓練目標")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    } else {
                        ForEach(cue.reminders, id: \.self) { reminder in
                            HStack {
                                Image(systemName: "quote.bubble.fill")
                                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                                    .font(.system(size: 13))
                                Text(reminder)
                                    .font(.system(size: 14))
                                Spacer()
                                Button {
                                    cue.reminders.removeAll(where: { $0 == reminder })
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundColor(FitnessRiderTheme.accentRed)
                                        .font(.system(size: 12))
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                    }

                    Button {
                        isShowingRemindersPicker = true
                    } label: {
                        Label("從 40+ 經典口訣庫選取...", systemImage: "plus.circle.fill")
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .font(.system(size: 14, weight: .semibold))
                    }
                }

                // Custom Message
                Section("備註說明 (Message)") {
                    TextField("例如：站姿起立，跟上音樂節拍！", text: $cue.message)
                }
            }
            .navigationTitle("編輯動作提示 (Cue)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("確定") {
                        onSave(cue)
                        dismiss()
                    }
                    .fontWeight(.bold)
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }
            .sheet(isPresented: $isShowingRemindersPicker) {
                RemindersPickerSheet(
                    posture: cue.posture,
                    selectedReminders: cue.reminders,
                    onSave: { updated in
                        cue.reminders = updated
                    }
                )
            }
        }
    }
}

// MARK: - Reminders Picker Sheet
private struct RemindersPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let posture: PostureType
    @State private var selectedReminders: Set<String>
    @State private var customInput: String = ""
    let onSave: ([String]) -> Void

    init(posture: PostureType, selectedReminders: [String], onSave: @escaping ([String]) -> Void) {
        self.posture = posture
        self._selectedReminders = State(initialValue: Set(selectedReminders))
        self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            List {
                Section("自訂口訣輸入") {
                    HStack {
                        TextField("輸入自訂帶課提醒...", text: $customInput)
                        Button("加入") {
                            let trimmed = customInput.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !trimmed.isEmpty {
                                selectedReminders.insert(trimmed)
                                customInput = ""
                            }
                        }
                        .disabled(customInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }
                }

                ForEach(CoachingReminderLibrary.categories(for: posture)) { category in
                    Section(category.title) {
                        ForEach(category.reminders, id: \.self) { reminder in
                            let isSelected = selectedReminders.contains(reminder)
                            Button {
                                if isSelected {
                                    selectedReminders.remove(reminder)
                                } else {
                                    selectedReminders.insert(reminder)
                                }
                            } label: {
                                HStack {
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(isSelected ? FitnessRiderTheme.topBarGreenDark : .secondary)
                                    Text(reminder)
                                        .foregroundColor(FitnessRiderTheme.textPrimary)
                                        .font(.system(size: 14))
                                    Spacer()
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("選擇指導口訣")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成 (\(selectedReminders.count))") {
                        onSave(Array(selectedReminders))
                        dismiss()
                    }
                    .fontWeight(.bold)
                    .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }
            }
        }
    }
}
