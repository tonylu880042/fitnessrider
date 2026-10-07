import Foundation
import AVFoundation

public final class WaveformAnalyzer: Sendable {
    public static let shared = WaveformAnalyzer()

    public static let analysisVersion = 1
    private static let minBpm = 65.0
    private static let maxBpm = 175.0
    private static let envelopeSampleRate = 22_050.0

    private init() {}

    public func analyzeWaveform(for fileName: String, completion: @escaping @MainActor @Sendable ([Float], Int, Double) -> Void) {
        let cached = ClassRepository.shared.fetchWaveform(for: fileName)
        if let cached, cached.analysisVersion >= Self.analysisVersion {
            DispatchQueue.main.async {
                completion(cached.samples, cached.durationMs, cached.bpm)
            }
            return
        }

        let fallbackResult = { () -> ([Float], Int, Double) in
            if let cached { return (cached.samples, cached.durationMs, cached.bpm) }
            return (self.generateSyntheticWaveform(sampleCount: 800), 300_000, 128.0)
        }

        guard MusicSource.fileExists(for: fileName) else {
            let fallback = fallbackResult()
            DispatchQueue.main.async {
                completion(fallback.0, fallback.1, fallback.2)
            }
            return
        }

        DispatchQueue.global(qos: .userInitiated).async {
            let didAnalyze: Bool = MusicSource.withResolvedFileURL(for: fileName) { fileURL -> Bool? in
                let asset = AVURLAsset(url: fileURL)
                guard let track = asset.tracks(withMediaType: .audio).first else {
                    return false
                }

                do {
                    let reader = try AVAssetReader(asset: asset)
                    let outputSettings: [String: Any] = [
                        AVFormatIDKey: kAudioFormatLinearPCM,
                        AVLinearPCMBitDepthKey: 16,
                        AVLinearPCMIsBigEndianKey: false,
                        AVLinearPCMIsFloatKey: false,
                        AVLinearPCMIsNonInterleaved: false,
                        AVNumberOfChannelsKey: 1,
                        AVSampleRateKey: Self.envelopeSampleRate
                    ]

                    let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
                    readerOutput.alwaysCopiesSampleData = false
                    reader.add(readerOutput)
                    reader.startReading()

                    var rawPeaks: [Float] = []
                    var envelope = EnergyEnvelopeAccumulator(sampleRate: Int(Self.envelopeSampleRate))
                    while reader.status == .reading {
                        guard let sampleBuffer = readerOutput.copyNextSampleBuffer(),
                              let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else {
                            break
                        }

                        var lengthAtOffset: Int = 0
                        var totalLength: Int = 0
                        var dataPointer: UnsafeMutablePointer<Int8>?

                        if CMBlockBufferGetDataPointer(blockBuffer, atOffset: 0, lengthAtOffsetOut: &lengthAtOffset, totalLengthOut: &totalLength, dataPointerOut: &dataPointer) == noErr,
                           let ptr = dataPointer {
                            let sampleCount = totalLength / MemoryLayout<Int16>.size
                            let int16Ptr = ptr.withMemoryRebound(to: Int16.self, capacity: sampleCount) { $0 }

                            var chunkPeak: Float = 0
                            for i in stride(from: 0, to: sampleCount, by: 4) {
                                let val = abs(Float(int16Ptr[i]) / 32768.0)
                                if val > chunkPeak { chunkPeak = val }
                            }
                            rawPeaks.append(chunkPeak)
                            envelope.add(UnsafeBufferPointer(start: int16Ptr, count: sampleCount))
                        }
                    }

                    let targetPoints = 800
                    var finalPoints: [Float] = []
                    if rawPeaks.count > targetPoints {
                        let bucketSize = rawPeaks.count / targetPoints
                        for i in 0..<targetPoints {
                            let start = i * bucketSize
                            let end = min(start + bucketSize, rawPeaks.count)
                            let maxVal = rawPeaks[start..<end].max() ?? 0.0
                            finalPoints.append(maxVal)
                        }
                    } else if !rawPeaks.isEmpty {
                        finalPoints = rawPeaks
                    } else {
                        finalPoints = self.generateSyntheticWaveform(sampleCount: targetPoints)
                    }

                    let maxAmp = finalPoints.max() ?? 1.0
                    let scale: Float = maxAmp > 0.05 ? 0.95 / maxAmp : 1.0
                    for i in 0..<finalPoints.count {
                        finalPoints[i] = min(1.0, max(0.05, finalPoints[i] * scale))
                    }

                    let durationMs = Int(CMTimeGetSeconds(asset.duration) * 1000)
                    let calculatedBpm = self.bpmTag(in: asset) ?? self.estimateBpmFromEnvelope(envelope.values, hopMs: envelope.hopMs)

                    ClassRepository.shared.saveWaveform(for: fileName, samples: finalPoints, durationMs: durationMs, bpm: calculatedBpm, analysisVersion: Self.analysisVersion)

                    DispatchQueue.main.async {
                        completion(finalPoints, durationMs, calculatedBpm)
                    }
                    return true
                } catch {
                    return false
                }
            } ?? false

            if !didAnalyze {
                let fallback = fallbackResult()
                DispatchQueue.main.async { completion(fallback.0, fallback.1, fallback.2) }
            }
        }
    }

