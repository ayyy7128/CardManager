package com.cardmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 中国交易日判断服务
 * API: https://timor.tech/api/holiday/year/{year}
 * 返回该年所有节假日/补班信息，本地缓存7天
 */
object TradingDayService {
    private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    // 节假日缓存：年份 -> Set<"yyyy-MM-dd">（休息日）
    private val holidayCache = mutableMapOf<Int, Set<String>>()
    // 补班缓存：年份 -> Set<"yyyy-MM-dd">（周末但需要上班的日期）
    private val workdayCache = mutableMapOf<Int, Set<String>>()
    private val memoryLoadedAt = mutableMapOf<Int, Long>()

    /**
     * 判断某一天是否是 A 股交易日
     * 规则：A 股交易日按工作日且非法定假日处理。周末补班日通常不开市，不计为交易日。
     */
    fun isTradingDay(date: LocalDate): Boolean {
        val dateStr = date.toString()
        val year = date.year
        val dow = date.dayOfWeek

        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false
        }
        // 工作日但是法定假日
        if (holidayCache[year]?.contains(dateStr) == true) return false
        return true
    }

    /**
     * 预加载某年的节假日数据
     */
    suspend fun loadYear(year: Int, context: Context) {
        if (isLoaded(year)) return

        // 先尝试从本地持久化缓存读取
        val repo = AppRepository(AppDatabase.get(context))
        val cacheKey = "holidays_$year"
        val cacheTimeKey = "holidays_${year}_updatedAt"
        val cached = repo.getSetting(cacheKey, "")
        val cachedAt = repo.getSetting(cacheTimeKey, "0").toLongOrNull() ?: 0L
        val cacheIsFresh = year < LocalDate.now().year ||
            (cachedAt > 0L && System.currentTimeMillis() - cachedAt <= CACHE_TTL_MS)
        if (cached.isNotEmpty() && cacheIsFresh && parseCached(year, cached)) {
            return
        }

        // 从网络获取
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://timor.tech/api/holiday/year/$year/")
                conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "CardManager/1.0")
                }
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                parseAndCache(year, response, repo, cacheKey, cacheTimeKey)
            } catch (e: Exception) {
                // 网络失败优先沿用旧缓存；没有旧缓存时才使用固定规则兜底。
                if (cached.isEmpty() || !parseCached(year, cached)) applyFallback(year)
            } finally {
                conn?.disconnect()
            }
        }
    }

    private suspend fun parseAndCache(
        year: Int,
        json: String,
        repo: AppRepository,
        cacheKey: String,
        cacheTimeKey: String
    ) {
        val root = JSONObject(json)
        val holidays = mutableSetOf<String>()
        val workdays = mutableSetOf<String>()
        // timor.tech 返回格式: { "code":0, "holiday": { "01-01": { "holiday":true, "name":"元旦", "wage":3 } } }
        val holidayObj = root.optJSONObject("holiday")
            ?: throw Exception("交易日接口返回格式错误")
        holidayObj.keys().forEach { key ->
            val item = holidayObj.getJSONObject(key)
            val fullDate = "$year-${key.replace("/", "-")}"
            // holiday=true 表示是法定节假日休息日
            // holiday=false 表示是补班（调休工作日）
            if (item.optBoolean("holiday", false)) {
                holidays.add(fullDate)
            } else {
                workdays.add(fullDate)
            }
        }
        holidayCache[year] = holidays
        workdayCache[year] = workdays
        memoryLoadedAt[year] = System.currentTimeMillis()
        repo.setSetting(cacheKey, "H:${holidays.joinToString(",")}|W:${workdays.joinToString(",")}")
        repo.setSetting(cacheTimeKey, System.currentTimeMillis().toString())
    }

    private fun parseCached(year: Int, data: String): Boolean {
        return try {
            if (!data.startsWith("H:") || "|W:" !in data) return false
            val parts = data.split("|")
            val h = parts.getOrNull(0)?.removePrefix("H:")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val w = parts.getOrNull(1)?.removePrefix("W:")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            holidayCache[year] = h
            workdayCache[year] = w
            memoryLoadedAt[year] = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applyFallback(year: Int) {
        // 不含补班数据，只标记固定节假日（兜底）
        val fixed = setOf("$year-01-01", "$year-05-01", "$year-10-01", "$year-10-02", "$year-10-03")
        if (!holidayCache.containsKey(year)) holidayCache[year] = fixed
        if (!workdayCache.containsKey(year)) workdayCache[year] = emptySet()
        memoryLoadedAt[year] = System.currentTimeMillis()
    }

    fun isLoaded(year: Int): Boolean {
        if (!holidayCache.containsKey(year)) return false
        if (year < LocalDate.now().year) return true
        val loadedAt = memoryLoadedAt[year] ?: return false
        return System.currentTimeMillis() - loadedAt <= CACHE_TTL_MS
    }
}
