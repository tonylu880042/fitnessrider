import Foundation

/// Pure Swift Zip Container Service for `.riderclass` master coach class packages.
/// Bundles workout_class.json + associated MP3/M4A audio tracks with zero external dependencies.
public final class RiderClassArchiveService: Sendable {
    public static let shared = RiderClassArchiveService()

    private let musicDir = SQLiteDatabase.shared.musicDirectoryURL

    private init() {}

    // MARK: - Export .riderclass

    public func exportRiderClass(for workoutClass: WorkoutClass) throws -> URL {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

        var filesToPackage: [(name: String, data: Data)] = []

        // 1. Encode Class JSON
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let jsonData = try encoder.encode(workoutClass)
        filesToPackage.append((name: "workout_class.json", data: jsonData))

        // 2. Collect Audio Files
        for seg in workoutClass.segments where !seg.musicFileName.isEmpty {
            let audioURL = musicDir.appendingPathComponent(seg.musicFileName)
            if FileManager.default.fileExists(atPath: audioURL.path) {
                if let audioData = try? Data(contentsOf: audioURL) {
                    // Only add if not already added
                    if !filesToPackage.contains(where: { $0.name == seg.musicFileName }) {
                        filesToPackage.append((name: seg.musicFileName, data: audioData))
                    }
                }
            }
        }

        // 3. Build Zip Archive (Store Mode 0)
        let zipData = createZipData(from: filesToPackage)
        let sanitizedTitle = workoutClass.title
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .joined(separator: "_")
        let outputFileName = "\(sanitizedTitle).riderclass"
        let outputURL = tempDir.appendingPathComponent(outputFileName)
        try zipData.write(to: outputURL)

        return outputURL
    }

    // MARK: - Import .riderclass

    public func importRiderClass(from fileURL: URL) throws -> WorkoutClass {
        let zipData = try Data(contentsOf: fileURL)
        let extractedFiles = extractZipData(zipData)

        guard let jsonFile = extractedFiles.first(where: { $0.name == "workout_class.json" }) else {
            throw NSError(domain: "RiderClassArchiveService", code: -1, userInfo: [NSLocalizedDescriptionKey: "封裝包中未找到 workout_class.json 課表定義檔"])
        }

        // 1. Decode Class
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let workoutClass = try decoder.decode(WorkoutClass.self, from: jsonFile.data)

        // 2. Save Audio Files to App Music Directory (with Zip Slip path traversal protection)
        let standardizedMusicPath = musicDir.standardizedFileURL.path
        for file in extractedFiles where file.name != "workout_class.json" {
            let destAudioURL = musicDir.appendingPathComponent(file.name).standardizedFileURL
            guard destAudioURL.path.hasPrefix(standardizedMusicPath + "/") else {
                continue
            }
            // Don't overwrite if existing
            if !FileManager.default.fileExists(atPath: destAudioURL.path) {
                try? file.data.write(to: destAudioURL)
            }
        }

        // 3. Save to Local SQLite
        ClassRepository.shared.saveClass(workoutClass)

        return workoutClass
    }

    // MARK: - Lightweight Pure Swift ZIP Container (Store Mode 0)

