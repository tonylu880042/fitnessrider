import SwiftUI

public struct BpmCalibrationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var bpm: Double
    @State private var tapCount: Int = 0
    @State private var isPulsing: Bool = false
    private let detector = TapTempoDetector()
    public let onSave: (Double) -> Void

    public init(initialBpm: Double, onSave: @escaping (Double) -> Void) {
        self._bpm = State(initialValue: max(40.0, min(240.0, initialBpm)))
        self.onSave = onSave
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                VStack(spacing: 4) {
                    Text("當前拍頻 (Tempo)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(FitnessRiderTheme.textSecondary)

                    HStack(alignment: .lastTextBaseline, spacing: 6) {
                        Text(String(format: "%.1f", bpm))
                            .font(.system(size: 44, weight: .black, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)

                        Text("BPM")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(FitnessRiderTheme.cardBackground)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
                )
                .padding(.horizontal, 24)

                Button {
                    withAnimation(.easeOut(duration: 0.08)) {
                        isPulsing = true
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        withAnimation(.easeOut(duration: 0.1)) {
                            isPulsing = false
                        }
                    }

                    if let detected = detector.recordTap() {
                        bpm = detected
                    }
                    tapCount = detector.tapCount
                } label: {
                    VStack(spacing: 4) {
                        Text("TAP")
                            .font(.system(size: 32, weight: .black))
                            .foregroundColor(.white)

                        Text(tapCount < 2 ? "輕敲測速" : "已點擊 \(tapCount) 次")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white.opacity(0.9))
                    }
                    .frame(width: 140, height: 140)
                    .background(FitnessRiderTheme.topBarGreen)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(FitnessRiderTheme.topBarGreenDark, lineWidth: 4)
                    )
                    .scaleEffect(isPulsing ? 0.92 : 1.0)
                    .shadow(color: FitnessRiderTheme.topBarGreen.opacity(0.3), radius: 8, x: 0, y: 4)
                }
                .buttonStyle(.plain)

                Text("請隨音樂重拍連續點擊 3 次以上自動計算")
                    .font(.system(size: 13))
                    .foregroundColor(FitnessRiderTheme.textSecondary)

                Divider()
                    .padding(.horizontal, 24)

                HStack(spacing: 12) {
                    stepperButton(title: "-5") { bpm = max(40.0, bpm - 5.0) }
                    stepperButton(title: "-1") { bpm = max(40.0, bpm - 1.0) }
                    stepperButton(title: "+1") { bpm = min(240.0, bpm + 1.0) }
                    stepperButton(title: "+5") { bpm = min(240.0, bpm + 5.0) }
                }
                .padding(.horizontal, 24)

                Spacer()

                Button {
                    onSave(bpm)
                    dismiss()
                } label: {
                    Text("套用 BPM")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(FitnessRiderTheme.topBarGreenDark)
                        .cornerRadius(10)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
            .padding(.top, 16)
            .navigationTitle("BPM 拍頻校正")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        detector.reset()
                        tapCount = 0
                    } label: {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }
                }
            }
        }
    }

    private func stepperButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(FitnessRiderTheme.cardBackground)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
                )
        }
    }
}
