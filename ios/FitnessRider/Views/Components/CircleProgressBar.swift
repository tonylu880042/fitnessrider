import SwiftUI

public struct CircleProgressBar<Content: View>: View {
    public let progress: Double
    public let strokeWidth: CGFloat
    public let ringColor: Color
    public let trackColor: Color
    public let content: Content

    public init(
        progress: Double,
        strokeWidth: CGFloat = 16.0,
        ringColor: Color = FitnessRiderTheme.topBarGreen,
        trackColor: Color = Color(red: 235 / 255.0, green: 240 / 255.0, blue: 235 / 255.0),
        @ViewBuilder content: () -> Content
    ) {
        self.progress = max(0.0, min(1.0, progress))
        self.strokeWidth = strokeWidth
        self.ringColor = ringColor
        self.trackColor = trackColor
        self.content = content()
    }

    public var body: some View {
        ZStack {
            Circle()
                .stroke(trackColor, lineWidth: strokeWidth)

            Circle()
                .trim(from: 0.0, to: CGFloat(progress))
                .stroke(
                    ringColor,
                    style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.1), value: progress)

            content
        }
    }
}
