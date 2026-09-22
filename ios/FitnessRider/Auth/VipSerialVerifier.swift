import Foundation
import CryptoKit

public enum VipSerialVerifier {
    private static let prefix = "FRVIP-"
    private static let payloadHexLength = 14

    private static let publicKeySPKIBase64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjwz6HDGkpRRv8HNT+3HqVORKB6ByIDcHXG5StD9sr0fc2fmZs6R19dObhW2U75iKdEILOefyPHF/E7yY1mVC1g=="

    public struct VipSerialInfo: Equatable {
        public let serialId: String
        public let planDays: Int
    }

    private static func hexToData(_ hex: String) -> Data? {
        guard hex.count % 2 == 0 else { return nil }
        var data = Data(capacity: hex.count / 2)
        var idx = hex.startIndex
        while idx < hex.endIndex {
            let next = hex.index(idx, offsetBy: 2)
            guard let byte = UInt8(hex[idx..<next], radix: 16) else { return nil }
            data.append(byte)
            idx = next
        }
        return data
    }

    public static func verify(_ rawCode: String, publicKeySPKIBase64Override: String? = nil) -> VipSerialInfo? {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard code.hasPrefix(prefix) else { return nil }
        let rest = String(code.dropFirst(prefix.count))
        guard let dashRange = rest.range(of: "-") else { return nil }
        let payloadHex = String(rest[rest.startIndex..<dashRange.lowerBound])
        let sigHex = String(rest[dashRange.upperBound...])

        guard payloadHex.count == payloadHexLength,
              payloadHex.range(of: "^[0-9A-F]+$", options: .regularExpression) != nil else { return nil }
        guard sigHex.count >= 16, sigHex.count % 2 == 0,
              sigHex.range(of: "^[0-9A-F]+$", options: .regularExpression) != nil else { return nil }

        guard let payload = hexToData(payloadHex),
              let signatureBytes = hexToData(sigHex),
              let pubKeyData = Data(base64Encoded: publicKeySPKIBase64Override ?? publicKeySPKIBase64) else { return nil }

        do {
            let publicKey = try P256.Signing.PublicKey(derRepresentation: pubKeyData)
            let signature = try P256.Signing.ECDSASignature(derRepresentation: signatureBytes)
            guard publicKey.isValidSignature(signature, for: payload) else { return nil }

            let bytes = [UInt8](payload)
            guard bytes.count == 7, bytes[0] == 1 else { return nil }
            let serialId = bytes[1...4].map { String(format: "%02X", $0) }.joined()
            let planDays = (Int(bytes[5]) << 8) | Int(bytes[6])
            guard planDays > 0 else { return nil }

            return VipSerialInfo(serialId: serialId, planDays: planDays)
        } catch {
            return nil
        }
    }
}
