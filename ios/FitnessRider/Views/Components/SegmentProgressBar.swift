import SwiftUI

public struct SegmentProgressBar: View {
    public let segments: [WorkoutSegment]
    public let height: CGFloat

    public init(segments: [WorkoutSegment], height: CGFloat = 8.0) {
        self.segments = segments
        self.height = height
    }

    public var body: some View {
        let totalDuration = max(1, segments.reduce(0) { $0 + $1.durationMs })

        GeometryReader { geo in
            HStack(spacing: 2) {
                ForEach(segments) { seg in
                    let ratio = CGFloat(seg.durationMs) / CGFloat(totalDuration)
                    let width = max(4, geo.size.width * ratio - 2)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(FitnessRiderTheme.colorForZone(seg.intensityZone))
                        .frame(width: width, height: height)
                }
            }
        }
        .frame(height: height)
    }
}
