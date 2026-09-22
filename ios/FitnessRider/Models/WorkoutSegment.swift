import Foundation

public struct WorkoutSegment: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public var classId: UUID
    public var orderIndex: Int
    public var title: String
    public var musicFileName: String
    public var durationMs: Int
    public var baseBpm: Double
    public var playbackRate: Double
    public var intensityZone: Int
    public var cues: [WorkoutCue]

    public var effectiveBpm: Double {
        return baseBpm * playbackRate
    }

    public var formattedDuration: String {
        let totalSeconds = durationMs / 1000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    public init(
        id: UUID = UUID(),
        classId: UUID = UUID(),
        orderIndex: Int = 0,
        title: String = "未命名段落",
        musicFileName: String = "",
        durationMs: Int = 300_000,
        baseBpm: Double = 128.0,
        playbackRate: Double = 1.0,
        intensityZone: Int = 2,
        cues: [WorkoutCue] = []
    ) {
        self.id = id
        self.classId = classId
        self.orderIndex = orderIndex
        self.title = title
        self.musicFileName = musicFileName
        self.durationMs = durationMs
        self.baseBpm = baseBpm
        self.playbackRate = playbackRate
        self.intensityZone = intensityZone
        self.cues = cues
    }
}
