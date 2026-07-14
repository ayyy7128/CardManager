package com.cardmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.cardmanager.EXTRA_WIDGET_ASSET_PLAN_ID
import com.cardmanager.EXTRA_WIDGET_TARGET_TAB
import com.cardmanager.MainActivity
import com.cardmanager.R
import com.cardmanager.WIDGET_TARGET_CALENDAR
import com.cardmanager.WIDGET_TARGET_CARDS
import com.cardmanager.WIDGET_TARGET_PIGGY
import com.cardmanager.data.AppDatabase
import com.cardmanager.data.AssetCalculator
import com.cardmanager.data.AssetPlan
import com.cardmanager.data.AssetPlanStatus
import com.cardmanager.data.ExchangeRateService
import com.cardmanager.data.PiggyEntry
import com.cardmanager.data.Task
import com.cardmanager.data.TaskHolidayPolicy
import com.cardmanager.data.TradingDayService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object CardWidgetUpdater {
    const val MODE_TASKS = "tasks"
    const val MODE_VAULT_TOTAL = "vault_total"
    const val MODE_ASSET_PLAN = "asset_plan"

    private const val PREFS = "cm_widgets"
    private const val KEY_MODE = "mode"
    private const val KEY_PLAN_ID = "planId"

    data class Config(val mode: String = MODE_TASKS, val planId: String = "")

    fun loadConfig(context: Context, appWidgetId: Int): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            mode = prefs.getString(key(appWidgetId, KEY_MODE), MODE_TASKS) ?: MODE_TASKS,
            planId = prefs.getString(key(appWidgetId, KEY_PLAN_ID), "") ?: ""
        )
    }

    fun saveConfig(context: Context, appWidgetId: Int, config: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(appWidgetId, KEY_MODE), config.mode)
            .putString(key(appWidgetId, KEY_PLAN_ID), config.planId)
            .apply()
    }

    fun clearConfig(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(appWidgetId, KEY_MODE))
            .remove(key(appWidgetId, KEY_PLAN_ID))
            .apply()
    }

    suspend fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val config = loadConfig(context, appWidgetId)
        val wide = manager.getAppWidgetInfo(appWidgetId)?.provider?.className?.contains("4x2") == true
        val views = buildViews(context.applicationContext, appWidgetId, config, wide)
        manager.updateAppWidget(appWidgetId, views)
    }

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = listOf(CardWidget2x2Provider::class.java, CardWidget4x2Provider::class.java)
            .flatMap { provider -> manager.getAppWidgetIds(ComponentName(appContext, provider)).asList() }
            .distinct()
        ids.forEach { updateWidget(appContext, manager, it) }
    }

    private suspend fun buildViews(context: Context, appWidgetId: Int, config: Config, wide: Boolean): RemoteViews = withContext(Dispatchers.IO) {
        val layoutId = if (wide) R.layout.widget_4x2 else R.layout.widget_2x2
        val snapshot = loadSnapshot(context, config, wide)
        val targetTab = when (config.mode) {
            MODE_TASKS -> WIDGET_TARGET_CALENDAR
            MODE_VAULT_TOTAL, MODE_ASSET_PLAN -> WIDGET_TARGET_PIGGY
            else -> WIDGET_TARGET_CARDS
        }
        val targetPlanId = config.planId.takeIf { config.mode == MODE_ASSET_PLAN && it.isNotBlank() }
        RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_title, snapshot.title)
            setTextViewText(R.id.widget_primary, snapshot.primary)
            setTextViewText(R.id.widget_secondary, snapshot.secondary)
            setTextViewText(R.id.widget_meta, snapshot.meta)
            setTextViewText(R.id.widget_list, snapshot.list)
            setTextViewTextSize(R.id.widget_primary, TypedValue.COMPLEX_UNIT_SP, primaryTextSize(snapshot.primary, wide))
            setViewVisibility(R.id.widget_list, if (snapshot.list.isBlank()) View.GONE else View.VISIBLE)
            setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent(context, appWidgetId, 1, targetTab, targetPlanId))
            setOnClickPendingIntent(R.id.widget_primary, openAppPendingIntent(context, appWidgetId, 2, targetTab, targetPlanId))
            setOnClickPendingIntent(R.id.widget_secondary, openAppPendingIntent(context, appWidgetId, 3, targetTab, targetPlanId))
            setOnClickPendingIntent(R.id.widget_meta, openAppPendingIntent(context, appWidgetId, 4, targetTab, targetPlanId))
            setOnClickPendingIntent(R.id.widget_list, openAppPendingIntent(context, appWidgetId, 5, targetTab, targetPlanId))
        }
    }

    private suspend fun loadSnapshot(context: Context, config: Config, wide: Boolean): WidgetSnapshot {
        val db = AppDatabase.get(context)
        val today = LocalDate.now()
        val settingCurrency = db.settingDao().get("vaultCurrency")?.value
        val prefsCurrency = context.getSharedPreferences("cm_settings", Context.MODE_PRIVATE)
            .getString("vaultCurrency", "CNY")
        val vaultCurrency = ExchangeRateService.sanitizeCurrency(settingCurrency ?: prefsCurrency ?: "CNY")
        val exchangeRates = ExchangeRateService.loadCachedRates(context)

        return when (config.mode) {
            MODE_VAULT_TOTAL -> {
                val entries = db.piggyDao().getAllEntries().first()
                val plans = db.assetPlanDao().getAllPlans().first()
                vaultTotalSnapshot(context, entries, plans, vaultCurrency, exchangeRates, wide)
            }
            MODE_ASSET_PLAN -> {
                val plans = db.assetPlanDao().getAllPlans().first()
                val plan = plans.firstOrNull { it.id == config.planId }
                if (plan == null) emptyProjectSnapshot(context, vaultCurrency)
                else assetPlanSnapshot(context, plan, vaultCurrency, exchangeRates, wide)
            }
            else -> {
                val tasks = db.taskDao().getAllTasks().first()
                val plans = db.assetPlanDao().getAllPlans().first()
                taskSnapshot(context, tasks, plans, today, wide)
            }
        }
    }

    private suspend fun taskSnapshot(
        context: Context,
        tasks: List<Task>,
        plans: List<AssetPlan>,
        today: LocalDate,
        wide: Boolean
    ): WidgetSnapshot {
        ensureTradingDays(context, today)
        val regularTasks = tasks
            .filterNot { it.isInvest }
            .filter { taskMatchesDate(it, today) }
            .map { it.name.ifBlank { context.getString(R.string.tab_calendar) } }
        val assetLines = plans.flatMap { plan ->
            AssetCalculator.calc(plan, today).logs
                .filter { it.date == today.toString() }
                .map { log ->
                    val name = plan.name.ifBlank { context.getString(R.string.widget_asset_project_title) }
                    "$name ${log.status}"
                }
        }
        val allLines = regularTasks + assetLines
        val countText = context.getString(R.string.widget_task_count, allLines.size)
        val firstLine = allLines.firstOrNull() ?: context.getString(R.string.widget_no_tasks)
        val listText = if (wide) allLines.drop(1).take(4).joinToString("\n") else allLines.drop(1).take(2).joinToString("\n")
        return WidgetSnapshot(
            title = context.getString(R.string.widget_tasks_title),
            primary = countText,
            secondary = firstLine,
            list = listText,
            meta = formatDate(today)
        )
    }

    private fun vaultTotalSnapshot(
        context: Context,
        entries: List<PiggyEntry>,
        plans: List<AssetPlan>,
        currency: String,
        exchangeRates: Map<String, Double>,
        wide: Boolean
    ): WidgetSnapshot {
        val manualTotal = entries
            .filter { isManualPiggyEntry(it) }
            .sumOf { ExchangeRateService.convert(it.amount, "CNY", currency, exchangeRates) }
        val includedPlans = plans.filter { it.countInTotal }
        val planRows = includedPlans.map { plan ->
            val amount = assetPlanDisplayAmount(plan)
            val converted = ExchangeRateService.convert(amount, plan.currency, currency, exchangeRates)
            plan to converted
        }
        val total = manualTotal + planRows.sumOf { it.second }
        val listText = buildList {
            add("${context.getString(R.string.widget_manual_records)} ${AssetCalculator.formatMoney(manualTotal, currency)}")
            addAll(planRows.take(if (wide) 4 else 2).map { (plan, amount) ->
                "${plan.name.ifBlank { context.getString(R.string.widget_asset_project_title) }} ${AssetCalculator.formatMoney(amount, currency)}"
            })
        }.joinToString("\n")
        return WidgetSnapshot(
            title = context.getString(R.string.widget_vault_total_title),
            primary = AssetCalculator.formatMoney(total, currency),
            secondary = context.getString(R.string.widget_project_count, includedPlans.size),
            list = listText,
            meta = context.getString(R.string.widget_updated_at, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        )
    }

    private fun assetPlanSnapshot(
        context: Context,
        plan: AssetPlan,
        vaultCurrency: String,
        exchangeRates: Map<String, Double>,
        wide: Boolean
    ): WidgetSnapshot {
        val calc = AssetCalculator.calc(plan, LocalDate.now())
        val amount = if (plan.status == AssetPlanStatus.STOPPED) {
            if (plan.countInTotal) plan.frozenAmount else 0.0
        } else {
            calc.amount
        }
        val converted = ExchangeRateService.convert(amount, plan.currency, vaultCurrency, exchangeRates)
        val status = if (plan.status == AssetPlanStatus.STOPPED) {
            context.getString(R.string.widget_asset_archived)
        } else {
            context.getString(R.string.widget_asset_running)
        }
        val detail = listOf(plan.platform, plan.category, plan.code).filter { it.isNotBlank() }.joinToString(" · ")
        val convertedLine = if (plan.currency == vaultCurrency) "" else AssetCalculator.formatMoney(converted, vaultCurrency)
        val recentLines = calc.logs
            .sortedByDescending { it.date }
            .take(if (wide) 4 else 2)
            .map { log ->
                "${shortDate(log.date)} ${log.status} ${AssetCalculator.formatMoney(log.amount, log.currency)}"
            }
        val list = buildList {
            if (convertedLine.isNotBlank()) add(convertedLine)
            addAll(recentLines)
        }.take(if (wide) 4 else 2).joinToString("\n")
        return WidgetSnapshot(
            title = plan.name.ifBlank { context.getString(R.string.widget_asset_project_title) },
            primary = AssetCalculator.formatMoney(amount, plan.currency),
            secondary = detail.ifBlank { status },
            list = list,
            meta = context.getString(R.string.widget_updated_at, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        )
    }

    private fun emptyProjectSnapshot(context: Context, currency: String): WidgetSnapshot =
        WidgetSnapshot(
            title = context.getString(R.string.widget_asset_project_title),
            primary = AssetCalculator.formatMoney(0.0, currency),
            secondary = context.getString(R.string.widget_no_asset_plan),
            list = "",
            meta = context.getString(R.string.widget_updated_at, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
        )

    private fun assetPlanDisplayAmount(plan: AssetPlan): Double =
        if (plan.status == AssetPlanStatus.STOPPED) {
            if (plan.countInTotal) plan.frozenAmount else 0.0
        } else {
            AssetCalculator.calc(plan).amount
        }

    private fun isManualPiggyEntry(entry: PiggyEntry): Boolean =
        !entry.desc.startsWith("任务同步：")

    private suspend fun ensureTradingDays(context: Context, today: LocalDate) {
        listOf(today.minusDays(7).year, today.year).distinct().forEach { year ->
            if (!TradingDayService.isLoaded(year)) TradingDayService.loadYear(year, context)
        }
    }

    private fun taskMatchesDate(task: Task, date: LocalDate): Boolean {
        if (TaskHolidayPolicy.avoidsNonTradingDays(task)) {
            if (!TradingDayService.isTradingDay(date)) return false
            if (taskBaseMatchesDate(task, date)) return true
            return taskPostponedFromDates(task, date).isNotEmpty()
        }
        return taskBaseMatchesDate(task, date)
    }

    private fun taskPostponedFromDates(task: Task, date: LocalDate): List<LocalDate> {
        if (!TaskHolidayPolicy.avoidsNonTradingDays(task) || !TradingDayService.isTradingDay(date)) return emptyList()
        val postponed = mutableListOf<LocalDate>()
        var cursor = date.minusDays(1)
        while (!TradingDayService.isTradingDay(cursor)) {
            if (taskBaseMatchesDate(task, cursor)) postponed += cursor
            cursor = cursor.minusDays(1)
        }
        return postponed
    }

    private fun taskBaseMatchesDate(task: Task, date: LocalDate): Boolean {
        val start = runCatching {
            LocalDate.parse(task.startDate.ifBlank { task.date.ifBlank { LocalDate.now().toString() } })
        }.getOrDefault(LocalDate.now())
        if (date.isBefore(start)) return false
        return when (task.freq) {
            "monthly" -> task.day == date.dayOfMonth
            "weekly" -> task.weekday == date.dayOfWeek.value
            "quarterly" -> {
                val months = task.months.split(",").mapNotNull { it.trim().toIntOrNull() }
                date.monthValue in months && task.day == date.dayOfMonth
            }
            "ndays" -> ChronoUnit.DAYS.between(start, date) % task.ndays.coerceAtLeast(1) == 0L
            "once" -> {
                val onceDate = runCatching { LocalDate.parse(task.date.ifBlank { task.startDate }) }.getOrNull()
                onceDate == date && !date.isBefore(start)
            }
            else -> false
        }
    }

    private fun formatDate(date: LocalDate): String {
        val locale = Locale.getDefault()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        return "${date.monthValue}/${date.dayOfMonth} $weekday"
    }

    private fun shortDate(value: String): String =
        runCatching {
            val date = LocalDate.parse(value)
            "${date.monthValue}/${date.dayOfMonth}"
        }.getOrDefault(value)

    private fun primaryTextSize(text: String, wide: Boolean): Float {
        val length = text.length
        val isMoney = text.contains('¥') || text.contains('$')
        return when {
            wide && length > 14 -> 21f
            wide && length > 11 -> 23f
            wide -> 27f
            isMoney && length > 9 -> 20f
            isMoney && length > 7 -> 22f
            isMoney -> 25f
            length > 11 -> 21f
            length > 9 -> 23f
            else -> 28f
        }
    }

    private fun openAppPendingIntent(
        context: Context,
        appWidgetId: Int,
        viewCode: Int,
        targetTab: String,
        targetAssetPlanId: String?
    ): PendingIntent {
        val requestCode = appWidgetId * 10 + viewCode
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.cardmanager.widget.OPEN"
            putExtra(EXTRA_WIDGET_TARGET_TAB, targetTab)
            targetAssetPlanId?.let { putExtra(EXTRA_WIDGET_ASSET_PLAN_ID, it) }
            data = Uri.parse("cardmanager://widget/$appWidgetId/$viewCode/$targetTab/${targetAssetPlanId.orEmpty()}")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun key(appWidgetId: Int, name: String): String = "${appWidgetId}_$name"
}

private data class WidgetSnapshot(
    val title: String,
    val primary: String,
    val secondary: String,
    val list: String,
    val meta: String
)
