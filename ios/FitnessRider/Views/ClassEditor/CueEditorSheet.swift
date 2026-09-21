import SwiftUI

public struct CueEditorSheet: View {
    @Environment(\.dismiss) private var dismiss

    @State private var cue: WorkoutCue
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

                // Coach Prompt Message
                Section("教練提示口令 (Message)") {
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
        }
    }
}
