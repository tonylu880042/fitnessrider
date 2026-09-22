import Foundation

public final class RiderClassArchiveService: Sendable {
    public static let shared = RiderClassArchiveService()

    private let musicDir = SQLiteDatabase.shared.musicDirectoryURL

    private init() {}

    public func exportRiderClass(for workoutClass: WorkoutClass) throws -> URL {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

        var filesToPackage: [(name: String, data: Data)] = []

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let jsonData = try encoder.encode(workoutClass)
        filesToPackage.append((name: "workout_class.json", data: jsonData))

        for seg in workoutClass.segments where !seg.musicFileName.isEmpty {
            let audioURL = musicDir.appendingPathComponent(seg.musicFileName)
            if FileManager.default.fileExists(atPath: audioURL.path) {
                if let audioData = try? Data(contentsOf: audioURL) {
                    if !filesToPackage.contains(where: { $0.name == seg.musicFileName }) {
                        filesToPackage.append((name: seg.musicFileName, data: audioData))
                    }
                }
            }
        }

        let zipData = createZipData(from: filesToPackage)
        let sanitizedTitle = workoutClass.title
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .joined(separator: "_")
        let outputFileName = "\(sanitizedTitle).riderclass"
        let outputURL = tempDir.appendingPathComponent(outputFileName)
        try zipData.write(to: outputURL)

        return outputURL
    }

    public func importRiderClass(from fileURL: URL) throws -> WorkoutClass {
        let zipData = try Data(contentsOf: fileURL)
        let extractedFiles = extractZipData(zipData)

        guard let jsonFile = extractedFiles.first(where: { $0.name == "workout_class.json" }) else {
            throw NSError(domain: "RiderClassArchiveService", code: -1, userInfo: [NSLocalizedDescriptionKey: "封裝包中未找到 workout_class.json 課表定義檔"])
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let workoutClass = try decoder.decode(WorkoutClass.self, from: jsonFile.data)

        let standardizedMusicPath = musicDir.standardizedFileURL.path
        for file in extractedFiles where file.name != "workout_class.json" {
            let destAudioURL = musicDir.appendingPathComponent(file.name).standardizedFileURL
            guard destAudioURL.path.hasPrefix(standardizedMusicPath + "/") else {
                continue
            }
            if !FileManager.default.fileExists(atPath: destAudioURL.path) {
                try? file.data.write(to: destAudioURL)
            }
        }

        ClassRepository.shared.saveClass(workoutClass)

        return workoutClass
    }

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

            var localHeader = Data()
            localHeader.append(contentsOf: [0x50, 0x4b, 0x03, 0x04])
            localHeader.append(contentsOf: [0x14, 0x00])
            localHeader.append(contentsOf: [0x00, 0x00])
            localHeader.append(contentsOf: [0x00, 0x00])
            localHeader.append(contentsOf: [0x00, 0x00, 0x00, 0x00])
            localHeader.append(contentsOf: withUnsafeBytes(of: crc.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            localHeader.append(contentsOf: withUnsafeBytes(of: fileNameLength.littleEndian) { Array($0) })
            localHeader.append(contentsOf: [0x00, 0x00])
            localHeader.append(contentsOf: fileNameBytes)
            localHeader.append(file.data)

            zip.append(localHeader)

            var cdEntry = Data()
            cdEntry.append(contentsOf: [0x50, 0x4b, 0x01, 0x02])
            cdEntry.append(contentsOf: [0x14, 0x00])
            cdEntry.append(contentsOf: [0x14, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00, 0x00, 0x00])
            cdEntry.append(contentsOf: withUnsafeBytes(of: crc.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: uncompressedSize.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: withUnsafeBytes(of: fileNameLength.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00])
            cdEntry.append(contentsOf: [0x00, 0x00, 0x00, 0x00])
            cdEntry.append(contentsOf: withUnsafeBytes(of: offset.littleEndian) { Array($0) })
            cdEntry.append(contentsOf: fileNameBytes)

            centralDirectory.append(cdEntry)
        }

        let cdOffset = UInt32(zip.count)
        let cdSize = UInt32(centralDirectory.count)
        let totalEntries = UInt16(files.count)

        zip.append(centralDirectory)

        var eocd = Data()
        eocd.append(contentsOf: [0x50, 0x4b, 0x05, 0x06])
        eocd.append(contentsOf: [0x00, 0x00])
        eocd.append(contentsOf: [0x00, 0x00])
        eocd.append(contentsOf: withUnsafeBytes(of: totalEntries.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: totalEntries.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: cdSize.littleEndian) { Array($0) })
        eocd.append(contentsOf: withUnsafeBytes(of: cdOffset.littleEndian) { Array($0) })
        eocd.append(contentsOf: [0x00, 0x00])

        zip.append(eocd)
        return zip
    }

    private func extractZipData(_ zip: Data) -> [(name: String, data: Data)] {
        var results: [(name: String, data: Data)] = []
        var cursor = 0

        while cursor + 30 <= zip.count {
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
