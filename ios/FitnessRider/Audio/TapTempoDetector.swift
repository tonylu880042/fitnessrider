import Foundation

public final class TapTempoDetector: @unchecked Sendable {
    private var tapTimestamps: [TimeInterval] = []
    private let maxHistory: Int
    private let resetThreshold: TimeInterval

    public init(maxHistory: Int = 8, resetThreshold: TimeInterval = 2.5) {
        self.maxHistory = maxHistory
        self.resetThreshold = resetThreshold
    }

    public var tapCount: Int {
        return tapTimestamps.count
    }

    @discardableResult
    public func recordTap(at time: TimeInterval = Date().timeIntervalSince1970) -> Double? {
        if let last = tapTimestamps.last {
            let elapsed = time - last
            if elapsed > resetThreshold {
                tapTimestamps.removeAll()
            }
        }

        tapTimestamps.append(time)
        if tapTimestamps.count > maxHistory {
            tapTimestamps.removeFirst()
        }

        return calculateCurrentBpm()
    }

    public func calculateCurrentBpm() -> Double? {
        guard tapTimestamps.count >= 2 else { return nil }

        var intervals: [TimeInterval] = []
        for i in 1..<tapTimestamps.count {
            let delta = tapTimestamps[i] - tapTimestamps[i - 1]
            if delta >= 0.2 && delta <= 2.0 {
                intervals.append(delta)
            }
        }

        guard !intervals.isEmpty else { return nil }
        let avgInterval = intervals.reduce(0.0, +) / Double(intervals.count)
        guard avgInterval > 0.0 else { return nil }

        let rawBpm = 60.0 / avgInterval
        let clamped = min(240.0, max(40.0, rawBpm))
        return (clamped * 10.0).rounded() / 10.0
    }

    public func reset() {
        tapTimestamps.removeAll()
    }

    public static func calculateBpm(from intervals: [TimeInterval]) -> Double? {
        let valid = intervals.filter { $0 >= 0.2 && $0 <= 2.0 }
        guard !valid.isEmpty else { return nil }
        let avg = valid.reduce(0.0, +) / Double(valid.count)
        guard avg > 0.0 else { return nil }
        let rawBpm = 60.0 / avg
        let clamped = min(240.0, max(40.0, rawBpm))
        return (clamped * 10.0).rounded() / 10.0
    }
}
