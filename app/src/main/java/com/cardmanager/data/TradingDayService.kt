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

    // 节假日缓存：年份 -> Set<"yyyy-MM-dd">（休息日）
    private val holidayCache = mutableMapOf<Int, Set<String>>()
    // 补班缓存：年份 -> Set<"yyyy-MM-dd">（周末但需要上班的日期）
    private val workdayCache = mutableMapOf<Int, Set<String>>()

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
        if (holidayCache.containsKey(year)) return  // 已缓存

        // 先尝试从本地持久化缓存读取
        val repo = AppRepository(AppDatabase.get(context))
        val cached = repo.getSetting("holidays_$year", "")
        if (cached.isNotEmpty()) {
            parseCached(year, cached)
            return
        }

        // 从网络获取
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://timor.tech/api/holiday/year/$year/")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "CardManager/1.0")
                }
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()
                parseAndCache(year, response, repo)
            } catch (e: Exception) {
                // 网络失败时使用规则兜底（只排除固定节假日）
                applyFallback(year)
            }
        }
    }

    private suspend fun parseAndCache(year: Int, json: String, repo: AppRepository) {
        try {
            val root = JSONObject(json)
            val holidays = mutableSetOf<String>()
            val workdays = mutableSetOf<String>()
            // timor.tech 返回格式: { "code":0, "holiday": { "01-01": { "holiday":true, "name":"元旦", "wage":3 } } }
            val holidayObj = root.optJSONObject("holiday") ?: return
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
            repo.setSetting("holidays_$year", "H:${holidays.joinToString(",")}|W:${workdays.joinToString(",")}")
        } catch (e: Exception) {
            applyFallback(year)
        }
    }

    private fun parseCached(year: Int, data: String) {
        try {
            val parts = data.split("|")
            val h = parts.getOrNull(0)?.removePrefix("H:")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val w = parts.getOrNull(1)?.removePrefix("W:")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            holidayCache[year] = h
            workdayCache[year] = w
        } catch (e: Exception) {
            applyFallback(year)
        }
    }

    private fun applyFallback(year: Int) {
        // 不含补班数据，只标记固定节假日（兜底）
        val fixed = setOf("$year-01-01", "$year-05-01", "$year-10-01", "$year-10-02", "$year-10-03")
        if (!holidayCache.containsKey(year)) holidayCache[year] = fixed
        if (!workdayCache.containsKey(year)) workdayCache[year] = emptySet()
    }

    fun isLoaded(year: Int) = holidayCache.containsKey(year)
}
