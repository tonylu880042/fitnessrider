package com.fitnessrider.util

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object VipSerialVerifier {
    private const val PREFIX = "FRVIP-"
    private const val PAYLOAD_HEX_LENGTH = 14

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
