import Foundation

@MainActor
public final class TrackDurationRepair: ObservableObject {
    public static let shared = TrackDurationRepair()

    @Published public private(set) var progress: (done: Int, total: Int)?
    @Published public private(set) var persistedCount: Int = 0

    private var isRunning = false

    private init() {}

    public func start(segments: [WorkoutSegment]) {
        let targets = segmentsNeedingDurationRepair(segments)
        guard !targets.isEmpty, !isRunning else { return }
        isRunning = true
        Task { @MainActor in
            for (index, segment) in targets.enumerated() {
                progress = (index + 1, targets.count)
                let (_, durationMs, bpm) = await WaveformAnalyzer.shared.analyzeWaveform(for: segment.musicFileName)
                let updated = segmentWithAnalyzedTrack(segment, durationMs: durationMs, bpm: bpm)
                if updated != segment {
                    ClassRepository.shared.updateSegmentTrack(
                        segmentId: segment.id,
                        durationMs: updated.durationMs,
                        baseBpm: updated.baseBpm != segment.baseBpm ? updated.baseBpm : nil
                    )
                    persistedCount += 1
                }
            }
            progress = nil
            isRunning = false
        }
    }
}
