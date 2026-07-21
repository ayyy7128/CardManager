package com.cardmanager.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class CardCreditLimit(val currency: String, val amount: Double)

object CardCreditLimitTools {
    fun requireValid(raw: String) {
        if (raw.isBlank()) return
        val array = JSONArray(raw)
        require(array.length() <= 64) { "多币种额度数量过多" }
        val currencies = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw IllegalArgumentException("多币种额度项不是对象")
            val currency = normalizeCurrency(item.optString("currency"))
                ?: throw IllegalArgumentException("多币种额度币种无效")
            require(currencies.add(currency)) { "多币种额度包含重复币种" }
            val amount = item.optDouble("amount", Double.NaN)
            require(amount.isFinite() && amount >= 0.0) { "多币种额度金额无效" }
        }
    }

    fun decode(raw: String, fallbackCurrency: String, fallbackAmount: Double): List<CardCreditLimit> {
        val parsed = runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val currency = normalizeCurrency(item.optString("currency")) ?: continue
                    val amount = item.optDouble("amount", Double.NaN)
                    if (amount.isFinite() && amount >= 0.0) add(CardCreditLimit(currency, amount))
                }
            }
        }.getOrDefault(emptyList())
        val normalized = normalize(parsed)
        if (normalized.isNotEmpty()) return normalized
        val currency = normalizeCurrency(fallbackCurrency) ?: "CNY"
        return listOf(CardCreditLimit(currency, fallbackAmount.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0))
    }

    fun encode(items: List<CardCreditLimit>): String = JSONArray().apply {
        normalize(items).forEach { item ->
            put(JSONObject().put("currency", item.currency).put("amount", item.amount))
        }
    }.toString()

    fun effective(items: List<CardCreditLimit>): List<CardCreditLimit> =
        normalize(items).filter { it.amount > 0.0 }

    fun legacyAmount(items: List<CardCreditLimit>, cardCurrency: String): Double {
        val normalizedCurrency = normalizeCurrency(cardCurrency)
        val normalized = normalize(items)
        return normalized.firstOrNull { it.currency == normalizedCurrency }?.amount
            ?: normalized.firstOrNull()?.amount
            ?: 0.0
    }

    private fun normalize(items: List<CardCreditLimit>): List<CardCreditLimit> {
        val byCurrency = linkedMapOf<String, CardCreditLimit>()
        items.forEach { item ->
            val currency = normalizeCurrency(item.currency) ?: return@forEach
            val amount = item.amount.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: return@forEach
            byCurrency[currency] = CardCreditLimit(currency, amount)
        }
        return byCurrency.values.toList()
    }

    private fun normalizeCurrency(value: String): String? =
        value.trim().uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{3}")) }
}
