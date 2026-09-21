import SwiftUI
import UniformTypeIdentifiers

public struct ClassEditorView: View {
    @Environment(\.dismiss) private var dismiss

    @State public var workoutClass: WorkoutClass
    public let onSave: (WorkoutClass) -> Void

    @State private var selectedSegmentIndex: Int = 0
    @State private var waveformSamples: [Float] = []
    @State private var isAnalyzingWaveform: Bool = false
    @State private var isShowingCueSheet: Bool = false
    @State private var isShowingBpmSheet: Bool = false
    @State private var editingCue: WorkoutCue?
    @State private var isShowingMusicPicker: Bool = false
    @State private var previewPlayheadMs: Int = 0
    @State private var isPreviewPlaying: Bool = false
    @State private var previewTimer: Timer?

    public init(workoutClass: WorkoutClass, onSave: @escaping (WorkoutClass) -> Void) {
        self._workoutClass = State(initialValue: workoutClass)
        self.onSave = onSave
    }

    private var activeSegment: WorkoutSegment? {
        guard !workoutClass.segments.isEmpty,
              selectedSegmentIndex < workoutClass.segments.count else { return nil }
        return workoutClass.segments[selectedSegmentIndex]
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Top Bar
            TopNavBar(
                title: "",
                leading: {
                    Button("取消") {
                        stopPreview()
                        dismiss()
                    }
                },
                trailing: {
                    Button("儲存") {
                        stopPreview()
                        workoutClass.recalculateTotals()
                        onSave(workoutClass)
                        dismiss()
                    }
                }
            )
            .overlay(
                // Editable Title Field in Center
                TextField("課表名稱", text: $workoutClass.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 80)
            )

            // Sub-info Bar
            HStack(spacing: 24) {
                Label("總時長: \(workoutClass.formattedDuration)", systemImage: "clock")
                Label("預估消耗: \(Int(workoutClass.estimatedCalories)) kcal", systemImage: "flame")

                Spacer()

                Button {
                    isShowingMusicPicker = true
                } label: {
                    Label("匯入音樂檔", systemImage: "music.note.list")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(FitnessRiderTheme.topBarGreen.opacity(0.15))
                        .cornerRadius(6)
                }
            }
            .font(.system(size: 14, weight: .medium))
            .foregroundColor(FitnessRiderTheme.textSecondary)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(FitnessRiderTheme.cardHeaderBackground)

            Divider()

            // Horizontal Segment Cards List
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(Array(workoutClass.segments.enumerated()), id: \.element.id) { index, segment in
                        segmentCard(segment, index: index)
                    }

                    // Add Segment Button
                    Button {
                        addNewSegment()
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: "plus")
                                .font(.system(size: 24, weight: .bold))
                            Text("新增段落")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundColor(FitnessRiderTheme.topBarGreen)
                        .frame(width: 140, height: 100)
                        .background(FitnessRiderTheme.cardBackground)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [5]))
                                .foregroundColor(FitnessRiderTheme.topBarGreen)
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
            }
            .background(Color.white)

            Divider()

            // Waveform & Cue Editor Section
            if let segment = activeSegment {
                waveformEditorSection(for: segment)
            } else {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "music.note")
                        .font(.system(size: 48))
                        .foregroundColor(FitnessRiderTheme.textMuted)
                    Text("請點選上方「新增段落」開始編排曲目與動作")
                        .font(.system(size: 16))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                    Spacer()
                }
            }
        }
        .background(FitnessRiderTheme.canvasWhite)
        .onAppear {
            loadWaveformForActiveSegment()
        }
        .onChange(of: selectedSegmentIndex) { _, _ in
            stopPreview()
            previewPlayheadMs = 0
            loadWaveformForActiveSegment()
        }
        .sheet(item: $editingCue) { cue in
            CueEditorSheet(cue: cue) { updatedCue in
                saveCue(updatedCue)
            }
        }
        .sheet(isPresented: $isShowingBpmSheet) {
            if let segment = activeSegment {
                BpmCalibrationSheet(initialBpm: segment.baseBpm) { newBpm in
                    if selectedSegmentIndex < workoutClass.segments.count {
                        workoutClass.segments[selectedSegmentIndex].baseBpm = newBpm
                    }
                }
            }
        }
        .fileImporter(
            isPresented: $isShowingMusicPicker,
            allowedContentTypes: [UTType.audio, UTType.mp3, UTType.mpeg4Audio],
            allowsMultipleSelection: true
        ) { result in
            handleImportedMusic(result)
        }
    }

    // MARK: - Subviews

    private func segmentCard(_ segment: WorkoutSegment, index: Int) -> some View {
        let isSelected = index == selectedSegmentIndex

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("#\(index + 1)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(isSelected ? .white : FitnessRiderTheme.textSecondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(isSelected ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder)
                    .cornerRadius(4)

                Spacer()

                Text(segment.formattedDuration)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }

            Text(segment.title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(FitnessRiderTheme.textPrimary)
                .lineLimit(1)

            HStack {
                Label("\(Int(segment.effectiveBpm)) BPM", systemImage: "metronome")
                Spacer()
                Text("\(segment.cues.count) Cues")
            }
            .font(.system(size: 11))
            .foregroundColor(FitnessRiderTheme.textSecondary)
        }
        .padding(12)
        .frame(width: 170, height: 100)
        .background(isSelected ? FitnessRiderTheme.topBarGreen.opacity(0.08) : FitnessRiderTheme.cardBackground)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(isSelected ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder, lineWidth: isSelected ? 2 : 1)
        )
        .onTapGesture {
            selectedSegmentIndex = index
        }
    }

    private func waveformEditorSection(for segment: WorkoutSegment) -> some View {
        VStack(spacing: 12) {
            // Waveform Canvas
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(segment.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Spacer()

                    // Time Code
                    let currentSec = previewPlayheadMs / 1000
                    let totalSec = segment.durationMs / 1000
                    Text(String(format: "%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60))
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                }

                WaveformCanvasView(
                    samples: waveformSamples,
                    cues: segment.cues,
                    durationMs: segment.durationMs,
                    currentOffsetMs: previewPlayheadMs
                ) { seekMs in
                    previewPlayheadMs = seekMs
                }
                .frame(height: 90)
                .background(Color.white)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
                )
            }
            .padding(.horizontal, 20)

            // Audio Controls & Speed Shifting (-2%, 100%, +2%)
            HStack(spacing: 16) {
                // Play / Pause Preview Button
                Button {
                    togglePreview(for: segment)
                } label: {
                    Image(systemName: isPreviewPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(FitnessRiderTheme.topBarGreen)
                        .clipShape(Circle())
                }

                // Speed Buttons
                HStack(spacing: 8) {
                    Button("-2%") {
                        adjustRate(for: segment, delta: -0.02)
                    }
                    .buttonStyle(SpeedButtonStyle())

                    Button("100%") {
                        setRate(for: segment, rate: 1.0)
                    }
                    .buttonStyle(SpeedButtonStyle(isPrimary: true))

                    Button("+2%") {
                        adjustRate(for: segment, delta: 0.02)
                    }
                    .buttonStyle(SpeedButtonStyle())
                }

                // Live BPM readout & Tap-Tempo Calibration button
                Button {
                    isShowingBpmSheet = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "metronome.fill")
                            .font(.system(size: 13))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        Text(String(format: "%.0f%% (%.1f BPM)", segment.playbackRate * 100, segment.effectiveBpm))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(FitnessRiderTheme.textPrimary)
                        Text("校正")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(FitnessRiderTheme.topBarGreen.opacity(0.15))
                            .cornerRadius(4)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(FitnessRiderTheme.cardBackground)
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)

                Spacer()

                // Add Cue Button
                Button {
                    let newCue = WorkoutCue(
                        id: UUID(),
                        segmentId: segment.id,
                        offsetMs: previewPlayheadMs,
                        posture: .standingClimb,
                        targetRpm: 65,
                        resistanceLevel: "LEVEL 6",
                        message: "起立站姿爬坡"
                    )
                    editingCue = newCue
                } label: {
                    Label("標記 Cue 點", systemImage: "pin.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(FitnessRiderTheme.topBarGreenDark)
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal, 20)

            Divider()

            // Cues List Table
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(segment.cues) { cue in
                        cueRow(cue, segment: segment)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
        }
    }

    private func cueRow(_ cue: WorkoutCue, segment: WorkoutSegment) -> some View {
        HStack(spacing: 12) {
            // Posture Icon
            Image(systemName: cue.posture.sfSymbol)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                .frame(width: 36, height: 36)
                .background(FitnessRiderTheme.topBarGreen.opacity(0.12))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(cue.posture.localizedName)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    HandPositionBadge(position: cue.handPosition, isCompact: true, showTitle: false)

                    Text("\(cue.targetRpm) RPM")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(FitnessRiderTheme.topBarGreen)
                        .cornerRadius(4)

                    Text(cue.resistanceLevel)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }

                if !cue.reminders.isEmpty {
                    Text("口訣: " + cue.reminders.joined(separator: " • "))
                        .font(.system(size: 12))
                        .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                        .lineLimit(1)
                } else if !cue.message.isEmpty {
                    Text(cue.message)
                        .font(.system(size: 13))
                        .foregroundColor(FitnessRiderTheme.textSecondary)
                }
            }

            Spacer()

            // Time Offset
            let sec = cue.offsetMs / 1000
            Text(String(format: "%02d:%02d", sec / 60, sec % 60))
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(FitnessRiderTheme.textSecondary)

            // Edit Cue
            Button {
                editingCue = cue
            } label: {
                Image(systemName: "pencil")
                    .font(.system(size: 15))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }

            // Delete Cue
            Button {
                deleteCue(cue)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(FitnessRiderTheme.accentRed)
            }
        }
        .padding(12)
        .background(Color.white)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
        )
    }

    // MARK: - Actions

    private func addNewSegment() {
        let order = workoutClass.segments.count
        let seg = WorkoutSegment(
            id: UUID(),
            classId: workoutClass.id,
            orderIndex: order,
            title: "段落 \(order + 1)",
            musicFileName: "",
            durationMs: 300_000,
            baseBpm: 128.0,
            playbackRate: 1.0,
            intensityZone: 3,
            cues: [
                WorkoutCue(offsetMs: 0, posture: .seatedFlat, targetRpm: 85, resistanceLevel: "LEVEL 4", message: "坐姿平路巡航")
            ]
        )
        workoutClass.segments.append(seg)
        selectedSegmentIndex = order
        workoutClass.recalculateTotals()
    }

    private func saveCue(_ cue: WorkoutCue) {
        guard selectedSegmentIndex < workoutClass.segments.count else { return }
        if let idx = workoutClass.segments[selectedSegmentIndex].cues.firstIndex(where: { $0.id == cue.id }) {
            workoutClass.segments[selectedSegmentIndex].cues[idx] = cue
        } else {
            workoutClass.segments[selectedSegmentIndex].cues.append(cue)
        }
        workoutClass.segments[selectedSegmentIndex].cues.sort { $0.offsetMs < $1.offsetMs }
        editingCue = nil
    }

    private func deleteCue(_ cue: WorkoutCue) {
        guard selectedSegmentIndex < workoutClass.segments.count else { return }
        workoutClass.segments[selectedSegmentIndex].cues.removeAll { $0.id == cue.id }
    }

    private func adjustRate(for segment: WorkoutSegment, delta: Double) {
        let newRate = max(0.85, min(1.15, segment.playbackRate + delta))
        setRate(for: segment, rate: newRate)
    }

    private func setRate(for segment: WorkoutSegment, rate: Double) {
        guard selectedSegmentIndex < workoutClass.segments.count else { return }
        workoutClass.segments[selectedSegmentIndex].playbackRate = (rate * 100).rounded() / 100
    }

    private func loadWaveformForActiveSegment() {
        guard let segment = activeSegment else { return }
        isAnalyzingWaveform = true
        WaveformAnalyzer.shared.analyzeWaveform(for: segment.musicFileName) { samples, bpm in
            self.waveformSamples = samples
            self.isAnalyzingWaveform = false
            if segment.baseBpm == 128.0 && bpm != 128.0 {
                if self.selectedSegmentIndex < self.workoutClass.segments.count {
                    self.workoutClass.segments[self.selectedSegmentIndex].baseBpm = bpm
                }
            }
        }
    }

    private func togglePreview(for segment: WorkoutSegment) {
        if isPreviewPlaying {
            stopPreview()
        } else {
            startPreview(for: segment)
        }
    }

    private func startPreview(for segment: WorkoutSegment) {
        isPreviewPlaying = true
        previewTimer?.invalidate()
        previewTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            self.previewPlayheadMs += Int(100 * segment.playbackRate)
            if self.previewPlayheadMs >= segment.durationMs {
                self.previewPlayheadMs = 0
                self.stopPreview()
            }
        }
    }

    private func stopPreview() {
        isPreviewPlaying = false
        previewTimer?.invalidate()
        previewTimer = nil
    }

    private func handleImportedMusic(_ result: Result<[URL], Error>) {
        do {
            let urls = try result.get()
            guard let firstUrl = urls.first else { return }
            _ = firstUrl.startAccessingSecurityScopedResource()
            defer { firstUrl.stopAccessingSecurityScopedResource() }

            let musicDir = SQLiteDatabase.shared.musicDirectoryURL
            let destURL = musicDir.appendingPathComponent(firstUrl.lastPathComponent)

            try? FileManager.default.removeItem(at: destURL)
            try FileManager.default.copyItem(at: firstUrl, to: destURL)

            // Update active segment or add new segment
            if selectedSegmentIndex < workoutClass.segments.count {
                workoutClass.segments[selectedSegmentIndex].musicFileName = firstUrl.lastPathComponent
                loadWaveformForActiveSegment()
            }
        } catch {
            print("Import audio error: \(error)")
        }
    }
}

// Button Style for Speed Steppers
struct SpeedButtonStyle: ButtonStyle {
    var isPrimary: Bool = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(isPrimary ? .white : FitnessRiderTheme.textPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(isPrimary ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder)
            .cornerRadius(6)
            .opacity(configuration.isPressed ? 0.7 : 1.0)
    }
}
