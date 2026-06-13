package com.cardmanager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

object ImageStore {
    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "card_logos").also { it.mkdirs() }

    /** 从 URI（图库选图）保存缩略图，返回文件路径 */
    fun saveFromUri(ctx: Context, uri: Uri, cardId: String): String {
        val input = ctx.contentResolver.openInputStream(uri) ?: return ""
        val bmp = BitmapFactory.decodeStream(input)
        input.close()
        return saveBitmap(ctx, bmp, cardId)
    }

    /** 从 base64 字符串（PWA 导入）保存，返回文件路径 */
    fun saveFromBase64(ctx: Context, b64: String, cardId: String): String {
        return try {
            // 去掉 data:image/xxx;base64, 前缀
            val raw = if (b64.contains(",")) b64.substringAfter(",") else b64
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
            saveBitmap(ctx, bmp, cardId)
        } catch (e: Exception) { "" }
    }

    private fun saveBitmap(ctx: Context, bmp: Bitmap, cardId: String): String {
        val targetW = minOf(1200, bmp.width)
        val targetH = (bmp.height.toFloat() * targetW / bmp.width).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        val file = File(dir(ctx), "${cardId}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 97, out)
        }
        bitmapCache.put(cacheKey(file.absolutePath, 0), scaled)
        return file.absolutePath
    }

    private fun cacheKey(path: String, maxDimension: Int): String =
        if (maxDimension > 0) "$path@$maxDimension" else path

    fun peek(path: String, maxDimension: Int = 0): Bitmap? {
        if (path.isEmpty()) return null
        bitmapCache.get(cacheKey(path, maxDimension))?.let { return it }
        return if (maxDimension > 0) bitmapCache.get(cacheKey(path, 0)) else null
    }

    /** 加载图片（返回 Bitmap 或 null） */
    fun load(path: String, maxDimension: Int = 0): Bitmap? {
        if (path.isEmpty()) return null
        val key = cacheKey(path, maxDimension)
        bitmapCache.get(key)?.let { return it }
        return try {
            val bmp = if (maxDimension > 0) decodeSampled(path, maxDimension) else BitmapFactory.decodeFile(path)
            bmp?.also { bitmapCache.put(key, it) }
        } catch (e: Exception) { null }
    }

    private fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val maxSource = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSource <= 0) return null
        var sample = 1
        while (maxSource / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val decodedMax = maxOf(decoded.width, decoded.height)
        if (decodedMax <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / decodedMax
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** 删除图片文件 */
    fun delete(path: String) {
        if (path.isNotEmpty()) {
            removeFromCache(path)
            File(path).delete()
        }
    }

    /** 只删除 App 自己图片目录中的文件，避免误删外部路径。 */
    fun deleteOwned(ctx: Context, path: String) {
        if (path.isEmpty()) return
        runCatching {
            val base = dir(ctx).canonicalFile
            val target = File(path).canonicalFile
            if (target.path.startsWith(base.path + File.separator)) {
                removeFromCache(target.absolutePath)
                target.delete()
            }
        }
    }

    private fun removeFromCache(path: String) {
        val snapshot = bitmapCache.snapshot().keys.toList()
        snapshot.filter { it == path || it.startsWith("$path@") }.forEach { bitmapCache.remove(it) }
    }
}
