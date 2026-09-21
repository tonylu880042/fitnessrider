import Foundation
import AVFoundation

public final class WaveformAnalyzer: Sendable {
    public static let shared = WaveformAnalyzer()

    private init() {}

    public func analyzeWaveform(for fileName: String, completion: @escaping @Sendable ([Float], Double) -> Void) {
        // 1. Check SQLite Cache
        if let cached = ClassRepository.shared.fetchWaveform(for: fileName) {
            DispatchQueue.main.async {
                completion(cached, 128.0)
            }
            return
        }

        // 2. Check File Exists
        let fileURL = SQLiteDatabase.shared.musicDirectoryURL.appendingPathComponent(fileName)
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            // Generate synthetic rhythmic waveform for placeholder
            let synthetic = generateSyntheticWaveform(sampleCount: 800)
            DispatchQueue.main.async {
                completion(synthetic, 128.0)
            }
            return
        }

        // 3. Asynchronous Streaming PCM Peak Analysis
        DispatchQueue.global(qos: .userInitiated).async {
            let asset = AVURLAsset(url: fileURL)
            guard let track = asset.tracks(withMediaType: .audio).first else {
                let fallback = self.generateSyntheticWaveform(sampleCount: 800)
                DispatchQueue.main.async { completion(fallback, 128.0) }
                return
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

                // Downsample to target 800 points
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

                // Simple peak-rate BPM estimation
                let durationMs = Int(CMTimeGetSeconds(asset.duration) * 1000)
                let calculatedBpm = self.estimateBpm(from: finalPoints, durationMs: durationMs)

                // Cache in SQLite
                ClassRepository.shared.saveWaveform(for: fileName, samples: finalPoints, durationMs: durationMs, bpm: calculatedBpm)

                DispatchQueue.main.async {
                    completion(finalPoints, calculatedBpm)
                }
            } catch {
                let fallback = self.generateSyntheticWaveform(sampleCount: 800)
                DispatchQueue.main.async { completion(fallback, 128.0) }
            }
        }
    }

    private func estimateBpm(from samples: [Float], durationMs: Int) -> Double {
        guard durationMs > 10_000, samples.count > 100 else { return 128.0 }
        let durationSeconds = Double(durationMs) / 1000.0

        // Find peaks above 0.6 threshold
        var peakIndices: [Int] = []
        for i in 1..<(samples.count - 1) {
            if samples[i] > 0.55 && samples[i] > samples[i-1] && samples[i] > samples[i+1] {
                peakIndices.append(i)
            }
        }

        guard peakIndices.count >= 4 else { return 128.0 }

        var intervals: [Double] = []
        for i in 1..<peakIndices.count {
            let sampleDiff = peakIndices[i] - peakIndices[i-1]
            let timeDiff = (Double(sampleDiff) / Double(samples.count)) * durationSeconds
            if timeDiff > 0.3 && timeDiff < 1.0 { // 60~200 BPM interval
                intervals.append(timeDiff)
            }
        }

        guard !intervals.isEmpty else { return 128.0 }
        let avgInterval = intervals.reduce(0.0, +) / Double(intervals.count)
        let bpm = 60.0 / avgInterval
        return (bpm * 10).rounded() / 10
    }

    private func generateSyntheticWaveform(sampleCount: Int) -> [Float] {
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
