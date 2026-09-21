import Foundation

public struct WorkoutCue: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public var segmentId: UUID
    public var offsetMs: Int
    public var posture: PostureType
    public var targetRpm: Int
    public var resistanceLevel: String
    public var message: String

    public init(
        id: UUID = UUID(),
        segmentId: UUID = UUID(),
        offsetMs: Int = 0,
        posture: PostureType = .seatedFlat,
        targetRpm: Int = 85,
        resistanceLevel: String = "LEVEL 5",
        message: String = ""
    ) {
        self.id = id
        self.segmentId = segmentId
        self.offsetMs = offsetMs
        self.posture = posture
        self.targetRpm = targetRpm
        self.resistanceLevel = resistanceLevel
        self.message = message
    }
}
