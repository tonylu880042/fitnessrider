import SwiftUI
import AVFoundation

struct ImportedTrackInfo {
    let fileName: String
    let durationMs: Int
    let bpm: Double
    var displayTitle: String? = nil
}

func resolveUniqueMusicFileName(_ desiredName: String, existingNames: Set<String>) -> String {
    guard existingNames.contains(desiredName) else { return desiredName }
    let ext = (desiredName as NSString).pathExtension
    let base = (desiredName as NSString).deletingPathExtension
    var suffix = 1
    var candidate: String
    repeat {
        candidate = ext.isEmpty ? "\(base)_\(suffix)" : "\(base)_\(suffix).\(ext)"
        suffix += 1
    } while existingNames.contains(candidate)
    return candidate
}

func musicTitleFromFileName(_ fileName: String) -> String {
    (fileName as NSString).deletingPathExtension
}

func buildSegmentsForImportedTracks(
    tracks: [ImportedTrackInfo],
    classId: UUID,
    startOrderIndex: Int
) -> [WorkoutSegment] {
    tracks.enumerated().map { index, track in
        WorkoutSegment(
            id: UUID(),
            classId: classId,
            orderIndex: startOrderIndex + index,
            title: track.displayTitle ?? musicTitleFromFileName(track.fileName),
            musicFileName: track.fileName,
            durationMs: track.durationMs > 0 ? track.durationMs : 300_000,
            baseBpm: track.bpm > 0 ? track.bpm : 128.0,
            playbackRate: 1.0,
            intensityZone: 3,
            cues: [
                WorkoutCue(offsetMs: 0, posture: .seatedFlat, targetRpm: 85, resistanceLevel: "LEVEL 4", message: "坐姿平路巡航")
            ]
        )
    }
}

func reindexedSegments(_ segments: [WorkoutSegment]) -> [WorkoutSegment] {
    segments.enumerated().map { index, seg in
        var s = seg
        s.orderIndex = index
        return s
    }
}

func segmentsAfterMove(_ segments: [WorkoutSegment], index: Int, offset: Int) -> [WorkoutSegment] {
    let target = index + offset
    guard segments.indices.contains(index), segments.indices.contains(target) else { return segments }
    var mutable = segments
    let item = mutable.remove(at: index)
    mutable.insert(item, at: target)
    return reindexedSegments(mutable)
}

func segmentsAfterRemoval(_ segments: [WorkoutSegment], index: Int) -> [WorkoutSegment] {
    guard segments.indices.contains(index) else { return segments }
    var mutable = segments
    mutable.remove(at: index)
    return reindexedSegments(mutable)
}

func selectedIndexAfterMove(_ selectedIndex: Int, movedFromIndex: Int, movedToIndex: Int) -> Int {
    if selectedIndex == movedFromIndex { return movedToIndex }
    if selectedIndex == movedToIndex { return movedFromIndex }
    return selectedIndex
}

