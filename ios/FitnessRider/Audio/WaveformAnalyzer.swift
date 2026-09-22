import Foundation
import AVFoundation

public final class WaveformAnalyzer: Sendable {
    public static let shared = WaveformAnalyzer()

    private init() {}

    public func analyzeWaveform(for fileName: String, completion: @escaping @MainActor @Sendable ([Float], Int, Double) -> Void) {
        if let cached = ClassRepository.shared.fetchWaveform(for: fileName) {
            DispatchQueue.main.async {
                completion(cached.samples, cached.durationMs, cached.bpm)
            }
            return
        }

        guard MusicSource.fileExists(for: fileName) else {
            let synthetic = generateSyntheticWaveform(sampleCount: 800)
            DispatchQueue.main.async {
                completion(synthetic, 300_000, 128.0)
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
                        AVLinearPCMIsNonInterleaved: false
                    ]

                    let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
                    readerOutput.alwaysCopiesSampleData = false
                    reader.add(readerOutput)
                    reader.startReading()

                    var rawPeaks: [Float] = []
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
                    let calculatedBpm = self.estimateBpm(from: finalPoints, durationMs: durationMs)

                    ClassRepository.shared.saveWaveform(for: fileName, samples: finalPoints, durationMs: durationMs, bpm: calculatedBpm)

                    DispatchQueue.main.async {
                        completion(finalPoints, durationMs, calculatedBpm)
                    }
                    return true
                } catch {
                    return false
                }
            } ?? false

            if !didAnalyze {
                let fallback = self.generateSyntheticWaveform(sampleCount: 800)
                DispatchQueue.main.async { completion(fallback, 300_000, 128.0) }
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

    public func estimateBpm(from samples: [Float], durationMs: Int) -> Double {
        guard durationMs > 10_000, samples.count > 100 else { return 128.0 }
        let durationSeconds = Double(durationMs) / 1000.0

        let maxVal = samples.max() ?? 1.0
        let threshold = maxVal * 0.55

        var peakIndices: [Int] = []
        for i in 1..<(samples.count - 1) {
            if samples[i] > threshold && samples[i] >= samples[i-1] && samples[i] >= samples[i+1] {
                peakIndices.append(i)
            }
        }

        guard peakIndices.count >= 4 else { return 128.0 }

        var intervals: [Double] = []
        for i in 1..<peakIndices.count {
            let sampleDiff = peakIndices[i] - peakIndices[i-1]
            let timeDiff = (Double(sampleDiff) / Double(samples.count)) * durationSeconds
            if timeDiff >= 0.25 && timeDiff <= 1.2 {
                var t = timeDiff
                while t > 1.05 { t /= 2.0 }
                while t < 0.27 { t *= 2.0 }
                intervals.append(t)
            }
        }

        guard !intervals.isEmpty else { return 128.0 }

        let avgInterval = intervals.reduce(0.0, +) / Double(intervals.count)
        guard avgInterval > 0 else { return 128.0 }
        var bpm = 60.0 / avgInterval
        while bpm < 65.0 { bpm *= 2.0 }
        while bpm > 175.0 { bpm /= 2.0 }
        return (bpm * 10.0).rounded() / 10.0
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
