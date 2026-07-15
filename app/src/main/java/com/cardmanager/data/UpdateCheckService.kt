package com.cardmanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val title: String,
    val notes: String,
    val downloadUrl: String,
    val publishedAt: String
)

enum class UpdateCheckError {
    NETWORK,
    RATE_LIMIT,
    NO_RELEASE,
    INVALID_RESPONSE
}

sealed class UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Failed(val reason: UpdateCheckError) : UpdateCheckResult()
    data object Skipped : UpdateCheckResult()
}

object UpdateCheckService {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/ayyy7128/CardManager/releases/latest"
    private const val PREFS = "cm_update_check"
    private const val KEY_LAST_AUTO_CHECK = "last_auto_check"
    private const val KEY_LAST_PROMPTED_VERSION = "last_prompted_version"
    private const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!force) {
                val lastCheck = preferences.getLong(KEY_LAST_AUTO_CHECK, 0L)
                if (lastCheck > 0L && now - lastCheck in 0 until AUTO_CHECK_INTERVAL_MS) {
                    return@withContext UpdateCheckResult.Skipped
                }
                preferences.edit().putLong(KEY_LAST_AUTO_CHECK, now).apply()
            }

            val currentVersion = currentVersion(appContext)
            val connection = runCatching {
                (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "CardManager-Android/$currentVersion")
                }
            }.getOrElse { return@withContext UpdateCheckResult.Failed(UpdateCheckError.NETWORK) }

            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            reader.readText().takeIf { it.length <= 1024 * 1024 }
                        } ?: return@withContext UpdateCheckResult.Failed(UpdateCheckError.INVALID_RESPONSE)
                        val release = runCatching { parseRelease(raw) }.getOrNull()
                            ?: return@withContext UpdateCheckResult.Failed(UpdateCheckError.INVALID_RESPONSE)
                        if (isNewer(release.versionName, currentVersion)) {
                            UpdateCheckResult.Available(release)
                        } else {
                            UpdateCheckResult.UpToDate(currentVersion)
                        }
                    }
                    HttpURLConnection.HTTP_FORBIDDEN, 429 ->
                        UpdateCheckResult.Failed(UpdateCheckError.RATE_LIMIT)
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        UpdateCheckResult.Failed(UpdateCheckError.NO_RELEASE)
                    else -> UpdateCheckResult.Failed(UpdateCheckError.NETWORK)
                }
            } catch (_: Exception) {
                UpdateCheckResult.Failed(UpdateCheckError.NETWORK)
            } finally {
                connection.disconnect()
            }
        }

    fun shouldPromptAutomatically(context: Context, versionName: String): Boolean {
        val prompted = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PROMPTED_VERSION, "")
            .orEmpty()
        return prompted != normalizeVersion(versionName)
    }

    fun markAutomaticallyPrompted(context: Context, versionName: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PROMPTED_VERSION, normalizeVersion(versionName))
            .apply()
    }

    fun openDownload(context: Context, downloadUrl: String): Boolean {
        val safeUrl = validatedDownloadUrl(downloadUrl) ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun currentVersion(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("").ifBlank { "0.0.0" }

    internal fun isNewer(latest: String, current: String): Boolean {
        val latestVersion = parseVersion(latest) ?: return false
        val currentVersion = parseVersion(current) ?: return false
        val maxParts = maxOf(latestVersion.parts.size, currentVersion.parts.size, 3)
        for (index in 0 until maxParts) {
            val latestPart = latestVersion.parts.getOrElse(index) { 0 }
            val currentPart = currentVersion.parts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return currentVersion.preRelease && !latestVersion.preRelease
    }

    private fun parseRelease(raw: String): ReleaseInfo {
        val json = JSONObject(raw)
        val tagName = json.getString("tag_name").trim()
        require(parseVersion(tagName) != null)
        val downloadUrl = selectApkDownloadUrl(json)
            ?: throw IllegalArgumentException("Release does not contain a valid APK asset")
        return ReleaseInfo(
            versionName = normalizeVersion(tagName),
            title = json.optString("name").trim().ifBlank { tagName },
            notes = json.optString("body").trim(),
            downloadUrl = downloadUrl,
            publishedAt = json.optString("published_at").trim()
        )
    }

    private fun selectApkDownloadUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length())
            .mapNotNull { index ->
                val asset = assets.optJSONObject(index) ?: return@mapNotNull null
                val name = asset.optString("name").trim()
                if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                val downloadUrl = validatedDownloadUrl(
                    asset.optString("browser_download_url").trim()
                ) ?: return@mapNotNull null
                val normalizedName = name.lowercase()
                if ("debug" in normalizedName || "unsigned" in normalizedName) {
                    return@mapNotNull null
                }
                val score = when {
                    "release" in normalizedName && "cardmanager" in normalizedName -> 4
                    "release" in normalizedName || "cardmanager" in normalizedName -> 3
                    "universal" in normalizedName -> 2
                    else -> 1
                }
                score to downloadUrl
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private data class ParsedVersion(val parts: List<Int>, val preRelease: Boolean)

    private fun parseVersion(value: String): ParsedVersion? {
        val normalized = normalizeVersion(value)
        val numeric = normalized.substringBefore('-').substringBefore('+')
        val parts = numeric.split('.').map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty()) return null
        return ParsedVersion(parts, preRelease = '-' in normalized)
    }

    private fun normalizeVersion(value: String): String = value.trim().removePrefix("v").removePrefix("V")

    private fun validatedDownloadUrl(value: String): String? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        return value.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                path.startsWith("/ayyy7128/CardManager/releases/download/") &&
                path.endsWith(".apk", ignoreCase = true) &&
                uri.query.isNullOrEmpty() &&
                uri.fragment.isNullOrEmpty()
        }
    }
}