func selectedIndexAfterRemoval(_ selectedIndex: Int, removedIndex: Int, newSize: Int) -> Int {
    let adjusted = selectedIndex > removedIndex ? selectedIndex - 1 : selectedIndex
    return min(max(adjusted, 0), max(newSize - 1, 0))
}

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
    @State private var isShowingMusicLibrary: Bool = false
    @State private var previewPlayheadMs: Int = 0
    @State private var isPreviewPlaying: Bool = false
    @State private var previewPlayer: AVAudioPlayer?
    @State private var previewTimer: Timer?
    @State private var importErrorMessage: String?
    @State private var segmentPendingDeleteIndex: Int?
    @State private var isShowingDeleteSegmentAlert: Bool = false

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
                TextField("課表名稱", text: $workoutClass.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 80)
            )

            HStack(spacing: 24) {
                Label("總時長: \(workoutClass.formattedDuration)", systemImage: "clock")
                Label("預估消耗: \(Int(workoutClass.estimatedCalories)) kcal", systemImage: "flame")

                Spacer()

                Button {
                    isShowingMusicLibrary = true
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

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(Array(workoutClass.segments.enumerated()), id: \.element.id) { index, segment in
                        segmentCard(segment, index: index)
                    }

                    Button {
                        isShowingMusicLibrary = true
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: "plus")
                                .font(.system(size: 24, weight: .bold))
                            Text("新增段落")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundColor(FitnessRiderTheme.topBarGreen)
                        .frame(width: 140, height: 132)
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
        .onDisappear {
            stopPreview(resetPlayhead: true)
        }
        .onChange(of: selectedSegmentIndex) { _, _ in
            stopPreview(resetPlayhead: true)
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
        .sheet(isPresented: $isShowingMusicLibrary) {
            MusicLibraryView(
                classId: workoutClass.id,
                startOrderIndex: workoutClass.segments.count,
                onDismiss: { isShowingMusicLibrary = false },
                onSegmentsCreated: { newSegments in appendSegments(newSegments) },
                onImportFailed: { failedLabels in
                    importErrorMessage = "匯入失敗：\(failedLabels.joined(separator: "、"))"
                }
            )
        }
        .alert(
            "匯入失敗",
            isPresented: Binding(
                get: { importErrorMessage != nil },
                set: { isPresented in if !isPresented { importErrorMessage = nil } }
            )
        ) {
            Button("確定", role: .cancel) {}
        } message: {
            Text(importErrorMessage ?? "")
        }
        .alert("刪除段落", isPresented: $isShowingDeleteSegmentAlert) {
            Button("取消", role: .cancel) {}
            Button("刪除", role: .destructive) {
                if let index = segmentPendingDeleteIndex {
                    deleteSegmentInEditor(at: index)
                }
            }
        } message: {
            let title = segmentPendingDeleteIndex.flatMap { idx in
                workoutClass.segments.indices.contains(idx) ? workoutClass.segments[idx].title : nil
            } ?? ""
            Text("確定要刪除「\(title)」嗎？段落內的動作提示會一併刪除，此動作無法復原。")
        }
    }

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

            Spacer(minLength: 0)

            HStack(spacing: 12) {
                Button {
                    moveSegmentInEditor(at: index, offset: -1)
                } label: {
                    Image(systemName: "chevron.up")
                        .font(.system(size: 13, weight: .bold))
                }
                .disabled(index == 0)
                .foregroundColor(index == 0 ? FitnessRiderTheme.textMuted : FitnessRiderTheme.topBarGreenDark)

                Button {
                    moveSegmentInEditor(at: index, offset: 1)
                } label: {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .bold))
                }
                .disabled(index == workoutClass.segments.count - 1)
                .foregroundColor(index == workoutClass.segments.count - 1 ? FitnessRiderTheme.textMuted : FitnessRiderTheme.topBarGreenDark)

                Spacer()

                Button {
                    segmentPendingDeleteIndex = index
                    isShowingDeleteSegmentAlert = true
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(FitnessRiderTheme.accentRed)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .frame(width: 170, height: 132)
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
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(segment.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(FitnessRiderTheme.textPrimary)

                    Spacer()

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
                    previewPlayer?.currentTime = Double(seekMs) / 1000.0
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

            HStack(spacing: 16) {
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

            let sec = cue.offsetMs / 1000
            Text(String(format: "%02d:%02d", sec / 60, sec % 60))
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(FitnessRiderTheme.textSecondary)

            Button {
                editingCue = cue
            } label: {
                Image(systemName: "pencil")
                    .font(.system(size: 15))
                    .foregroundColor(FitnessRiderTheme.textSecondary)
            }

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
        let rounded = (rate * 100).rounded() / 100
        workoutClass.segments[selectedSegmentIndex].playbackRate = rounded
        previewPlayer?.rate = Float(rounded)
    }

    private func loadWaveformForActiveSegment() {
        guard let segment = activeSegment else { return }
        isAnalyzingWaveform = true
        WaveformAnalyzer.shared.analyzeWaveform(for: segment.musicFileName) { samples, durationMs, bpm in
            Task { @MainActor in
                self.waveformSamples = samples
                self.isAnalyzingWaveform = false
                guard self.selectedSegmentIndex < self.workoutClass.segments.count else { return }
                var didChange = false
                if segment.baseBpm == 128.0 && bpm != 128.0 {
                    self.workoutClass.segments[self.selectedSegmentIndex].baseBpm = bpm
                    didChange = true
                }
                if durationMs > 0 && durationMs != segment.durationMs {
                    self.workoutClass.segments[self.selectedSegmentIndex].durationMs = durationMs
                    didChange = true
                }
                if didChange {
                    self.workoutClass.recalculateTotals()
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

        let player: AVAudioPlayer?
        if let existing = previewPlayer {
            player = existing
        } else {
            player = MusicSource.withResolvedFileURL(for: segment.musicFileName) { url in
                try? AVAudioPlayer(contentsOf: url)
            }.flatMap { $0 }
            if let p = player {
                p.enableRate = true
                self.previewPlayer = p
            }
        }

        if let player = player {
            player.enableRate = true
            player.rate = Float(segment.playbackRate)
            player.currentTime = Double(previewPlayheadMs) / 1000.0
            player.prepareToPlay()
            player.play()
        }

        previewTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            Task { @MainActor in
                guard self.isPreviewPlaying else { return }
                if let player = self.previewPlayer {
                    let currentMs = Int(player.currentTime * 1000.0)
                    if currentMs >= segment.durationMs || (!player.isPlaying && currentMs >= (segment.durationMs - 200)) {
                        self.stopPreview(resetPlayhead: true)
                    } else {
                        self.previewPlayheadMs = currentMs
                    }
                } else {
                    self.previewPlayheadMs += Int(50 * segment.playbackRate)
                    if self.previewPlayheadMs >= segment.durationMs {
                        self.stopPreview(resetPlayhead: true)
                    }
                }
            }
        }
    }

    private func stopPreview(resetPlayhead: Bool = false) {
        isPreviewPlaying = false
        previewPlayer?.pause()
        previewTimer?.invalidate()
        previewTimer = nil
        if resetPlayhead {
            previewPlayheadMs = 0
            previewPlayer?.currentTime = 0
            previewPlayer = nil
        }
    }

    private func appendSegments(_ newSegments: [WorkoutSegment]) {
        guard !newSegments.isEmpty else { return }
        workoutClass.segments.append(contentsOf: newSegments)
        selectedSegmentIndex = workoutClass.segments.count - 1
        workoutClass.recalculateTotals()
    }

    private func moveSegmentInEditor(at index: Int, offset: Int) {
        let target = index + offset
        guard workoutClass.segments.indices.contains(target) else { return }
        workoutClass.segments = segmentsAfterMove(workoutClass.segments, index: index, offset: offset)
        selectedSegmentIndex = selectedIndexAfterMove(selectedSegmentIndex, movedFromIndex: index, movedToIndex: target)
    }

    private func deleteSegmentInEditor(at index: Int) {
        guard workoutClass.segments.indices.contains(index) else { return }
        let newSegments = segmentsAfterRemoval(workoutClass.segments, index: index)
        selectedSegmentIndex = selectedIndexAfterRemoval(selectedSegmentIndex, removedIndex: index, newSize: newSegments.count)
        workoutClass.segments = newSegments
        workoutClass.recalculateTotals()
    }
}

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