    public func analyzeWaveform(for fileName: String) async -> ([Float], Int, Double) {
        await withCheckedContinuation { continuation in
            analyzeWaveform(for: fileName) { samples, durationMs, bpm in
                continuation.resume(returning: (samples, durationMs, bpm))
            }
        }
    }

    private func bpmTag(in asset: AVURLAsset) -> Double? {
        let items = asset.availableMetadataFormats.flatMap { asset.metadata(forFormat: $0) }
        for item in items {
            let identifier = item.identifier?.rawValue ?? ""
            guard identifier.hasSuffix("TBPM") || identifier.hasSuffix("tmpo") else { continue }
            let value = item.numberValue?.doubleValue
                ?? item.stringValue.flatMap { Double($0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")) }
            if let value, (40.0...240.0).contains(value) { return value }
        }
        return nil
    }

    public func estimateBpmFromEnvelope(_ envelope: [Float], hopMs: Double) -> Double {
        let n = envelope.count
        guard Double(n) * hopMs >= 10_000 else { return 128.0 }
        let mean = envelope.reduce(0.0) { $0 + Double($1) } / Double(n)
        guard mean > 0 else { return 128.0 }

        let logEnergy = envelope.map { log(1.0 + 0.3 * Double($0) / mean) }
        let onset = (0..<n).map { $0 == 0 ? 0.0 : max(0.0, logEnergy[$0] - logEnergy[$0 - 1]) }
        let kernel = [1.0, 2.0, 3.0, 2.0, 1.0]
        var smoothed = (0..<n).map { i -> Double in
            var acc = 0.0
            for k in 0..<kernel.count {
                let j = i + k - 2
                if j >= 0 && j < n { acc += kernel[k] * onset[j] }
            }
            return acc
        }
        let smoothedMean = smoothed.reduce(0.0, +) / Double(n)
        for i in 0..<n { smoothed[i] -= smoothedMean }

        let maxLag = Int(4.0 * 60_000.0 / (Self.minBpm * hopMs)) + 2
        guard n > maxLag * 2 else { return 128.0 }
        let autocorrelation = (0..<(maxLag + 2)).map { lag -> Double in
            var acc = 0.0
            for i in 0..<(n - lag) { acc += smoothed[i] * smoothed[i + lag] }
            return acc / Double(n - lag)
        }

        func autocorrelationAt(_ lag: Double) -> Double {
            let index = Int(lag)
            let fraction = lag - Double(index)
            return autocorrelation[index] * (1.0 - fraction) + autocorrelation[index + 1] * fraction
        }

        func score(_ bpm: Double) -> Double {
            let lag = 60_000.0 / (bpm * hopMs)
            let periodicity = autocorrelationAt(lag) + autocorrelationAt(2 * lag) / 2.0 + autocorrelationAt(4 * lag) / 4.0
            let octaves = log(bpm / 120.0) / log(2.0) / 0.8
            return periodicity * exp(-0.5 * octaves * octaves)
        }

        let coarse = stride(from: Int(Self.minBpm) * 2, through: Int(Self.maxBpm) * 2, by: 1).map { Double($0) / 2.0 }
        guard var best = coarse.max(by: { score($0) < score($1) }) else { return 128.0 }
        let center = best
        let fine = (-5...5).map { center + Double($0) / 10.0 }.filter { $0 >= Self.minBpm && $0 <= Self.maxBpm }
        best = fine.max(by: { score($0) < score($1) }) ?? best
        return (best * 10.0).rounded() / 10.0
    }

    public func generateSyntheticWaveform(sampleCount: Int = 800) -> [Float] {
        var result: [Float] = []
        for i in 0..<sampleCount {
            let progress = Double(i) / Double(sampleCount)
            let base = 0.35 + 0.3 * sin(progress * 20.0 * .pi)
            let beat = (i % 8 == 0) ? 0.3 : 0.0
            let noise = Double.random(in: -0.1...0.1)
            let val = Float(max(0.08, min(0.95, base + beat + noise)))
            result.append(val)
        }
        return result
    }
}

struct EnergyEnvelopeAccumulator {
    private let hopFrames: Int
    let hopMs: Double
    private(set) var values: [Float] = []
    private var energySum = 0.0
    private var frames = 0

    init(sampleRate: Int) {
        hopFrames = max(1, sampleRate / 100)
        hopMs = Double(hopFrames) * 1000.0 / Double(sampleRate)
    }

    mutating func add(_ samples: UnsafeBufferPointer<Int16>) {
        for sample in samples {
            let normalized = Double(sample) / 32768.0
            energySum += normalized * normalized
            frames += 1
            if frames == hopFrames {
                values.append(Float(energySum / Double(hopFrames)))
                energySum = 0
                frames = 0
            }
        }
    }
}
