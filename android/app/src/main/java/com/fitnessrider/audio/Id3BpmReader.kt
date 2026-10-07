package com.fitnessrider.audio

import java.io.InputStream

object Id3BpmReader {
    private const val HEADER_SIZE = 10
    private const val MAX_TAG_SIZE = 4 * 1024 * 1024
    private const val MIN_BPM = 40.0
    private const val MAX_BPM = 240.0

    fun read(input: InputStream): Double? {
        val header = ByteArray(HEADER_SIZE)
        if (readFully(input, header) < HEADER_SIZE) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val bodySize = syncsafe(header, 6)
        if (bodySize <= 0 || bodySize > MAX_TAG_SIZE) return null
        val tag = ByteArray(HEADER_SIZE + bodySize)
        header.copyInto(tag)
        val body = ByteArray(bodySize)
        val read = readFully(input, body)
        body.copyInto(tag, HEADER_SIZE, 0, read)
        return parse(tag.copyOf(HEADER_SIZE + read))
    }

    fun parse(tag: ByteArray): Double? {
        if (tag.size < HEADER_SIZE || tag[0] != 'I'.code.toByte() || tag[1] != 'D'.code.toByte() || tag[2] != '3'.code.toByte()) return null
        val version = tag[3].toInt()
        if (version != 3 && version != 4) return null
        val hasExtendedHeader = (tag[5].toInt() and 0x40) != 0
        var offset = HEADER_SIZE
        if (hasExtendedHeader) {
            if (tag.size < offset + 4) return null
            offset += if (version == 4) syncsafe(tag, offset) else bigEndian(tag, offset) + 4
        }
        val end = minOf(tag.size, HEADER_SIZE + syncsafe(tag, 6))
        while (offset + HEADER_SIZE <= end && tag[offset].toInt() != 0) {
            val id = String(tag, offset, 4, Charsets.ISO_8859_1)
            val size = if (version == 4) syncsafe(tag, offset + 4) else bigEndian(tag, offset + 4)
            val start = offset + HEADER_SIZE
            if (size < 0 || start + size > end) return null
            if (id == "TBPM") return parseTextFrame(tag, start, size)
            offset = start + size
        }
        return null
    }

    private fun parseTextFrame(tag: ByteArray, start: Int, size: Int): Double? {
        if (size < 2) return null
        val charset = when (tag[start].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        val text = String(tag, start + 1, size - 1, charset).trim { it <= ' ' || it == '\u0000' }
        val bpm = text.replace(',', '.').toDoubleOrNull() ?: return null
        return if (bpm in MIN_BPM..MAX_BPM) bpm else null
    }

    private fun syncsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or (bytes[offset + 3].toInt() and 0x7F)

    private fun bigEndian(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