    private func createZipData(from files: [(name: String, data: Data)]) -> Data {
        var zip = Data()
        var centralDirectory = Data()
        var localHeaderOffsets: [UInt32] = []

        for file in files {
            let offset = UInt32(zip.count)
            localHeaderOffsets.append(offset)

            let fileNameBytes = [UInt8](file.name.utf8)
            let fileNameLength = UInt16(fileNameBytes.count)
            let uncompressedSize = UInt32(file.data.count)
            let crc = calculateCRC32(data: file.data)

            // Local File Header
            var localHeader = Data()
            localHeader.append(contentsOf: [0x50, 0x4b, 0x03, 0x04]) // Signature 0x04034b50
            localHeader.append(contentsOf: [0x14, 0x00])             // Version needed: 2.0
            localHeader.append(contentsOf: [0x00, 0x00])             // Flags: none
            localHeader.append(contentsOf: [0x00, 0x00])             // Compression: Store (0)
            localHeader.append(contentsOf: [0x00, 0x00, 0x00, 0x00]) // Mod time/date
            localHeader.append(contentsOf: withUnsafeBytes(of: crc.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: fileNameLength.littleEndian) { Array($0) })
            localHeader.append(contentsOf: [0x00, 0x00])             // Extra field length
            localHeader.append(contentsOf: fileNameBytes)
            localHeader.append(file.data)

            zip.append(localHeader)

            // Central Directory Entry
            var cdEntry = Data()
            cdEntry.append(contentsOf: [0x50, 0x4b, 0x01, 0x02])     // Signature 0x02014b50
            cdEntry.append(contentsOf: [0x14, 0x00])                 // Version made by: 2.0
            cdEntry.append(contentsOf: [0x14, 0x00])                 // Version needed: 2.0
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Flags
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Compression: Store
            cdEntry.append(contentsOf: [0x00, 0x00, 0x00, 0x00])     // Mod time/date
            cdEntry.append(contentsOf: withUnsafeBytes(of: crc.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: fileNameLength.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Extra length
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Comment length
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Disk number start
            cdEntry.append(contentsOf: [0x00, 0x00])                 // Internal attributes
            cdEntry.append(contentsOf: [0x00, 0x00, 0x00, 0x00])     // External attributes
            cdEntry.append(contentsOf: withUnsafeBytes(of: offset.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: fileNameBytes)

            centralDirectory.append(cdEntry)
        }

        let cdOffset = UInt32(zip.count)
        let cdSize = UInt32(centralDirectory.count)
        let totalEntries = UInt16(files.count)

        zip.append(centralDirectory)

        // End of Central Directory
        var eocd = Data()
        eocd.append(contentsOf: [0x50, 0x4b, 0x05, 0x06])           // Signature 0x06054b50
        eocd.append(contentsOf: [0x00, 0x00])                       // Disk number
        eocd.append(contentsOf: [0x00, 0x00])                       // Start disk
        eocd.append(contentsOf: withUnsafeBytes(of: totalEntries.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: totalEntries.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: cdSize.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: cdOffset.littleEndian) { Array($0) })
        eocd.append(contentsOf: [0x00, 0x00])                       // Comment length

        zip.append(eocd)
        return zip
    }

    private func extractZipData(_ zip: Data) -> [(name: String, data: Data)] {
        var results: [(name: String, data: Data)] = []
        var cursor = 0

        while cursor + 30 <= zip.count {
            // Check local header signature 0x04034b50 (PK\x03\x04)
            guard zip[cursor] == 0x50 && zip[cursor+1] == 0x4b &&
                  zip[cursor+2] == 0x03 && zip[cursor+3] == 0x04 else {
                break
            }

            let nameLen = Int(zip[cursor+26]) | (Int(zip[cursor+27]) << 8)
            let extraLen = Int(zip[cursor+28]) | (Int(zip[cursor+29]) << 8)
            let compSize = Int(zip[cursor+18]) | (Int(zip[cursor+19]) << 8) | (Int(zip[cursor+20]) << 16) | (Int(zip[cursor+21]) << 24)

            let nameStart = cursor + 30
            let nameEnd = nameStart + nameLen
            guard nameEnd <= zip.count else { break }

            let nameData = zip.subdata(in: nameStart..<nameEnd)
            let fileName = String(data: nameData, encoding: .utf8) ?? "unknown"

            let dataStart = nameEnd + extraLen
            let dataEnd = dataStart + compSize
            guard dataEnd <= zip.count else { break }

            let fileData = zip.subdata(in: dataStart..<dataEnd)
            results.append((name: fileName, data: fileData))

            cursor = dataEnd
        }

        return results
    }

    private func calculateCRC32(data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                let mask = (crc & 1) != 0 ? UInt32(0xEDB88320) : 0
                crc = (crc >> 1) ^ mask
            }
        }
        return ~crc
    }
}
