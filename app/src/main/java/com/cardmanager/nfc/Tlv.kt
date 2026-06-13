package com.cardmanager.nfc

data class Tlv(
    val tag: String,
    val value: ByteArray,
    val children: List<Tlv> = emptyList(),
) {
    val length: Int = value.size
}

object TlvParser {
    fun parse(data: ByteArray): List<Tlv> = parseRange(data, 0, data.size)

    private fun parseRange(data: ByteArray, start: Int, end: Int): List<Tlv> {
        val nodes = mutableListOf<Tlv>()
        var offset = start
        while (offset < end) {
            while (offset < end && (data[offset] == 0x00.toByte() || data[offset] == 0xFF.toByte())) {
                offset++
            }
            if (offset >= end) break

            val tagStart = offset
            offset = readTagEnd(data, offset, end) ?: break
            val tagBytes = data.copyOfRange(tagStart, offset)

            val lengthResult = readLength(data, offset, end) ?: break
            offset = lengthResult.nextOffset

            val valueEnd = offset + lengthResult.length
            if (lengthResult.length < 0 || valueEnd > end) break

            val value = data.copyOfRange(offset, valueEnd)
            offset = valueEnd

            val isConstructed = (tagBytes.first().toInt() and 0x20) != 0
            val children = if (isConstructed) parseRange(value, 0, value.size) else emptyList()
            nodes += Tlv(tag = tagBytes.toHexString(), value = value, children = children)
        }
        return nodes
    }

    private fun readTagEnd(data: ByteArray, offset: Int, end: Int): Int? {
        if (offset >= end) return null
        var cursor = offset + 1
        if ((data[offset].toInt() and 0x1F) == 0x1F) {
            while (cursor < end) {
                val current = data[cursor].toInt() and 0xFF
                cursor++
                if ((current and 0x80) == 0) return cursor
            }
            return null
        }
        return cursor
    }

    private fun readLength(data: ByteArray, offset: Int, end: Int): LengthResult? {
        if (offset >= end) return null
        val first = data[offset].toInt() and 0xFF
        if ((first and 0x80) == 0) {
            return LengthResult(first, offset + 1)
        }

        val count = first and 0x7F
        if (count == 0 || count > 3 || offset + 1 + count > end) return null

        var length = 0
        repeat(count) { index ->
            length = (length shl 8) or (data[offset + 1 + index].toInt() and 0xFF)
        }
        return LengthResult(length, offset + 1 + count)
    }

    private data class LengthResult(val length: Int, val nextOffset: Int)
}

fun List<Tlv>.flattenTlvs(): List<Tlv> =
    flatMap { node -> listOf(node) + node.children.flattenTlvs() }

fun List<Tlv>.findFirst(tag: String): Tlv? =
    flattenTlvs().firstOrNull { it.tag.equals(tag, ignoreCase = true) }

fun List<Tlv>.findAll(tag: String): List<Tlv> =
    flattenTlvs().filter { it.tag.equals(tag, ignoreCase = true) }

data class DolEntry(val tag: String, val length: Int)

fun parseDol(data: ByteArray): List<DolEntry> {
    val entries = mutableListOf<DolEntry>()
    var offset = 0
    while (offset < data.size) {
        val tagStart = offset
        offset = readDolTagEnd(data, offset) ?: break
        if (offset >= data.size) break
        val tag = data.copyOfRange(tagStart, offset).toHexString()
        val length = data[offset].toInt() and 0xFF
        offset++
        entries += DolEntry(tag, length)
    }
    return entries
}

private fun readDolTagEnd(data: ByteArray, offset: Int): Int? {
    if (offset >= data.size) return null
    var cursor = offset + 1
    if ((data[offset].toInt() and 0x1F) == 0x1F) {
        while (cursor < data.size) {
            val current = data[cursor].toInt() and 0xFF
            cursor++
            if ((current and 0x80) == 0) return cursor
        }
        return null
    }
    return cursor
}
