package com.cardmanager.nfc

import android.nfc.tech.IsoDep
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EmvProbeResult(
    val success: Boolean,
    val failureStage: String? = null,
    val organization: String = "Unknown",
    val currency: String? = null,
    val cardKind: String = "Unknown",
    val maskedPan: String? = null,
    val panLast4: String? = null,
    val expiration: String? = null,
    val applicationLabel: String? = null,
    val applicationPreferredName: String? = null,
    val selectedAid: String? = null,
    val debugTags: List<DebugTag> = emptyList(),
    val apduTrace: List<String> = emptyList(),
)

data class DebugTag(
    val source: String,
    val tag: String,
    val name: String,
    val length: Int,
    val valuePreview: String?,
)

class EmvCardReader {
    private val secureRandom = SecureRandom()

    fun read(isoDep: IsoDep): EmvProbeResult {
        val allTags = mutableListOf<DebugTag>()
        val trace = mutableListOf<String>()

        try {
            isoDep.timeout = 5_000
            if (!isoDep.isConnected) isoDep.connect()

            val ppse = transceive(isoDep, "SELECT PPSE", selectByName("2PAY.SYS.DDF01"), trace)
            if (!ppse.isSuccess) {
                return failure("PPSE 选择失败 (${ppse.swHex})", allTags, trace)
            }

            val ppseTlvs = parseAndCollect("PPSE", ppse.data, allTags)
            val aids = ppseTlvs.findAll("4F")
                .map { it.value.toHexString() }
                .distinct()
                .sortedWith(compareBy { aid -> aidSelectionRank(aid) })

            if (aids.isEmpty()) {
                return failure("未找到 AID", allTags, trace)
            }

            var bestResult: EmvProbeResult? = null

            for (aid in aids) {
                val response = transceive(isoDep, "SELECT AID ${shortAid(aid)}", selectByAid(aid.hexToBytes()), trace)
                if (!response.isSuccess) continue

                val selectedTlvs = parseAndCollect("SELECT AID", response.data, allTags)
                val gpoCommand = buildGpoCommand(selectedTlvs.findFirst("9F38")?.value)
                val gpo = transceive(isoDep, "GPO", gpoCommand, trace)
                if (!gpo.isSuccess) {
                    bestResult = bestResult.prefer(
                        partialOrFailure(
                            stage = "GPO/读取记录失败 (${gpo.swHex})",
                            aid = aid,
                            tlvs = ppseTlvs + selectedTlvs,
                            debugTags = allTags,
                            trace = trace,
                        ),
                    )
                    continue
                }

                val gpoTlvs = parseAndCollect("GPO", gpo.data, allTags)
                val afl = extractAfl(gpoTlvs)
                if (afl == null || afl.isEmpty()) {
                    val result = partialOrFailure(
                        stage = "GPO/读取记录失败：未返回 AFL",
                        aid = aid,
                        tlvs = ppseTlvs + selectedTlvs + gpoTlvs,
                        debugTags = allTags,
                        trace = trace,
                    )
                    if (result.success) return result
                    bestResult = bestResult.prefer(result)
                    continue
                }

                val recordTlvs = mutableListOf<Tlv>()
                var readAnyRecord = false
                for (record in recordsFromAfl(afl)) {
                    val command = readRecord(record.sfi, record.number)
                    val recordResponse = transceive(isoDep, "READ SFI ${record.sfi} REC ${record.number}", command, trace)
                    if (recordResponse.isSuccess) {
                        readAnyRecord = true
                        recordTlvs += parseAndCollect("READ RECORD", recordResponse.data, allTags)
                    }
                }

                if (!readAnyRecord) {
                    bestResult = bestResult.prefer(
                        partialOrFailure(
                            stage = "GPO/读取记录失败：AFL 记录读取均失败",
                            aid = aid,
                            tlvs = ppseTlvs + selectedTlvs + gpoTlvs,
                            debugTags = allTags,
                            trace = trace,
                        ),
                    )
                    continue
                }

                val combinedTlvs = ppseTlvs + selectedTlvs + gpoTlvs + recordTlvs
                val result = buildResult(aid, combinedTlvs, allTags, trace, failureStage = null)
                if (result.success) return result
                bestResult = bestResult.prefer(result)
            }

            return bestResult ?: failure("AID 选择失败", allTags, trace)
        } catch (error: IOException) {
            return failure("ISO-DEP 通信失败：${error.javaClass.simpleName}", allTags, trace)
        } catch (error: IllegalArgumentException) {
            return failure("解析失败：${error.message.orEmpty()}", allTags, trace)
        } finally {
            try {
                isoDep.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun partialOrFailure(
        stage: String,
        aid: String,
        tlvs: List<Tlv>,
        debugTags: List<DebugTag>,
        trace: List<String>,
    ): EmvProbeResult {
        val result = buildResult(aid, tlvs, debugTags, trace, stage)
        return if (result.panLast4 == null && result.expiration == null) {
            result.copy(success = false)
        } else {
            result
        }
    }

    private fun EmvProbeResult?.prefer(candidate: EmvProbeResult): EmvProbeResult =
        when {
            this == null -> candidate
            candidate.score() > score() -> candidate
            else -> this
        }

    private fun EmvProbeResult.score(): Int {
        var score = 0
        if (success) score += 100
        if (panLast4 != null) score += 20
        if (expiration != null) score += 20
        if (organization != "Unknown") score += 8
        if (cardKind != "Unknown") score += 6
        if (currency != null) score += 4
        if (selectedAid != null) score += 2
        return score
    }

    private fun buildResult(
        aid: String,
        tlvs: List<Tlv>,
        debugTags: List<DebugTag>,
        trace: List<String>,
        failureStage: String?,
    ): EmvProbeResult {
        val pan = readPan(tlvs)
        val expiration = readExpiration(tlvs)
        val label = tlvs.findFirst("50")?.value?.toAscii()
        val preferredName = tlvs.findFirst("9F12")?.value?.toAscii()
        val last4 = pan?.takeLast(4)
        val appNames = listOfNotNull(label, preferredName)
        val readMissing = when {
            pan == null && expiration == null -> "未读到 PAN/有效期"
            pan == null -> "未读到 PAN"
            expiration == null -> "未读到有效期"
            else -> null
        }

        return EmvProbeResult(
            success = readMissing == null,
            failureStage = failureStage ?: readMissing,
            organization = organizationFromAidOrBin(aid, pan),
            currency = readCurrency(tlvs),
            cardKind = inferCardKind(aid, appNames),
            maskedPan = last4?.let { "**** **** **** $it" },
            panLast4 = last4,
            expiration = expiration,
            applicationLabel = label,
            applicationPreferredName = preferredName,
            selectedAid = aid,
            debugTags = debugTags.distinctBy { "${it.source}|${it.tag}|${it.length}|${it.valuePreview}" },
            apduTrace = trace,
        )
    }

    private fun readPan(tlvs: List<Tlv>): String? {
        tlvs.findFirst("5A")?.value
            ?.toBcdString()
            ?.trimEnd('F')
            ?.filter(Char::isDigit)
            ?.takeIf { it.length >= 4 }
            ?.let { return it }

        val track2 = tlvs.findFirst("57")?.value?.toBcdString()?.trimEnd('F') ?: return null
        return track2.substringBefore('D')
            .filter(Char::isDigit)
            .takeIf { it.length >= 4 }
    }

    private fun readExpiration(tlvs: List<Tlv>): String? {
        tlvs.findFirst("5F24")?.value?.toBcdString()?.let { date ->
            if (date.length >= 4) return "${date.substring(2, 4)}/${date.substring(0, 2)}"
        }

        val track2 = tlvs.findFirst("57")?.value?.toBcdString()?.trimEnd('F') ?: return null
        val separator = track2.indexOf('D')
        if (separator < 0 || separator + 5 > track2.length) return null
        val yyMm = track2.substring(separator + 1, separator + 5)
        return "${yyMm.substring(2, 4)}/${yyMm.substring(0, 2)}"
    }

    private fun readCurrency(tlvs: List<Tlv>): String? {
        val code = tlvs.findFirst("9F42")?.value?.toBcdString()
            ?: tlvs.findFirst("5F2A")?.value?.toBcdString()
            ?: return null
        val normalized = code.takeLast(3).padStart(3, '0')
        return when (normalized) {
            "156" -> "CNY (156)"
            "840" -> "USD (840)"
            "344" -> "HKD (344)"
            "446" -> "MOP (446)"
            "392" -> "JPY (392)"
            "978" -> "EUR (978)"
            "826" -> "GBP (826)"
            "702" -> "SGD (702)"
            else -> normalized
        }
    }

    private fun parseAndCollect(source: String, data: ByteArray, debugTags: MutableList<DebugTag>): List<Tlv> {
        val tlvs = TlvParser.parse(data)
        tlvs.flattenTlvs()
            .filter { it.children.isEmpty() }
            .forEach { debugTags += it.toDebugTag(source) }
        return tlvs
    }

    private fun Tlv.toDebugTag(source: String): DebugTag =
        DebugTag(
            source = source,
            tag = tag,
            name = TagDictionary.name(tag),
            length = length,
            valuePreview = safeValuePreview(this),
        )

    private fun safeValuePreview(tlv: Tlv): String? =
        when (tlv.tag.uppercase()) {
            "4F" -> tlv.value.toHexString()
            "50", "9F12" -> tlv.value.toAscii()
            "5A" -> tlv.value.toBcdString().trimEnd('F').filter(Char::isDigit).takeLast(4)
                .takeIf { it.isNotEmpty() }
                ?.let { "**** **** **** $it" }
                ?: "<masked PAN>"
            "57" -> "<masked Track 2 Equivalent Data>"
            "5F24" -> readExpiration(listOf(tlv))
            "5F2A", "9F42" -> tlv.value.toBcdString().takeLast(3)
            "94" -> tlv.value.toHexString(" ")
            else -> null
        }

    private fun transceive(
        isoDep: IsoDep,
        label: String,
        command: ByteArray,
        trace: MutableList<String>,
    ): ApduResponse {
        val response = isoDep.transceive(command)
        val apdu = ApduResponse.from(response)
        trace += "$label -> ${apdu.swHex}"
        return apdu
    }

    private fun selectByName(name: String): ByteArray {
        val nameBytes = name.encodeToByteArray()
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, nameBytes.size.toByte()) +
            nameBytes +
            byteArrayOf(0x00)
    }

    private fun selectByAid(aid: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    private fun buildGpoCommand(pdol: ByteArray?): ByteArray {
        val pdolData = pdol?.let { requested ->
            parseDol(requested)
                .flatMap { entry -> pdolValue(entry).asIterable() }
                .toByteArray()
        } ?: ByteArray(0)
        val gpoData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
        return byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, gpoData.size.toByte()) +
            gpoData +
            byteArrayOf(0x00)
    }

    private fun pdolValue(entry: DolEntry): ByteArray {
        val value = when (entry.tag.uppercase()) {
            "9F66" -> "36000000".hexToBytes()
            "9F02" -> "000000000000".hexToBytes()
            "9F03" -> "000000000000".hexToBytes()
            "9F1A" -> "0156".hexToBytes()
            "95" -> "0000000000".hexToBytes()
            "5F2A" -> "0156".hexToBytes()
            "9A" -> SimpleDateFormat("yyMMdd", Locale.US).format(Date()).hexToBytes()
            "9C" -> byteArrayOf(0x00)
            "9F37" -> ByteArray(4).also { secureRandom.nextBytes(it) }
            "9F35" -> byteArrayOf(0x22)
            "9F34" -> "000000".hexToBytes()
            else -> ByteArray(entry.length)
        }
        return value.fitToLength(entry.length)
    }

    private fun ByteArray.fitToLength(length: Int): ByteArray =
        when {
            size == length -> this
            size > length -> copyOfRange(0, length)
            else -> this + ByteArray(length - size)
        }

    private fun extractAfl(gpoTlvs: List<Tlv>): ByteArray? {
        gpoTlvs.findFirst("94")?.value?.let { return it }
        val format80 = gpoTlvs.findFirst("80")?.value ?: return null
        if (format80.size <= 2) return null
        return format80.copyOfRange(2, format80.size)
    }

    private fun recordsFromAfl(afl: ByteArray): List<AflRecord> {
        val records = mutableListOf<AflRecord>()
        var offset = 0
        while (offset + 3 < afl.size) {
            val sfi = (afl[offset].toInt() and 0xF8) ushr 3
            val first = afl[offset + 1].toInt() and 0xFF
            val last = afl[offset + 2].toInt() and 0xFF
            if (sfi in 1..30 && first in 1..last) {
                for (record in first..last) {
                    records += AflRecord(sfi = sfi, number = record)
                }
            }
            offset += 4
        }
        return records
    }

    private fun readRecord(sfi: Int, record: Int): ByteArray =
        byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00)

    private fun failure(stage: String, debugTags: List<DebugTag>, trace: List<String>): EmvProbeResult =
        EmvProbeResult(
            success = false,
            failureStage = stage,
            debugTags = debugTags,
            apduTrace = trace,
        )

    private fun organizationFromAidOrBin(aid: String, pan: String?): String {
        val byAid = organizationFromAid(aid)
        return if (byAid != "Unknown") byAid else organizationFromPanPrefix(pan)
    }

    private fun aidSelectionRank(aid: String): Int =
        when {
            aid.startsWith("A000000333", ignoreCase = true) -> 0
            aid.startsWith("A000000003", ignoreCase = true) -> 0
            aid.startsWith("A000000004", ignoreCase = true) -> 0
            aid.startsWith("A000000790", ignoreCase = true) -> 0
            aid.startsWith("A000000025", ignoreCase = true) -> 0
            aid.startsWith("A000000065", ignoreCase = true) -> 0
            else -> 1
        }

    private fun organizationFromAid(aid: String): String =
        when {
            aid.startsWith("A000000333", ignoreCase = true) -> "UnionPay"
            aid.startsWith("A000000003", ignoreCase = true) -> "Visa"
            aid.startsWith("A000000004", ignoreCase = true) -> "Mastercard"
            aid.startsWith("A000000025", ignoreCase = true) -> "AMEX"
            aid.startsWith("A000000790", ignoreCase = true) -> "AMEX"
            aid.startsWith("A000000065", ignoreCase = true) -> "JCB"
            else -> "Unknown"
        }

    private fun organizationFromPanPrefix(pan: String?): String {
        if (pan.isNullOrBlank()) return "Unknown"
        val prefix2 = pan.take(2).toIntOrNull()
        val prefix4 = pan.take(4).toIntOrNull()
        val prefix6 = pan.take(6).toIntOrNull()
        return when {
            pan.startsWith("4") -> "Visa"
            prefix2 in 51..55 -> "Mastercard"
            prefix4 in 2221..2720 -> "Mastercard"
            pan.startsWith("34") || pan.startsWith("37") -> "AMEX"
            prefix4 in 3528..3589 -> "JCB"
            prefix6 in 620000..629999 || prefix2 == 62 -> "UnionPay"
            prefix6 in 810000..819999 -> "UnionPay"
            else -> "Unknown"
        }
    }

    private fun inferCardKind(aid: String, names: List<String>): String {
        val text = (names.joinToString(" ") + " " + aid).uppercase()
        return when {
            listOf("DEBIT", "借记", "DEB").any { text.contains(it) } -> "Debit"
            listOf("CREDIT", "贷记", "CRED").any { text.contains(it) } -> "Credit"
            text.contains("A0000003330101") -> "Debit"
            text.contains("A0000003330102") -> "Credit"
            text.contains("A000000025010502") || text.contains("A000000790010502") -> "Credit"
            text.contains("A000000025010602") || text.contains("A000000790010602") -> "Debit"
            else -> "Unknown"
        }
    }

    private fun shortAid(aid: String): String =
        if (aid.length <= 12) aid else "${aid.take(10)}..."

    private fun ByteArray.toAscii(): String =
        map { byte ->
            val value = byte.toInt() and 0xFF
            if (value in 0x20..0x7E) value.toChar() else ' '
        }.joinToString("")
            .trim()
            .takeIf { it.isNotBlank() }
            .orEmpty()

    private data class AflRecord(val sfi: Int, val number: Int)

    private data class ApduResponse(val data: ByteArray, val sw1: Int, val sw2: Int) {
        val sw: Int = (sw1 shl 8) or sw2
        val swHex: String = "%04X".format(sw)
        val isSuccess: Boolean = sw == 0x9000

        companion object {
            fun from(bytes: ByteArray): ApduResponse {
                require(bytes.size >= 2) { "APDU response shorter than status word." }
                val dataEnd = bytes.size - 2
                return ApduResponse(
                    data = bytes.copyOfRange(0, dataEnd),
                    sw1 = bytes[dataEnd].toInt() and 0xFF,
                    sw2 = bytes[dataEnd + 1].toInt() and 0xFF,
                )
            }
        }
    }
}

object TagDictionary {
    fun name(tag: String): String =
        when (tag.uppercase()) {
            "4F" -> "AID"
            "50" -> "Application Label"
            "57" -> "Track 2 Equivalent Data"
            "5A" -> "PAN"
            "5F24" -> "Application Expiration Date"
            "5F2A" -> "Transaction Currency Code"
            "6F" -> "FCI Template"
            "70" -> "EMV Proprietary Template"
            "77" -> "Response Message Template Format 2"
            "80" -> "Response Message Template Format 1"
            "82" -> "Application Interchange Profile"
            "84" -> "DF Name"
            "87" -> "Application Priority Indicator"
            "88" -> "Short File Identifier"
            "8C" -> "CDOL1"
            "8D" -> "CDOL2"
            "8E" -> "CVM List"
            "94" -> "Application File Locator"
            "9F10" -> "Issuer Application Data"
            "9F12" -> "Application Preferred Name"
            "9F26" -> "Application Cryptogram"
            "9F27" -> "Cryptogram Information Data"
            "9F36" -> "Application Transaction Counter"
            "9F38" -> "PDOL"
            "9F42" -> "Application Currency Code"
            "9F4A" -> "SDA Tag List"
            "A5" -> "FCI Proprietary Template"
            "BF0C" -> "FCI Issuer Discretionary Data"
            else -> "Unknown"
        }
}
