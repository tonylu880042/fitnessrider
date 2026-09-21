import SwiftUI

/// Visual spinning bike handlebar widget highlighting Hand Position 1, 2, or 3
public struct HandPositionBadge: View {
    public let position: HandPosition
    public var isCompact: Bool = false
    public var showTitle: Bool = true

    public init(position: HandPosition, isCompact: Bool = false, showTitle: Bool = true) {
        self.position = position
        self.isCompact = isCompact
        self.showTitle = showTitle
    }

    public var body: some View {
        HStack(spacing: isCompact ? 6 : 10) {
            // Handlebar Schematic Vector
            HandlebarShape(activePosition: position)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 3)
                .background(
                    HandlebarHighlight(activePosition: position)
                        .stroke(FitnessRiderTheme.topBarGreenDark, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                )
                .frame(width: isCompact ? 36 : 48, height: isCompact ? 24 : 32)

            if showTitle {
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Text("把位")
                            .font(.system(size: isCompact ? 10 : 11, weight: .bold))
                            .foregroundColor(FitnessRiderTheme.textSecondary)
                        Text("\(position.rawValue)")
                            .font(.system(size: isCompact ? 14 : 16, weight: .black, design: .rounded))
                            .foregroundColor(FitnessRiderTheme.topBarGreenDark)
                    }

                    Text(positionLabel)
                        .font(.system(size: isCompact ? 9 : 11, weight: .medium))
                        .foregroundColor(FitnessRiderTheme.textPrimary)
                }
            }
        }
        .padding(.horizontal, isCompact ? 8 : 12)
        .padding(.vertical, isCompact ? 4 : 6)
        .background(Color.white)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(FitnessRiderTheme.cardBorder, lineWidth: 1)
        )
    }

    private var positionLabel: String {
        switch position {
        case .position1: return "平把中段"
        case .position2: return "橫桿轉折"
        case .position3: return "前端牛角"
        }
    }
}

// MARK: - Handlebar Schematic Path
private struct HandlebarShape: Shape {
    let activePosition: HandPosition

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height

        // Outer bullhorns + crossbar geometry of indoor cycle
        // Left horn
        path.move(to: CGPoint(x: w * 0.1, y: h * 0.1))
        path.addLine(to: CGPoint(x: w * 0.1, y: h * 0.65))
        // Left bend to crossbar
        path.addLine(to: CGPoint(x: w * 0.35, y: h * 0.85))
        // Center crossbar
        path.addLine(to: CGPoint(x: w * 0.65, y: h * 0.85))
        // Right bend
        path.addLine(to: CGPoint(x: w * 0.9, y: h * 0.65))
        // Right horn
        path.addLine(to: CGPoint(x: w * 0.9, y: h * 0.1))

        return path
    }
}

private struct HandlebarHighlight: Shape {
    let activePosition: HandPosition

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height

        switch activePosition {
        case .position1:
            // Center flat crossbar (Position 1)
            path.move(to: CGPoint(x: w * 0.35, y: h * 0.85))
            path.addLine(to: CGPoint(x: w * 0.65, y: h * 0.85))
        case .position2:
            // Corners / transitions (Position 2)
            path.move(to: CGPoint(x: w * 0.12, y: h * 0.55))
            path.addLine(to: CGPoint(x: w * 0.35, y: h * 0.85))
            path.move(to: CGPoint(x: w * 0.88, y: h * 0.55))
            path.addLine(to: CGPoint(x: w * 0.65, y: h * 0.85))
        case .position3:
            // Upper bullhorns (Position 3)
            path.move(to: CGPoint(x: w * 0.1, y: h * 0.1))
            path.addLine(to: CGPoint(x: w * 0.1, y: h * 0.45))
            path.move(to: CGPoint(x: w * 0.9, y: h * 0.1))
            path.addLine(to: CGPoint(x: w * 0.9, y: h * 0.45))
        }

        return path
    }
}
