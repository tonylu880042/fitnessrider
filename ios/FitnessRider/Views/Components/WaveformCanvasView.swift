import SwiftUI

public struct WaveformCanvasView: View {
    public let samples: [Float]
    public let cues: [WorkoutCue]
    public let durationMs: Int
    public let currentOffsetMs: Int
    public let onSeek: (Int) -> Void

    public init(
        samples: [Float],
        cues: [WorkoutCue] = [],
        durationMs: Int,
        currentOffsetMs: Int = 0,
        onSeek: @escaping (Int) -> Void = { _ in }
    ) {
        self.samples = samples
        self.cues = cues
        self.durationMs = max(1000, durationMs)
        self.currentOffsetMs = currentOffsetMs
        self.onSeek = onSeek
    }

    public var body: some View {
        GeometryReader { geo in
            let progress = min(1.0, max(0.0, Double(currentOffsetMs) / Double(durationMs)))
            let playheadX = geo.size.width * CGFloat(progress)

            ZStack(alignment: .leading) {
                Canvas { context, size in
                    let totalSeconds = durationMs / 1000
                    let intervalSeconds = totalSeconds <= 120 ? 30 : (totalSeconds <= 300 ? 60 : 120)
                    var sec = intervalSeconds

                    while sec < totalSeconds {
                        let gridX = size.width * (CGFloat(sec) / CGFloat(totalSeconds))
                        var gridPath = Path()
                        gridPath.move(to: CGPoint(x: gridX, y: 0))
                        gridPath.addLine(to: CGPoint(x: gridX, y: size.height))
                        context.stroke(gridPath, with: .color(FitnessRiderTheme.cardBorder), lineWidth: 1)

                        let timeStr = String(format: "%02d:%02d", sec / 60, sec % 60)
                        let text = Text(timeStr)
                            .font(.system(size: 9, weight: .medium, design: .monospaced))
                            .foregroundColor(FitnessRiderTheme.textSecondary.opacity(0.8))
                        context.draw(text, at: CGPoint(x: gridX + 16, y: 8))

                        sec += intervalSeconds
                    }

                    guard !samples.isEmpty else { return }
                    let barWidth: CGFloat = 2.5
                    let spacing: CGFloat = 1.5
                    let totalBarSpace = barWidth + spacing
                    let barCount = Int(size.width / totalBarSpace)
                    let midY = size.height / 2.0

                    for i in 0..<barCount {
                        let sampleIdx = min(samples.count - 1, Int(Double(i) / Double(barCount) * Double(samples.count)))
                        let amplitude = CGFloat(samples[sampleIdx])
                        let barHeight = max(4.0, amplitude * (size.height * 0.72))
                        let x = CGFloat(i) * totalBarSpace
                        let isPlayed = x <= playheadX

                        let rect = CGRect(
                            x: x,
                            y: midY - barHeight / 2.0,
                            width: barWidth,
                            height: barHeight
                        )

                        let color = isPlayed ? FitnessRiderTheme.topBarGreen : FitnessRiderTheme.cardBorder
                        context.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(color))
                    }
                }

                ForEach(cues) { cue in
                    let cueProgress = min(1.0, max(0.0, Double(cue.offsetMs) / Double(durationMs)))
                    let cueX = geo.size.width * CGFloat(cueProgress)

                    VStack(spacing: 2) {
                        Image(systemName: cue.posture.sfSymbol)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 18, height: 18)
                            .background(FitnessRiderTheme.topBarGreenDark)
                            .clipShape(Circle())
                            .shadow(radius: 1)

                        Rectangle()
                            .fill(FitnessRiderTheme.topBarGreenDark)
                            .frame(width: 2, height: geo.size.height - 34)

                        Text("\(cue.targetRpm)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }
                    .position(x: cueX, y: geo.size.height / 2.0)
                }

                VStack(spacing: 0) {
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 10))
                        .foregroundColor(FitnessRiderTheme.accentRed)
                    Rectangle()
                        .fill(FitnessRiderTheme.accentRed)
                        .frame(width: 2.5, height: geo.size.height - 10)
                }
                .position(x: playheadX, y: geo.size.height / 2.0)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let ratio = min(1.0, max(0.0, value.location.x / geo.size.width))
                        let seekMs = Int(Double(durationMs) * Double(ratio))
                        onSeek(seekMs)
                    }
            )
        }
    }
}
