package com.cardmanager.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object AssetPlanCodec {
    fun encodeRatePlans(items: List<AssetRatePlan>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("startDate", it.startDate).put("amount", it.amount)) }
    }.toString()

    fun decodeRatePlans(raw: String): List<AssetRatePlan> = decodeArray(raw) { o ->
        AssetRatePlan(o.optString("startDate"), o.optDouble("amount", 0.0))
    }

    fun encodeAdjustments(items: List<AssetAdjustment>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("date", it.date).put("amount", it.amount).put("note", it.note)) }
    }.toString()

    fun decodeAdjustments(raw: String): List<AssetAdjustment> = decodeArray(raw) { o ->
        AssetAdjustment(o.optString("date"), o.optDouble("amount", 0.0), o.optString("note"))
    }

    fun encodeOverrides(items: List<AssetOverrideLog>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject()
                .put("date", it.date)
                .put("amount", it.amount)
                .put("status", it.status)
                .put("note", it.note)
                .put("isDeleted", it.isDeleted)
                .put("type", it.type))
        }
    }.toString()

    fun decodeOverrides(raw: String): List<AssetOverrideLog> = decodeArray(raw) { o ->
        AssetOverrideLog(
            date = o.optString("date"),
            amount = o.optDouble("amount", 0.0),
            status = o.optString("status"),
            note = o.optString("note"),
            isDeleted = o.optBoolean("isDeleted", false),
            type = o.optString("type")
        )
    }

    fun firstRatePlan(): String =
        encodeRatePlans(listOf(AssetRatePlan(LocalDate.now().toString(), 0.0)))

    private fun <T> decodeArray(raw: String, mapper: (JSONObject) -> T): List<T> =
        runCatching {
            val arr = JSONArray(raw.ifBlank { "[]" })
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(mapper(it)) }
                }
            }
        }.getOrDefault(emptyList())
}

