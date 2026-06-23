package com.cardmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ExchangeRateService {
    val supportedCurrencies = listOf("CNY", "HKD", "USD")
    val fallbackRates = mapOf(
        "CNY" to 1.0,
        "HKD" to 1.08,
        "USD" to 0.14
    )

    suspend fun fetchRates(): Map<String, Double> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://open.er-api.com/v6/latest/CNY").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val rates = JSONObject(raw).getJSONObject("rates")
            supportedCurrencies.associateWith { currency ->
                if (currency == "CNY") 1.0 else rates.optDouble(currency, fallbackRates[currency] ?: 1.0)
            }
        }.getOrDefault(fallbackRates)
    }

    fun sanitizeCurrency(value: String): String =
        value.uppercase(Locale.ROOT).takeIf { it in supportedCurrencies } ?: "CNY"

    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double {
        val safeFrom = sanitizeCurrency(from)
        val safeTo = sanitizeCurrency(to)
        val fromRate = rates[safeFrom] ?: fallbackRates[safeFrom] ?: 1.0
        val toRate = rates[safeTo] ?: fallbackRates[safeTo] ?: 1.0
        if (fromRate == 0.0) return amount
        return amount / fromRate * toRate
    }
}
