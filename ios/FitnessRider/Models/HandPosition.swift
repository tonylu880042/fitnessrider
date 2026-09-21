import Foundation

public enum HandPosition: Int, Codable, CaseIterable, Identifiable, Sendable {
    case position1 = 1
    case position2 = 2
    case position3 = 3

    public var id: Int { rawValue }

    public var shortTitle: String {
        "\(rawValue) 號位"
    }

    public var localizedName: String {
        switch self {
        case .position1: return "1號位 (平把中段)"
        case .position2: return "2號位 (橫桿轉折)"
        case .position3: return "3號位 (前端牛角)"
        }
    }

    public var gripDescription: String {
        switch self {
        case .position1: return "雙手平放於中段近身把手，手肘微彎，專注核心穩定與基礎踩踏"
        case .position2: return "雙手握於把手兩側轉折處，支撐上半身重心，準備進行高轉速跑步抽車"
        case .position3: return "雙手握在把手最前端牛角突起處，利用槓桿原理發力，站立對抗重阻力"
        }
    }
}
