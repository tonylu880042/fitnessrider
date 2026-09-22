import Foundation
import CryptoKit

/// 付費 VIP 序號驗簽（ECDSA P-256 / SHA-256），對應後端 backend/src/lib/vipSerial.ts
/// 與 Android VipSerialVerifier.kt。
///
/// App 內只放「公鑰」，驗過簽章才開通；完全移除舊有
/// `code.hasPrefix("RIDER-VIP-") && code.count >= 14` 這種可偽造規則，
/// 以及寫死的 `RIDER-VIP-2026-PASS`、`FITNESS-PRO-ANNUAL-KEY`。
///
/// 序號格式與 payload 結構詳見 backend/src/lib/vipSerial.ts 的說明註解：
/// `FRVIP-<payload hex 14碼>-<簽章 hex>`，payload = version(1 byte) + serialId(4 bytes)
/// + planDays(uint16 big-endian, 2 bytes)。簽章為 DER 編碼，CryptoKit 的
/// `P256.Signing.ECDSASignature(derRepresentation:)` 原生就是吃 DER，不需要做任何格式轉換。
///
/// 選 P-256 而非 Ed25519：對應 Android minSdk 26 的平台 API 限制（見 Android 端註解），
/// 三平台（Node/Android/iOS）都原生支援 P-256，不需要新增任何依賴。
public enum VipSerialVerifier {
    private static let prefix = "FRVIP-"
    private static let payloadHexLength = 14 // 7 bytes

    /// P-256 公鑰（SubjectPublicKeyInfo, DER, Base64），與後端 vipSerial.ts 內建的公鑰相同。
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

    /// 驗證序號簽章，成功回傳解出的序號 ID 與開通天數；格式錯誤或簽章不符一律回傳 nil。
    /// 純離線運算（不連網），供「無網路時仍可開通」使用；但也因此無法檢查伺服器端的
    /// 兌換次數上限，與既有推廣代碼的離線 fallback 有相同的已知限制。
    ///
    /// `publicKeySPKIBase64Override` 預設用內建的正式公鑰；測試檔案可傳入自己產生的測試
    /// 金鑰對的公鑰，驗證「用別的金鑰簽出來的序號」一定會被正式公鑰拒絕（見 FitnessRiderTests）。
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
