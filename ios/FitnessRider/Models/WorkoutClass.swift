import Foundation

public struct WorkoutClass: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public var title: String
    public var author: String
    public var createdAt: Date
    public var totalDurationMs: Int
    public var estimatedCalories: Double
    public var segments: [WorkoutSegment]
    public var streamingPlaylistUrl: String

    public var totalDurationMinutes: Int {
        return totalDurationMs / 60_000
    }

    public var formattedDuration: String {
        let totalSeconds = totalDurationMs / 1000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    public init(
        id: UUID = UUID(),
        title: String = "全新飛輪課表",
        author: String = "教練",
        createdAt: Date = Date(),
        totalDurationMs: Int = 0,
        estimatedCalories: Double = 0.0,
        segments: [WorkoutSegment] = [],
        streamingPlaylistUrl: String = ""
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.createdAt = createdAt
        self.totalDurationMs = totalDurationMs
        self.estimatedCalories = estimatedCalories
        self.segments = segments
        self.streamingPlaylistUrl = streamingPlaylistUrl
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.title = try container.decode(String.self, forKey: .title)
        self.author = try container.decode(String.self, forKey: .author)
        self.createdAt = try container.decode(Date.self, forKey: .createdAt)
        self.totalDurationMs = try container.decode(Int.self, forKey: .totalDurationMs)
        self.estimatedCalories = try container.decode(Double.self, forKey: .estimatedCalories)
        self.segments = try container.decode([WorkoutSegment].self, forKey: .segments)
        let rawUrl = (try? container.decodeIfPresent(String.self, forKey: .streamingPlaylistUrl)) ?? ""
        self.streamingPlaylistUrl = normalizedStreamingPlaylistUrl(rawUrl) ?? ""
    }

    public mutating func recalculateTotals() {
        self.totalDurationMs = segments.reduce(0) { $0 + $1.durationMs }
        var cals: Double = 0
        for seg in segments {
            let minutes = Double(seg.durationMs) / 60_000.0
            let ratePerMin: Double = switch seg.intensityZone {
            case 1: 7.0
            case 2: 9.0
            case 3: 11.0
            case 4: 13.5
            case 5: 16.0
            default: 10.0
            }
            cals += minutes * ratePerMin
        }
        self.estimatedCalories = (cals * 10).rounded() / 10
    }
}

func normalizedStreamingPlaylistUrl(_ input: String) -> String? {
    let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return "" }
    let forbidden = CharacterSet.whitespacesAndNewlines.union(.controlCharacters)
    guard trimmed.rangeOfCharacter(from: forbidden) == nil,
          let components = URLComponents(string: trimmed),
          components.scheme?.lowercased() == "https",
          let host = components.host, !host.isEmpty else { return nil }
    return trimmed
}
