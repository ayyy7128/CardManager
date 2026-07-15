package com.cardmanager.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class CardResourceItem(
    val id: String,
    val packId: String,
    val packName: String,
    val bank: String,
    val bankEnglish: String,
    val name: String,
    val network: String,
    val cardCategory: String,
    val currency: String,
    val level: String,
    val country: String,
    val source: String,
    val imageOrientation: String,
    val imagePath: String,
    val bankLogoPath: String
)

data class CardResourcePackInfo(
    val id: String,
    val name: String,
    val version: String,
    val sourceUrl: String,
    val notice: String,
    val itemCount: Int,
    val sizeBytes: Long
)

data class CardResourceImportResult(
    val pack: CardResourcePackInfo,
    val replacedExisting: Boolean
)

object CardResourcePackManager {
    private const val FORMAT = "cardmanager-resource-pack"
    private const val FORMAT_VERSION = 1
    private const val MAX_ENTRIES = 5_000
    private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 24 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    private val packIdPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

    fun loadPacks(context: Context): List<CardResourcePackInfo> =
        packRoot(context).listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { directory ->
                runCatching {
                    val manifest = parseManifest(directory, validateImages = false)
                    manifest.toInfo(directorySize(directory))
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    fun loadItems(context: Context): List<CardResourceItem> =
        packRoot(context).listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .flatMap { directory ->
                runCatching {
                    val manifest = parseManifest(directory, validateImages = false)
                    manifest.items.map { item -> item.toPublic(manifest, directory) }
                }.getOrDefault(emptyList())
            }
            .sortedWith(compareBy<CardResourceItem> { it.bank }.thenBy { it.name })

    fun importPack(context: Context, uri: Uri): CardResourceImportResult {
        val staging = File(context.cacheDir, "card_resource_${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            extractPack(context, uri, staging)
            val manifest = parseManifest(staging, validateImages = true)
            val root = packRoot(context)
            val target = File(root, manifest.id)
            val replaced = target.exists()
            val backup = File(root, ".${manifest.id}_${UUID.randomUUID()}.old")

            if (replaced && !target.renameTo(backup)) {
                throw Exception("无法替换现有资源包")
            }
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw Exception("无法保存资源包")
            }
            if (backup.exists()) backup.deleteRecursively()
            return CardResourceImportResult(
                pack = manifest.toInfo(directorySize(target)),
                replacedExisting = replaced
            )
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun deletePack(context: Context, packId: String): Boolean {
        if (!packIdPattern.matches(packId)) return false
        val target = File(packRoot(context), packId)
        return target.exists() && target.deleteRecursively()
    }

    fun copyItemImage(context: Context, item: CardResourceItem, cardId: String): String {
        val source = File(item.imagePath)
        if (!source.isFile || !isInsidePackRoot(context, source)) return ""
        return ImageStore.saveFromFile(context, source, cardId)
    }

    fun copyItemBankLogo(context: Context, item: CardResourceItem, cardId: String): String {
        if (item.bankLogoPath.isBlank()) return ""
        val source = File(item.bankLogoPath)
        if (!source.isFile || !isInsidePackRoot(context, source)) return ""
        return ImageStore.saveFromFile(context, source, "${cardId}_bank", preserveAlpha = true)
    }

    private fun extractPack(context: Context, uri: Uri, staging: File) {
        val seen = mutableSetOf<String>()
        var entries = 0
        var totalBytes = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法读取资源包")
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory) {
                    entries++
                    if (entries > MAX_ENTRIES) throw Exception("资源包文件数量过多")
                    if (!seen.add(name)) throw Exception("资源包包含重复文件：$name")
                    val target = safeEntryFile(staging, name)
                        ?: throw Exception("资源包包含无效路径")
                    when {
                        name == "manifest.json" -> {
                            val bytes = readLimited(zip, MAX_MANIFEST_BYTES, "manifest.json")
                            totalBytes += bytes.size
                            target.parentFile?.mkdirs()
                            target.writeBytes(bytes)
                        }
                        name.startsWith("images/") && isSupportedImage(name) -> {
                            target.parentFile?.mkdirs()
                            totalBytes += copyLimited(zip, target, MAX_IMAGE_BYTES, "卡面图片")
                        }
                        name.startsWith("logos/") && isSupportedImage(name) -> {
                            target.parentFile?.mkdirs()
                            totalBytes += copyLimited(zip, target, MAX_IMAGE_BYTES, "银行 Logo")
                        }
                        name == "NOTICE.txt" || name == "LICENSE.txt" || name == "README.txt" ||
                            name == "failed-images.json" || name == "failed-logos.json" -> {
                            val bytes = readLimited(zip, 1024 * 1024, name)
                            totalBytes += bytes.size
                            target.writeBytes(bytes)
                        }
                        else -> throw Exception("资源包包含不支持的文件：$name")
                    }
                    if (totalBytes > MAX_TOTAL_BYTES) throw Exception("资源包解压后超过 512 MB")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (!File(staging, "manifest.json").isFile) throw Exception("资源包缺少 manifest.json")
    }

    private fun parseManifest(directory: File, validateImages: Boolean): ManifestPack {
        val file = File(directory, "manifest.json")
        if (!file.isFile || file.length() > MAX_MANIFEST_BYTES) throw Exception("资源包清单无效")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        if (root.optString("format") != FORMAT || root.optInt("formatVersion") != FORMAT_VERSION) {
            throw Exception("不支持的资源包格式")
        }
        val id = root.getString("id").trim()
        if (!packIdPattern.matches(id)) throw Exception("资源包 ID 无效")
        val name = root.getString("name").trim().takeIf { it.isNotEmpty() }
            ?: throw Exception("资源包名称为空")
        val version = root.optString("version", "1").trim().take(64)
        val sourceUrl = root.optString("sourceUrl", "").trim().take(512)
        val notice = root.optString("notice", "").trim().take(2_000)
        val array = root.getJSONArray("items")
        if (array.length() > MAX_ENTRIES) throw Exception("资源包卡面数量过多")

        val ids = mutableSetOf<String>()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val itemId = item.getString("id").trim()
                if (itemId.length !in 1..128 || !ids.add(itemId)) throw Exception("资源包包含无效或重复卡面 ID")
                val image = item.getString("image").replace('\\', '/')
                val imageFile = safeEntryFile(directory, image)
                    ?.takeIf { image.startsWith("images/") && isSupportedImage(image) && it.isFile }
                    ?: throw Exception("卡面 $itemId 缺少有效图片")
                if (imageFile.length() > MAX_IMAGE_BYTES) throw Exception("卡面 $itemId 图片过大")
                if (validateImages && !isValidBitmap(imageFile)) {
                    throw Exception("卡面 $itemId 图片格式无效或像素过大")
                }
                val bankLogo = item.optString("bankLogo", "").replace('\\', '/')
                val bankLogoFile = if (bankLogo.isBlank()) {
                    null
                } else {
                    safeEntryFile(directory, bankLogo)
                        ?.takeIf { bankLogo.startsWith("logos/") && isSupportedImage(bankLogo) && it.isFile }
                        ?: throw Exception("卡面 $itemId 缺少有效银行 Logo")
                }
                if (bankLogoFile != null && bankLogoFile.length() > MAX_IMAGE_BYTES) {
                    throw Exception("卡面 $itemId 银行 Logo 过大")
                }
                if (validateImages && bankLogoFile != null && !isValidBitmap(bankLogoFile)) {
                    throw Exception("卡面 $itemId 银行 Logo 格式无效或像素过大")
                }
                val bank = item.optString("bank", "").trim().take(120)
                if (bank.isBlank()) throw Exception("卡面 $itemId 缺少银行名称")
                add(
                    ManifestItem(
                        id = itemId,
                        bank = bank,
                        bankEnglish = item.optString("bankEnglish", "").trim().take(160),
                        name = item.optString("name", "").trim().take(160),
                        network = normalizeNetwork(item.optString("network", "其他")),
                        cardCategory = normalizeCategory(item.optString("cardCategory", "")),
                        currency = normalizeCurrency(item.optString("currency", "CNY")),
                        level = item.optString("level", "").trim().take(80),
                        country = item.optString("country", "").trim().take(80),
                        source = item.optString("source", "").trim().take(120),
                        imageOrientation = if (item.optString("imageOrientation") == "vertical") "vertical" else "horizontal",
                        image = image,
                        bankLogo = bankLogo
                    )
                )
            }
        }
        if (items.isEmpty()) throw Exception("资源包中没有卡面")
        return ManifestPack(id, name, version, sourceUrl, notice, items)
    }

    private fun normalizeNetwork(value: String): String = when (value.trim().lowercase()) {
        "unionpay", "银联" -> "银联"
        "visa" -> "Visa"
        "mastercard", "master card" -> "Mastercard"
        "amex", "american express" -> "AMEX"
        "jcb" -> "JCB"
        "discover" -> "Discover"
        else -> "其他"
    }

    private fun normalizeCategory(value: String): String = when (value.trim().lowercase()) {
        "credit", "信用卡", "贷记卡" -> "信用卡"
        "debit", "prepaid", "储蓄卡", "借记卡", "预付费" -> "储蓄卡"
        else -> ""
    }

    private fun normalizeCurrency(value: String): String =
        value.trim().uppercase().takeIf { it.matches(Regex("[A-Z]{3}")) } ?: "CNY"

    private fun ManifestPack.toInfo(sizeBytes: Long) = CardResourcePackInfo(
        id = id,
        name = name,
        version = version,
        sourceUrl = sourceUrl,
        notice = notice,
        itemCount = items.size,
        sizeBytes = sizeBytes
    )

    private fun ManifestItem.toPublic(pack: ManifestPack, directory: File) = CardResourceItem(
        id = id,
        packId = pack.id,
        packName = pack.name,
        bank = bank,
        bankEnglish = bankEnglish,
        name = name,
        network = network,
        cardCategory = cardCategory,
        currency = currency,
        level = level,
        country = country,
        source = source,
        imageOrientation = imageOrientation,
        imagePath = File(directory, image).absolutePath,
        bankLogoPath = bankLogo.takeIf { it.isNotBlank() }
            ?.let { File(directory, it).absolutePath }
            .orEmpty()
    )

    private fun packRoot(context: Context): File =
        File(context.filesDir, "card_resource_packs").also { it.mkdirs() }

    private fun safeEntryFile(parent: File, name: String): File? {
        if (name.isBlank() || name.startsWith('/') || name.contains(':')) return null
        if (name.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val base = parent.canonicalFile
        val target = File(base, name).canonicalFile
        return target.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun isSupportedImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in imageExtensions

    private fun isValidBitmap(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        return width in 1..20_000 && height in 1..20_000 &&
            width.toLong() * height.toLong() <= 100_000_000L
    }

    private fun isInsidePackRoot(context: Context, file: File): Boolean = runCatching {
        val base = packRoot(context).canonicalFile
        file.canonicalFile.path.startsWith(base.path + File.separator)
    }.getOrDefault(false)

    private fun readLimited(input: InputStream, maxBytes: Int, label: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw Exception("${label}过大")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, target: File, maxBytes: Int, label: String): Long {
        var total = 0L
        try {
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw Exception("${label}过大")
                    output.write(buffer, 0, count)
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return total
    }

    private fun directorySize(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private data class ManifestPack(
        val id: String,
        val name: String,
        val version: String,
        val sourceUrl: String,
        val notice: String,
        val items: List<ManifestItem>
    )

    private data class ManifestItem(
        val id: String,
        val bank: String,
        val bankEnglish: String,
        val name: String,
        val network: String,
        val cardCategory: String,
        val currency: String,
        val level: String,
        val country: String,
        val source: String,
        val imageOrientation: String,
        val image: String,
        val bankLogo: String
    )
}
