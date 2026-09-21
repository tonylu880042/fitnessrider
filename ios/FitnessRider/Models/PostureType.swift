import Foundation
import SwiftUI

public enum PostureType: String, Codable, CaseIterable, Identifiable, Sendable {
    case seatedFlat = "SEATED_FLAT"
    case standingFlat = "STANDING_FLAT"
    case seatedClimb = "SEATED_CLIMB"
    case standingClimb = "STANDING_CLIMB"
    case jumps = "JUMPS"
    case sprint = "SPRINT"
    case recovery = "RECOVERY"

    public var id: String { rawValue }

    public var localizedName: String {
        switch self {
        case .seatedFlat: return "坐姿平路"
        case .standingFlat: return "站姿平路"
        case .seatedClimb: return "坐姿爬坡"
        case .standingClimb: return "站姿重爬坡"
        case .jumps: return "抽車節奏跳躍"
        case .sprint: return "極速全力衝刺"
        case .recovery: return "緩和放鬆"
        }
    }

    public var sfSymbol: String {
        switch self {
        case .seatedFlat: return "figure.outdoor.cycle"
        case .standingFlat: return "figure.indoor.cycle"
        case .seatedClimb: return "mountain.2"
        case .standingClimb: return "mountain.2.fill"
        case .jumps: return "arrow.up.and.down"
        case .sprint: return "bolt.fill"
        case .recovery: return "heart.fill"
        }
    }

    public var defaultRpm: Int {
        switch self {
        case .seatedFlat: return 90
        case .standingFlat: return 75
        case .seatedClimb: return 65
        case .standingClimb: return 60
        case .jumps: return 70
        case .sprint: return 110
        case .recovery: return 75
        }
    }
}
