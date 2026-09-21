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

    public var defaultHandPosition: HandPosition {
        switch self {
        case .seatedFlat, .seatedClimb, .recovery:
            return .position1
        case .standingFlat, .jumps:
            return .position2
        case .standingClimb, .sprint:
            return .position3
        }
    }

    public var assetImageName: String {
        switch self {
        case .seatedFlat, .recovery: return "icon_sit"
        case .standingFlat, .jumps: return "icon_stand"
        case .seatedClimb: return "icon_sit_climbing"
        case .standingClimb, .sprint: return "icon_stand_climbing"
        }
    }

    public var trainingGoalDescription: String {
        switch self {
        case .seatedFlat:
            return "騎車的最基本姿勢，幫忙建立騎車的基本力量以及基本體能"
        case .seatedClimb:
            return "以較高的阻力挑戰下半身，尤其是臀肌、腿後腱肌群的力量"
        case .standingFlat:
            return "運用到更多核心肌群的穩定，增加騎車速度並鍛鍊耐力"
        case .standingClimb:
            return "站立姿勢來爬更重的坡，鍛鍊股四頭肌的力量與爬坡爆發力"
        case .jumps:
            return "藉由規律的坐姿與站姿抽車交替，強化核心肌群與動態心肺爆發力"
        case .sprint:
            return "在平路或微坡以最快踩踏極限衝刺，激發無氧耐力與乳酸耐受力"
        case .recovery:
            return "以輕阻力舒緩踩踏，幫助心率回穩、排解肌肉乳酸並恢復體能"
        }
    }
}

