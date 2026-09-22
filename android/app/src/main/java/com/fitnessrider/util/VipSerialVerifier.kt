package com.fitnessrider.util

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 付費 VIP 序號驗簽（ECDSA P-256 / SHA-256），對應後端 backend/src/lib/vipSerial.ts。
 *
 * App 內只放「公鑰」，驗過簽章才開通；完全移除舊有
 * `code.startsWith("RIDER-VIP-") && code.length >= 14` 這種可偽造規則，
 * 以及寫死的 `RIDER-VIP-2026-PASS`、`FITNESS-PRO-ANNUAL-KEY`。
 *
 * 序號格式與 payload 結構詳見 backend/src/lib/vipSerial.ts 的說明註解：
 * `FRVIP-<payload hex 14碼>-<簽章 hex>`，payload = version(1 byte) + serialId(4 bytes)
 * + planDays(uint16 big-endian, 2 bytes)。簽章為 DER 編碼，`Signature("SHA256withECDSA")`
 * 原生就是驗 DER，不需要做任何格式轉換。
 *
 * 選 P-256 而非 Ed25519：Android minSdk 26，平台 `java.security.Signature` 對
 * Ed25519 要 API 33+ 才支援，P-256 從 API 1 就有，不需要新增任何依賴。
 */
object VipSerialVerifier {
    private const val PREFIX = "FRVIP-"
    private const val PAYLOAD_HEX_LENGTH = 14 // 7 bytes

    /** P-256 公鑰（SubjectPublicKeyInfo, DER, Base64），與後端 vipSerial.ts 內建的公鑰相同。 */
    private const val PUBLIC_KEY_SPKI_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjwz6HDGkpRRv8HNT+3HqVORKB6ByIDcHXG5StD9sr0fc2fmZs6R19dObhW2U75iKdEILOefyPHF/E7yY1mVC1g=="

    private val HEX_PATTERN = Regex("^[0-9A-F]+$")

    data class VipSerialInfo(val serialId: String, val planDays: Int)

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex length must be even" }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    /**
     * 驗證序號簽章，成功回傳解出的序號 ID 與開通天數；格式錯誤或簽章不符一律回傳 null。
     * 純離線運算（不連網），供「無網路時仍可開通」使用；但也因此無法檢查伺服器端的
     * 兌換次數上限，與既有推廣代碼的離線 fallback 有相同的已知限制。
     *
     * [publicKeySpkiB64] 預設用內建的正式公鑰；測試檔案可傳入自己產生的測試金鑰對的公鑰，
     * 驗證「用別把金鑰簽出來的序號」一定會被正式公鑰拒絕（見 FitnessRiderAndroidTest）。
     */
    fun verify(rawCode: String, publicKeySpkiB64: String = PUBLIC_KEY_SPKI_B64): VipSerialInfo? {
        val code = rawCode.trim().uppercase()
        if (!code.startsWith(PREFIX)) return null
        val rest = code.substring(PREFIX.length)
        val dashIndex = rest.indexOf('-')
        if (dashIndex <= 0) return null
        val payloadHex = rest.substring(0, dashIndex)
        val sigHex = rest.substring(dashIndex + 1)

        if (payloadHex.length != PAYLOAD_HEX_LENGTH || !HEX_PATTERN.matches(payloadHex)) return null
        if (sigHex.length < 16 || sigHex.length % 2 != 0 || !HEX_PATTERN.matches(sigHex)) return null

        return try {
            val payload = hexToBytes(payloadHex)
            val signatureBytes = hexToBytes(sigHex)

            val keyBytes = Base64.getDecoder().decode(publicKeySpkiB64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(keySpec)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(payload)
            if (!verifier.verify(signatureBytes)) return null

            val version = payload[0].toInt() and 0xFF
            if (version != 1) return null

            val serialId = payload.copyOfRange(1, 5).joinToString("") { "%02X".format(it) }
            val planDays = ((payload[5].toInt() and 0xFF) shl 8) or (payload[6].toInt() and 0xFF)
            if (planDays <= 0) return null

            VipSerialInfo(serialId, planDays)
        } catch (e: Exception) {
            null
        }
    }
}
