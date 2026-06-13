package com.cardmanager.nfc

private val HEX = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHexString(separator: String = ""): String =
    joinToString(separator) { byte ->
        val value = byte.toInt() and 0xFF
        "${HEX[value ushr 4]}${HEX[value and 0x0F]}"
    }

fun String.hexToBytes(): ByteArray {
    val compact = filterNot { it.isWhitespace() }
    require(compact.length % 2 == 0) { "Hex string length must be even." }
    return ByteArray(compact.length / 2) { index ->
        compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toBcdString(): String {
    val builder = StringBuilder(size * 2)
    forEach { byte ->
        val value = byte.toInt() and 0xFF
        builder.append(nibbleToChar(value ushr 4))
        builder.append(nibbleToChar(value and 0x0F))
    }
    return builder.toString()
}

private fun nibbleToChar(value: Int): Char =
    when (value) {
        in 0..9 -> '0' + value
        0x0D -> 'D'
        0x0F -> 'F'
        else -> '?'
    }