object AssetCalculator {
    fun calc(plan: AssetPlan, today: LocalDate = LocalDate.now()): AssetCalcResult {
        if (plan.status == AssetPlanStatus.STOPPED) {
            return AssetCalcResult(plan.frozenAmount, emptyList())
        }
        val logs = mutableListOf<AssetTransactionLog>()
        val initialDateText = plan.initialDate.ifBlank { plan.startDate.ifBlank { today.toString() } }
        val initialIsEffective = parseDate(initialDateText)?.isAfter(today) != true
        var total = if (initialIsEffective) plan.initialCapital else 0.0
        if (initialIsEffective && plan.initialCapital != 0.0) {
            logs += AssetTransactionLog(
                date = initialDateText,
                amount = plan.initialCapital,
                status = "初始资金",
                currency = plan.currency,
                type = AssetLogType.INITIAL
            )
        }

        val overrides = AssetPlanCodec.decodeOverrides(plan.overridesJson)
        fun overrideFor(date: String, type: String): AssetOverrideLog? =
            overrides.firstOrNull { it.date == date && it.type == type }
                ?: overrides.firstOrNull { it.date == date && it.type.isBlank() }
        val ratePlans = AssetPlanCodec.decodeRatePlans(plan.ratePlansJson)
            .mapNotNull { rp -> parseDate(rp.startDate)?.let { it to rp } }
            .sortedBy { it.first }
        val isSingle = plan.cycleDays == 0 && plan.monthlyDay == 0 && plan.weeklyDay == 0
        if (ratePlans.isNotEmpty() && isSingle) {
            val firstDate = ratePlans.first().first
            val firstPlan = ratePlans.first().second
            if (!firstDate.isAfter(today)) {
                val firstDateText = firstDate.toString()
                val override = overrideFor(firstDateText, AssetLogType.PERIODIC)
                when {
                    override != null && override.isDeleted -> Unit
                    override != null -> {
                        total += override.amount
                        logs += AssetTransactionLog(firstDateText, override.amount, override.status.ifBlank { "单次投入" }, plan.currency, AssetLogType.PERIODIC, override.note)
                    }
                    firstPlan.amount != 0.0 -> {
                        total += firstPlan.amount
                        logs += AssetTransactionLog(firstDateText, firstPlan.amount, "单次投入", plan.currency, AssetLogType.PERIODIC)
                    }
                }
            }
        } else if (ratePlans.isNotEmpty() && (plan.cycleDays > 0 || plan.monthlyDay > 0 || plan.weeklyDay > 0)) {
            var cursor = parseDate(plan.startDate).orElse(ratePlans.first().first)
            val firstDate = ratePlans.first().first
            if (cursor.isBefore(firstDate)) cursor = firstDate
            var postponedAmount = 0.0

            if (plan.includeFirstDay && !firstDate.isAfter(today)) {
                val firstPlan = ratePlans.first().second
                val firstDateText = firstDate.toString()
                val override = overrideFor(firstDateText, AssetLogType.PERIODIC)
                when {
                    override != null && override.isDeleted -> Unit
                    override != null -> {
                        total += override.amount
                        logs += AssetTransactionLog(firstDateText, override.amount, override.status.ifBlank { "定投成功" }, plan.currency, AssetLogType.PERIODIC, override.note)
                    }
                    else -> {
                        total += firstPlan.amount
                        logs += AssetTransactionLog(firstDateText, firstPlan.amount, "定投成功", plan.currency, AssetLogType.PERIODIC)
                    }
                }
                cursor = firstDate.plusDays(1)
            }

            while (!cursor.isAfter(today)) {
                if (!plan.includeFirstDay && cursor == firstDate) {
                    cursor = cursor.plusDays(1)
                    continue
                }
                val isRunDay = isRunDay(plan, cursor)
                val activePlan = ratePlans.lastOrNull { it.first <= cursor }?.second
                val nonTradingDay = plan.skipWeekends && !TradingDayService.isTradingDay(cursor)
                val tradingDay = !nonTradingDay

                if (isRunDay && activePlan != null) {
                    val dateText = cursor.toString()
                    val override = overrideFor(dateText, AssetLogType.PERIODIC)
                    if (override != null) {
                        if (!override.isDeleted) {
                            total += override.amount
                            logs += AssetTransactionLog(dateText, override.amount, override.status.ifBlank { "定投成功" }, plan.currency, AssetLogType.PERIODIC, override.note)
                        }
                    } else if (!tradingDay) {
                        if (plan.postponeNonTrading) {
                            postponedAmount += activePlan.amount
                        } else {
                            logs += AssetTransactionLog(
                                cursor.toString(),
                                0.0,
                                "非交易日跳过",
                                plan.currency,
                                AssetLogType.SKIP_WEEKEND
                            )
                        }
                    } else {
                        total += activePlan.amount
                        logs += AssetTransactionLog(cursor.toString(), activePlan.amount, "定投成功", plan.currency, AssetLogType.PERIODIC)
                    }
                }
                if (plan.postponeNonTrading && postponedAmount > 0.0 && tradingDay) {
                    val dateText = cursor.toString()
                    val override = overrideFor(dateText, AssetLogType.POSTPONED)
                    if (override != null) {
                        if (!override.isDeleted) {
                            total += override.amount
                            logs += AssetTransactionLog(dateText, override.amount, override.status.ifBlank { "顺延补单" }, plan.currency, AssetLogType.POSTPONED, override.note)
                        }
                    } else {
                        total += postponedAmount
                        logs += AssetTransactionLog(dateText, postponedAmount, "顺延补单", plan.currency, AssetLogType.POSTPONED)
                    }
                    postponedAmount = 0.0
                }
                cursor = cursor.plusDays(1)
            }
        }

        AssetPlanCodec.decodeAdjustments(plan.adjustmentsJson)
            .filterNot { parseDate(it.date)?.isAfter(today) == true }
            .forEach { adj ->
            total += adj.amount
            logs += AssetTransactionLog(adj.date, adj.amount, "资金调整", plan.currency, AssetLogType.ADJUSTMENT, adj.note)
        }
        return AssetCalcResult(total, logs.sortedBy { it.date })
    }

    fun displayAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else DecimalFormat("#,##0.##").format(amount)

    fun formatMoney(amount: Double, currency: String): String {
        val symbol = when (currency.uppercase(Locale.ROOT)) {
            "USD" -> "$"
            "HKD" -> "HK$"
            else -> "¥"
        }
        return symbol + DecimalFormat("#,##0.00").format(amount)
    }

    fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

    private fun isRunDay(plan: AssetPlan, date: LocalDate): Boolean =
        when {
            plan.monthlyDay > 0 -> {
                val maxDay = date.lengthOfMonth()
                if (plan.monthlyDay <= maxDay) date.dayOfMonth == plan.monthlyDay
                else !plan.skipMissingMonthlyDate && date.dayOfMonth == maxDay
            }
            plan.weeklyDay > 0 -> date.dayOfWeek.value == plan.weeklyDay.coerceIn(1, 7)
            plan.cycleDays == 1 -> true
            plan.cycleDays > 1 -> {
                val start = parseDate(plan.startDate) ?: date
                val diff = java.time.temporal.ChronoUnit.DAYS.between(start, date)
                diff > 0 && diff % plan.cycleDays == 0L
            }
            else -> false
        }

    private fun LocalDate?.orElse(fallback: LocalDate): LocalDate = this ?: fallback
}
