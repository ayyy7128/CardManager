package com.cardmanager.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardmanager.R
import com.cardmanager.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class PiggyTaskSyncRule(
    val mode: String = "date",
    val date: String = LocalDate.now().toString()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(AppDatabase.get(app), app)
    private val prefs = app.getSharedPreferences("cm_settings", Context.MODE_PRIVATE)
    private val defaultTabOrder = listOf("cards", "calendar", "piggy", "data")
    private val optionalTabIds = setOf("calendar", "piggy")
    private val defaultDataOverviewIds = listOf("totalCards", "groupCount", "activeCards", "totalTasks", "savingsBalance", "abnormalCards")
    private val defaultDataChartIds = listOf("status", "composition", "category", "network", "currency", "group", "taskFrequency", "taskLink", "piggyFlow")

    val groups = repo.groups.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val cards  = repo.cards.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val piggyEntries = repo.piggyEntries.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val assetPlans = repo.assetPlans.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    private val _imageVersion = MutableStateFlow(0)
    val imageVersion: StateFlow<Int> = _imageVersion
    private val _assetTradingCalendarVersion = MutableStateFlow(0)
    val assetTradingCalendarVersion: StateFlow<Int> = _assetTradingCalendarVersion
    private var loadedAssetPlanTradingYears: Set<Int> = emptySet()

    private val _themeMode = MutableStateFlow(sanitizeThemeMode(prefs.getString("theme", "light") ?: "light"))
    val themeMode: StateFlow<String> = _themeMode
    private val _isDark = MutableStateFlow(_themeMode.value == "dark")
    val isDark: StateFlow<Boolean> = _isDark
    private val _pigCardId = MutableStateFlow("")
    val pigCardId: StateFlow<String> = _pigCardId
    private val _piggyTaskId = MutableStateFlow("")
    val piggyTaskId: StateFlow<String> = _piggyTaskId
    private val _piggySyncFromStart = MutableStateFlow(false)
    val piggySyncFromStart: StateFlow<Boolean> = _piggySyncFromStart
    private val _piggyTaskSyncRules = MutableStateFlow<Map<String, PiggyTaskSyncRule>>(emptyMap())
    val piggyTaskSyncRules: StateFlow<Map<String, PiggyTaskSyncRule>> = _piggyTaskSyncRules
    private val _vaultCurrency = MutableStateFlow(ExchangeRateService.sanitizeCurrency(prefs.getString("vaultCurrency", "CNY") ?: "CNY"))
    val vaultCurrency: StateFlow<String> = _vaultCurrency
    private val _exchangeRates = MutableStateFlow(ExchangeRateService.fallbackRates)
    val exchangeRates: StateFlow<Map<String, Double>> = _exchangeRates
    private val _preferHighRefreshRate = MutableStateFlow(prefs.getString("preferHighRefreshRate", "false").toBoolean())
    val preferHighRefreshRate: StateFlow<Boolean> = _preferHighRefreshRate
    private val _cardsPerRowPortrait = MutableStateFlow(prefs.getString("cardsPerRowPortrait", "2")?.toIntOrNull()?.coerceIn(1, 4) ?: 2)
    val cardsPerRowPortrait: StateFlow<Int> = _cardsPerRowPortrait
    private val _cardsPerRowLandscape = MutableStateFlow(prefs.getString("cardsPerRowLandscape", "3")?.toIntOrNull()?.coerceIn(1, 6) ?: 3)
    val cardsPerRowLandscape: StateFlow<Int> = _cardsPerRowLandscape
    private val _ungroupedMode = MutableStateFlow(prefs.getString("ungroupedMode", "false").toBoolean())
    val ungroupedMode: StateFlow<Boolean> = _ungroupedMode
    private val _cardGalleryMode = MutableStateFlow(prefs.getString("cardGalleryMode", "true").toBoolean())
    val cardGalleryMode: StateFlow<Boolean> = _cardGalleryMode
    private val _tabOrder = MutableStateFlow(sanitizeTabOrder(prefs.getString("tabOrder", defaultTabOrder.joinToString(",")) ?: defaultTabOrder.joinToString(",")))
    val tabOrder: StateFlow<List<String>> = _tabOrder
    private val _visibleOptionalTabs = MutableStateFlow(sanitizeVisibleOptionalTabs(prefs.getString("visibleOptionalTabs", optionalTabIds.joinToString(",")) ?: optionalTabIds.joinToString(",")))
    val visibleOptionalTabs: StateFlow<Set<String>> = _visibleOptionalTabs
    private val _visibleDataCharts = MutableStateFlow(sanitizeDataCharts(prefs.getString("visibleDataCharts", defaultDataChartIds.joinToString(",")) ?: defaultDataChartIds.joinToString(",")))
    val visibleDataCharts: StateFlow<Set<String>> = _visibleDataCharts
    private val _dataChartOrder = MutableStateFlow(sanitizeDataChartOrder(prefs.getString("dataChartOrder", defaultDataChartIds.joinToString(",")) ?: defaultDataChartIds.joinToString(",")))
    val dataChartOrder: StateFlow<List<String>> = _dataChartOrder
    private val _visibleDataOverview = MutableStateFlow(sanitizeDataOverview(prefs.getString("visibleDataOverview", defaultDataOverviewIds.joinToString(",")) ?: defaultDataOverviewIds.joinToString(",")))
    val visibleDataOverview: StateFlow<Set<String>> = _visibleDataOverview
    private val _dataOverviewOrder = MutableStateFlow(sanitizeDataOverviewOrder(prefs.getString("dataOverviewOrder", defaultDataOverviewIds.joinToString(",")) ?: defaultDataOverviewIds.joinToString(",")))
    val dataOverviewOrder: StateFlow<List<String>> = _dataOverviewOrder

    init {
        viewModelScope.launch {
            repo.migrateLegacyInvestTasksToAssetPlans()
            val theme = sanitizeThemeMode(repo.getSetting("theme", prefs.getString("theme", "light") ?: "light"))
            prefs.edit().putString("theme", theme).apply()
            _themeMode.value = theme
            _isDark.value = theme == "dark"
            _pigCardId.value = repo.getSetting("pigCard", "")
            val piggyTaskRaw = repo.getSetting("piggyTask", "")
            val legacyPiggySyncFromStart = repo.getSetting("piggySyncFromStart", "false").toBoolean()
            _piggyTaskId.value = piggyTaskRaw
            _piggySyncFromStart.value = legacyPiggySyncFromStart
            _piggyTaskSyncRules.value = parsePiggyTaskSyncRules(
                repo.getSetting("piggyTaskSyncRules", ""),
                piggyTaskRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                legacyPiggySyncFromStart
            )
            val portraitRows = repo.getSetting("cardsPerRowPortrait", prefs.getString("cardsPerRowPortrait", "2") ?: "2")
                .toIntOrNull()?.coerceIn(1, 4) ?: 2
            val landscapeRows = repo.getSetting("cardsPerRowLandscape", prefs.getString("cardsPerRowLandscape", "3") ?: "3")
                .toIntOrNull()?.coerceIn(1, 6) ?: 3
            val ungrouped = repo.getSetting("ungroupedMode", prefs.getString("ungroupedMode", "false") ?: "false").toBoolean()
            val cardGalleryMode = repo.getSetting("cardGalleryMode", prefs.getString("cardGalleryMode", "true") ?: "true").toBoolean()
            val tabOrder = sanitizeTabOrder(repo.getSetting("tabOrder", prefs.getString("tabOrder", defaultTabOrder.joinToString(",")) ?: defaultTabOrder.joinToString(",")))
            val visibleOptionalTabs = sanitizeVisibleOptionalTabs(repo.getSetting("visibleOptionalTabs", prefs.getString("visibleOptionalTabs", optionalTabIds.joinToString(",")) ?: optionalTabIds.joinToString(",")))
            val visibleDataCharts = sanitizeDataCharts(repo.getSetting("visibleDataCharts", prefs.getString("visibleDataCharts", defaultDataChartIds.joinToString(",")) ?: defaultDataChartIds.joinToString(",")))
            val dataChartOrder = sanitizeDataChartOrder(repo.getSetting("dataChartOrder", prefs.getString("dataChartOrder", defaultDataChartIds.joinToString(",")) ?: defaultDataChartIds.joinToString(",")))
            val visibleDataOverview = sanitizeDataOverview(repo.getSetting("visibleDataOverview", prefs.getString("visibleDataOverview", defaultDataOverviewIds.joinToString(",")) ?: defaultDataOverviewIds.joinToString(",")))
            val dataOverviewOrder = sanitizeDataOverviewOrder(repo.getSetting("dataOverviewOrder", prefs.getString("dataOverviewOrder", defaultDataOverviewIds.joinToString(",")) ?: defaultDataOverviewIds.joinToString(",")))
            val preferHighRefreshRate = repo.getSetting("preferHighRefreshRate", prefs.getString("preferHighRefreshRate", "false") ?: "false").toBoolean()
            val vaultCurrency = ExchangeRateService.sanitizeCurrency(repo.getSetting("vaultCurrency", prefs.getString("vaultCurrency", "CNY") ?: "CNY"))
            prefs.edit()
                .putString("cardsPerRowPortrait", portraitRows.toString())
                .putString("cardsPerRowLandscape", landscapeRows.toString())
                .putString("ungroupedMode", ungrouped.toString())
                .putString("cardGalleryMode", cardGalleryMode.toString())
                .putString("tabOrder", tabOrder.joinToString(","))
                .putString("visibleOptionalTabs", visibleOptionalTabs.joinToString(","))
                .putString("visibleDataCharts", visibleDataCharts.joinToString(","))
                .putString("dataChartOrder", dataChartOrder.joinToString(","))
                .putString("visibleDataOverview", visibleDataOverview.joinToString(","))
                .putString("dataOverviewOrder", dataOverviewOrder.joinToString(","))
                .putString("vaultCurrency", vaultCurrency)
                .putString("preferHighRefreshRate", preferHighRefreshRate.toString())
                .apply()
            _cardsPerRowPortrait.value = portraitRows
            _cardsPerRowLandscape.value = landscapeRows
            _ungroupedMode.value = ungrouped
            _cardGalleryMode.value = cardGalleryMode
            _tabOrder.value = tabOrder
            _visibleOptionalTabs.value = visibleOptionalTabs
            _visibleDataCharts.value = visibleDataCharts
            _dataChartOrder.value = dataChartOrder
            _visibleDataOverview.value = visibleDataOverview
            _dataOverviewOrder.value = dataOverviewOrder
            _vaultCurrency.value = vaultCurrency
            _preferHighRefreshRate.value = preferHighRefreshRate
            refreshExchangeRates()
        }
        viewModelScope.launch {
            assetPlans.collect { plans ->
                if (ensureAssetPlanTradingYearsLoaded(plans)) {
                    _assetTradingCalendarVersion.value += 1
                }
            }
        }
    }

    // ── Notifications ─────────────────────────────────────



    // ── Settings ──────────────────────────────────────────
    fun toggleTheme() {
        viewModelScope.launch {
            setThemeMode(if (_themeMode.value == "dark") "light" else "dark")
        }
    }
    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            val safeMode = sanitizeThemeMode(mode)
            _themeMode.value = safeMode
            _isDark.value = safeMode == "dark"
            prefs.edit().putString("theme", safeMode).apply()
            repo.setSetting("theme", safeMode)
        }
    }
    fun setPigCard(id: String) { viewModelScope.launch { _pigCardId.value = id; repo.setSetting("pigCard", id) } }
    fun setPiggyTask(id: String) { viewModelScope.launch { _piggyTaskId.value = id; repo.setSetting("piggyTask", id) } }
    fun setPiggySyncFromStart(enabled: Boolean) {
        viewModelScope.launch {
            _piggySyncFromStart.value = enabled
            repo.setSetting("piggySyncFromStart", enabled.toString())
        }
    }
    fun setPiggyTasks(ids: Set<String>, syncFromStart: Boolean? = null) {
        viewModelScope.launch {
            syncFromStart?.let {
                _piggySyncFromStart.value = it
                repo.setSetting("piggySyncFromStart", it.toString())
            }
            val safeIds = ids.filter { id -> tasks.value.any { it.id == id && it.isInvest } }.distinct()
            val rules = safeIds.associateWith {
                PiggyTaskSyncRule(
                    mode = if (_piggySyncFromStart.value) "start" else "date",
                    date = LocalDate.now().toString()
                )
            }
            savePiggyTasksAndRules(safeIds, rules)
            syncPiggyTasksByRules(rules, notify = null)
        }
    }

    fun setPiggyTasksWithSyncRules(ids: Set<String>, rules: Map<String, PiggyTaskSyncRule>) {
        viewModelScope.launch {
            val safeIds = ids.filter { id -> tasks.value.any { it.id == id && it.isInvest } }.distinct()
            val safeRules = safeIds.associateWith { id ->
                sanitizePiggyTaskSyncRule(id, rules[id] ?: _piggyTaskSyncRules.value[id])
            }
            savePiggyTasksAndRules(safeIds, safeRules)
            syncPiggyTasksByRules(safeRules, notify = null)
        }
    }

    private suspend fun savePiggyTasksAndRules(ids: List<String>, rules: Map<String, PiggyTaskSyncRule>) {
        val raw = ids.joinToString(",")
        _piggyTaskId.value = raw
        _piggyTaskSyncRules.value = rules
        repo.setSetting("piggyTask", raw)
        repo.setSetting("piggyTaskSyncRules", serializePiggyTaskSyncRules(rules))
    }

    fun setPreferHighRefreshRate(enabled: Boolean) {
        viewModelScope.launch {
            _preferHighRefreshRate.value = enabled
            prefs.edit().putString("preferHighRefreshRate", enabled.toString()).apply()
            repo.setSetting("preferHighRefreshRate", enabled.toString())
        }
    }

    fun setVaultCurrency(currency: String) {
        val safe = ExchangeRateService.sanitizeCurrency(currency)
        viewModelScope.launch {
            _vaultCurrency.value = safe
            prefs.edit().putString("vaultCurrency", safe).apply()
            repo.setSetting("vaultCurrency", safe)
            refreshExchangeRates()
        }
    }

    fun cycleVaultCurrency() {
        val currencies = ExchangeRateService.supportedCurrencies
        val nextIndex = (currencies.indexOf(_vaultCurrency.value).coerceAtLeast(0) + 1) % currencies.size
        setVaultCurrency(currencies[nextIndex])
    }

    fun refreshExchangeRates() {
        viewModelScope.launch {
            _exchangeRates.value = ExchangeRateService.fetchRates()
        }
    }

    fun convertMoney(amount: Double, from: String, to: String = _vaultCurrency.value): Double =
        ExchangeRateService.convert(amount, from, to, _exchangeRates.value)

    fun vaultTotalIn(currency: String = _vaultCurrency.value): Double {
        val entryTotal = piggyEntries.value
            .filter(::isManualPiggyEntry)
            .sumOf { convertMoney(it.amount, "CNY", currency) }
        val planTotal = assetPlans.value
            .filter { it.countInTotal }
            .sumOf { convertMoney(assetPlanDisplayAmount(it), it.currency, currency) }
        return entryTotal + planTotal
    }
    fun setCardsPerRowPortrait(value: Int) { setCardsPerRow("cardsPerRowPortrait", value, _cardsPerRowPortrait, 4) }
    fun setCardsPerRowLandscape(value: Int) { setCardsPerRow("cardsPerRowLandscape", value, _cardsPerRowLandscape, 6) }
    fun setUngroupedMode(value: Boolean) {
        viewModelScope.launch {
            _ungroupedMode.value = value
            prefs.edit().putString("ungroupedMode", value.toString()).apply()
            repo.setSetting("ungroupedMode", value.toString())
        }
    }

    fun setCardGalleryMode(value: Boolean) {
        viewModelScope.launch {
            _cardGalleryMode.value = value
            prefs.edit().putString("cardGalleryMode", value.toString()).apply()
            repo.setSetting("cardGalleryMode", value.toString())
        }
    }

    fun setTabVisible(tabId: String, visible: Boolean) {
        if (tabId !in optionalTabIds) return
        viewModelScope.launch {
            val next = if (visible) _visibleOptionalTabs.value + tabId else _visibleOptionalTabs.value - tabId
            val safe = sanitizeVisibleOptionalTabs(next.joinToString(","))
            _visibleOptionalTabs.value = safe
            prefs.edit().putString("visibleOptionalTabs", safe.joinToString(",")).apply()
            repo.setSetting("visibleOptionalTabs", safe.joinToString(","))
        }
    }

    fun moveTab(tabId: String, delta: Int) {
        viewModelScope.launch {
            val ordered = _tabOrder.value.toMutableList()
            val from = ordered.indexOf(tabId)
            if (from < 0 || ordered.size < 2) return@launch
            val to = (from + delta).coerceIn(0, ordered.lastIndex)
            if (from == to) return@launch
            val moving = ordered.removeAt(from)
            ordered.add(to, moving)
            val safe = sanitizeTabOrder(ordered.joinToString(","))
            _tabOrder.value = safe
            prefs.edit().putString("tabOrder", safe.joinToString(",")).apply()
            repo.setSetting("tabOrder", safe.joinToString(","))
        }
    }

    private fun setCardsPerRow(key: String, value: Int, state: MutableStateFlow<Int>, maxValue: Int) {
        viewModelScope.launch {
            val safeValue = value.coerceIn(1, maxValue)
            state.value = safeValue
            prefs.edit().putString(key, safeValue.toString()).apply()
            repo.setSetting(key, safeValue.toString())
        }
    }

    private fun sanitizeTabOrder(raw: String): List<String> {
        val parsed = raw.split(",").map { it.trim() }.filter { it in defaultTabOrder }.distinct()
        return (parsed + defaultTabOrder.filter { it !in parsed }).distinct()
    }

    private fun sanitizeVisibleOptionalTabs(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it in optionalTabIds }.toSet()

    private fun sanitizeThemeMode(raw: String): String =
        if (raw in setOf("system", "light", "dark")) raw else "system"

    private fun parsePiggyTaskSyncRules(
        raw: String,
        taskIds: Set<String>,
        legacySyncFromStart: Boolean
    ): Map<String, PiggyTaskSyncRule> {
        val parsed = raw.split(";")
            .mapNotNull { item ->
                val parts = item.split("|")
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isBlank()) return@mapNotNull null
                id to PiggyTaskSyncRule(
                    mode = parts.getOrNull(1)?.trim().orEmpty(),
                    date = parts.getOrNull(2)?.trim().orEmpty()
                )
            }
            .toMap()
        return taskIds.associateWith { id ->
            sanitizePiggyTaskSyncRule(
                id,
                parsed[id] ?: PiggyTaskSyncRule(
                    mode = if (legacySyncFromStart) "start" else "date",
                    date = LocalDate.now().toString()
                )
            )
        }
    }

    private fun sanitizePiggyTaskSyncRule(taskId: String, rule: PiggyTaskSyncRule?): PiggyTaskSyncRule {
        val task = tasks.value.firstOrNull { it.id == taskId }
        val mode = if (rule?.mode == "start") "start" else "date"
        val date = runCatching { LocalDate.parse(rule?.date.orEmpty()).toString() }
            .getOrElse { task?.let { taskStartDate(it).toString() } ?: LocalDate.now().toString() }
        return PiggyTaskSyncRule(mode, date)
    }

    private fun serializePiggyTaskSyncRules(rules: Map<String, PiggyTaskSyncRule>): String =
        rules.entries.joinToString(";") { (id, rule) ->
            "$id|${if (rule.mode == "start") "start" else "date"}|${rule.date}"
        }

    private fun sanitizeDataOverview(raw: String): Set<String> {
        val parsed = raw.split(",").map { it.trim() }.filter { it in defaultDataOverviewIds }.toSet()
        return parsed.ifEmpty { defaultDataOverviewIds.toSet() }
    }

    private fun sanitizeDataOverviewOrder(raw: String): List<String> {
        val parsed = raw.split(",").map { it.trim() }.filter { it in defaultDataOverviewIds }.distinct()
        return (parsed + defaultDataOverviewIds.filter { it !in parsed }).distinct()
    }

    private fun sanitizeDataCharts(raw: String): Set<String> {
        val parsed = raw.split(",").map { it.trim() }.filter { it in defaultDataChartIds }.toSet()
        return parsed.ifEmpty { defaultDataChartIds.toSet() }
    }

    private fun sanitizeDataChartOrder(raw: String): List<String> {
        val parsed = raw.split(",").map { it.trim() }.filter { it in defaultDataChartIds }.distinct()
        return (parsed + defaultDataChartIds.filter { it !in parsed }).distinct()
    }

    fun setDataOverviewVisible(itemId: String, visible: Boolean) {
        if (itemId !in defaultDataOverviewIds) return
        viewModelScope.launch {
            val next = if (visible) _visibleDataOverview.value + itemId else _visibleDataOverview.value - itemId
            val safe = next.ifEmpty { setOf(itemId) }
            _visibleDataOverview.value = safe
            prefs.edit().putString("visibleDataOverview", safe.joinToString(",")).apply()
            repo.setSetting("visibleDataOverview", safe.joinToString(","))
        }
    }

    fun moveDataOverview(itemId: String, delta: Int) {
        moveDataItem(itemId, delta, defaultDataOverviewIds, _dataOverviewOrder, "dataOverviewOrder")
    }

    fun setDataChartVisible(chartId: String, visible: Boolean) {
        if (chartId !in defaultDataChartIds) return
        viewModelScope.launch {
            val next = if (visible) _visibleDataCharts.value + chartId else _visibleDataCharts.value - chartId
            val safe = next.ifEmpty { setOf(chartId) }
            _visibleDataCharts.value = safe
            prefs.edit().putString("visibleDataCharts", safe.joinToString(",")).apply()
            repo.setSetting("visibleDataCharts", safe.joinToString(","))
        }
    }

    fun moveDataChart(chartId: String, delta: Int) {
        moveDataItem(chartId, delta, defaultDataChartIds, _dataChartOrder, "dataChartOrder")
    }

    private fun moveDataItem(
        itemId: String,
        delta: Int,
        allowedIds: List<String>,
        state: MutableStateFlow<List<String>>,
        key: String
    ) {
        if (itemId !in allowedIds) return
        viewModelScope.launch {
            val ordered = state.value.toMutableList()
            val from = ordered.indexOf(itemId)
            if (from < 0 || ordered.size < 2) return@launch
            val to = (from + delta).coerceIn(0, ordered.lastIndex)
            if (from == to) return@launch
            val moving = ordered.removeAt(from)
            ordered.add(to, moving)
            val safe = (ordered.filter { it in allowedIds } + allowedIds.filter { it !in ordered }).distinct()
            state.value = safe
            prefs.edit().putString(key, safe.joinToString(",")).apply()
            repo.setSetting(key, safe.joinToString(","))
        }
    }

    // ── Groups ─────────────────────────────────────────────
    fun addGroup(name: String, icon: String) { viewModelScope.launch { repo.saveGroup(CardGroup(UUID.randomUUID().toString(), name, icon, sortOrder = groups.value.size)) } }
    fun updateGroup(g: CardGroup) { viewModelScope.launch { repo.updateGroup(g) } }
    fun deleteGroup(g: CardGroup) { viewModelScope.launch { repo.deleteGroup(g) } }
    fun toggleGroupOpen(g: CardGroup) { viewModelScope.launch { repo.updateGroup(g.copy(isOpen = !g.isOpen)) } }
    fun moveGroupUp(group: CardGroup) { viewModelScope.launch { moveGroup(group, -1) } }
    fun moveGroupDown(group: CardGroup) { viewModelScope.launch { moveGroup(group, 1) } }

    private suspend fun moveGroup(group: CardGroup, delta: Int) {
        val ordered = groups.value
            .sortedWith(compareBy<CardGroup> { it.sortOrder }.thenBy { it.id })
            .toMutableList()
        val from = ordered.indexOfFirst { it.id == group.id }
        if (from < 0 || ordered.size < 2) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return
        val moving = ordered.removeAt(from)
        ordered.add(to, moving)
        ordered.forEachIndexed { index, item ->
            if (item.sortOrder != index) repo.updateGroup(item.copy(sortOrder = index))
        }
    }

    // ── Cards ──────────────────────────────────────────────
    fun addCard(groupId: String, bank: String, network: String, currency: String, tail: String,
                note: String, status: String, isVirtual: Boolean, noCard: Boolean,
                logoEmoji: String = "", logoImagePath: String = "", bankLogoPath: String = "",
                cardTypeName: String = "", expiryDate: String = "", cardCategory: String = "",
                imageOrientation: String = "horizontal") {
        viewModelScope.launch {
            val order = cards.value.count { it.groupId == groupId }
            repo.saveCard(Card(UUID.randomUUID().toString(), groupId, bank, network, currency,
                tail, "", note, status, isVirtual, noCard, logoEmoji, logoImagePath,
                bankLogoPath, cardTypeName, expiryDate, cardCategory, order, imageOrientation))
        }
    }
    fun updateCard(c: Card) {
        viewModelScope.launch {
            repo.updateCard(c)
            _imageVersion.value += 1
        }
    }
    fun deleteCard(c: Card) { viewModelScope.launch { repo.deleteCard(c) } }
    fun saveBankLogo(card: Card, uri: Uri) {
        viewModelScope.launch {
            val path = ImageStore.saveFromUri(getApplication(), uri, "bank_${card.id}")
            if (path.isNotEmpty()) {
                repo.updateCard(card.copy(bankLogoPath = path))
                _imageVersion.value += 1
            }
        }
    }

    fun saveCardImage(card: Card, uri: Uri) {
        viewModelScope.launch {
            val path = ImageStore.saveFromUri(getApplication(), uri, card.id)
            if (path.isNotEmpty()) {
                repo.updateCard(card.copy(logoImagePath = path))
                _imageVersion.value += 1
            }
        }
    }
    fun moveCardUp(card: Card) {
        viewModelScope.launch {
            moveCard(card, -1)
        }
    }
    fun moveCardDown(card: Card) {
        viewModelScope.launch {
            moveCard(card, 1)
        }
    }
    fun cardsForGroup(groupId: String) = cards.value
        .filter { it.groupId == groupId }
        .sortedWith(compareBy<Card> { it.sortOrder }.thenBy { it.id })

    private suspend fun moveCard(card: Card, delta: Int) {
        val ordered = cards.value
            .filter { it.groupId == card.groupId }
            .sortedWith(compareBy<Card> { it.sortOrder }.thenBy { it.id })
            .toMutableList()
        val from = ordered.indexOfFirst { it.id == card.id }
        if (from < 0 || ordered.size < 2) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return
        val moving = ordered.removeAt(from)
        ordered.add(to, moving)
        ordered.forEachIndexed { index, item ->
            if (item.sortOrder != index) repo.updateCard(item.copy(sortOrder = index))
        }
    }

    // ── Tasks ──────────────────────────────────────────────
    fun addTask(name: String, freq: String, cardId: String, isInvest: Boolean, investAmount: Double,
                day: Int, weekday: Int, months: String, ndays: Int, startDate: String, date: String,
                holidays: String) {
        viewModelScope.launch {
            repo.saveTask(Task(UUID.randomUUID().toString(), name, freq, cardId, isInvest,
                investAmount, day, weekday, months, ndays.coerceAtLeast(1), startDate, date, holidays))
        }
    }
    fun updateTask(t: Task) { viewModelScope.launch { repo.updateTask(t.copy(ndays = t.ndays.coerceAtLeast(1))) } }
    fun deleteTask(t: Task) { viewModelScope.launch { repo.deleteTask(t) } }

    fun taskDatesForMonth(year: Int, month: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        val daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth()
        tasks.value.forEach { task ->
            (1..daysInMonth).forEach { d ->
                val date = LocalDate.of(year, month, d)
                if (taskMatchesDate(task, date)) result.add(d)
            }
        }
        return result
    }

    fun postponedTaskDatesForMonth(year: Int, month: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        val daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth()
        tasks.value.filter { it.isInvest }.forEach { task ->
            (1..daysInMonth).forEach { d ->
                val date = LocalDate.of(year, month, d)
                if (taskPostponedFromDates(task, date).isNotEmpty()) result.add(d)
            }
        }
        return result
    }

    fun tasksForDate(year: Int, month: Int, day: Int): List<Task> {
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return emptyList()
        return tasks.value.filter { task -> taskMatchesDate(task, date) }
    }

    private fun taskStartDate(task: Task): LocalDate =
        runCatching { LocalDate.parse(task.startDate.ifBlank { task.date.ifBlank { LocalDate.now().toString() } }) }
            .getOrDefault(LocalDate.now())

    fun taskMatchesDate(task: Task, date: LocalDate): Boolean {
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
        val start = taskStartDate(task)
        if (date.isBefore(start)) return false
        return when (task.freq) {
            "monthly" -> task.day == date.dayOfMonth
            "weekly" -> task.weekday == date.dayOfWeek.value
            "quarterly" -> {
                val ms = task.months.split(",").mapNotNull { it.trim().toIntOrNull() }
                date.monthValue in ms && task.day == date.dayOfMonth
            }
            "ndays" -> {
                val interval = task.ndays.coerceAtLeast(1)
                ChronoUnit.DAYS.between(start, date) % interval == 0L
            }
            "once" -> {
                val onceDate = runCatching { LocalDate.parse(task.date.ifBlank { task.startDate }) }.getOrNull()
                onceDate == date && !date.isBefore(start)
            }
            else -> false
        }
    }

    // ── Piggy ──────────────────────────────────────────────
    val piggyEntryTotal: StateFlow<Double> = piggyEntries
        .map { entries -> entries.filter(::isManualPiggyEntry).sumOf { e -> e.amount } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val assetPlanTotal: StateFlow<Double> = combine(assetPlans, exchangeRates) { plans, _ ->
        plans.filter { it.countInTotal }.sumOf { convertMoney(assetPlanDisplayAmount(it), it.currency, "CNY") }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val piggyTotal: StateFlow<Double> = combine(piggyEntryTotal, assetPlanTotal) { piggy, assets -> piggy + assets }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun addPiggyEntry(amount: Double, desc: String) {
        viewModelScope.launch {
            repo.savePiggy(PiggyEntry(System.currentTimeMillis(), amount, desc, "", LocalDate.now().toString()))
        }
    }
    fun updatePiggyEntry(e: PiggyEntry) { viewModelScope.launch { repo.updatePiggy(e) } }
    fun deletePiggyEntry(e: PiggyEntry) { viewModelScope.launch { repo.deletePiggy(e) } }

    private fun isManualPiggyEntry(entry: PiggyEntry): Boolean =
        !entry.desc.startsWith("任务同步：")

    fun addAssetPlan(plan: AssetPlan) {
        viewModelScope.launch {
            repo.saveAssetPlan(plan.copy(
                id = plan.id.ifBlank { UUID.randomUUID().toString() },
                orderIndex = if (plan.orderIndex == 0L) System.currentTimeMillis() else plan.orderIndex,
                startDate = plan.startDate.ifBlank { LocalDate.now().toString() },
                initialDate = plan.initialDate.ifBlank { plan.startDate.ifBlank { LocalDate.now().toString() } }
            ))
        }
    }

    fun updateAssetPlan(plan: AssetPlan) {
        viewModelScope.launch { repo.updateAssetPlan(plan) }
    }

    fun deleteAssetPlan(plan: AssetPlan) {
        viewModelScope.launch { repo.deleteAssetPlan(plan) }
    }

    fun archiveAssetPlan(plan: AssetPlan, countInTotal: Boolean) {
        viewModelScope.launch {
            val frozen = AssetCalculator.calc(plan).amount
            repo.updateAssetPlan(plan.copy(
                status = AssetPlanStatus.STOPPED,
                frozenAmount = frozen,
                countInTotal = countInTotal
            ))
        }
    }

    fun restoreAssetPlan(plan: AssetPlan) {
        viewModelScope.launch {
            repo.updateAssetPlan(plan.copy(status = AssetPlanStatus.RUNNING))
        }
    }

    fun assetPlanDisplayAmount(plan: AssetPlan): Double =
        if (plan.status == AssetPlanStatus.STOPPED) {
            if (plan.countInTotal) plan.frozenAmount else 0.0
        } else {
            AssetCalculator.calc(plan).amount
        }

    fun assetPlanLogs(plan: AssetPlan): List<AssetTransactionLog> =
        AssetCalculator.calc(plan).logs

    fun syncPiggyTaskForToday(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            syncPiggyTasksFrom(LocalDate.now(), onResult)
        }
    }
    fun syncPiggyTasksToToday() {
        viewModelScope.launch {
            val taskIds = _piggyTaskId.value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val rules = taskIds.associateWith { id ->
                sanitizePiggyTaskSyncRule(id, _piggyTaskSyncRules.value[id] ?: PiggyTaskSyncRule("start"))
            }
            syncPiggyTasksByRules(rules, notify = null)
        }
    }

    private suspend fun syncPiggyTasksFrom(fromDateOverride: LocalDate?, notify: ((Boolean, String) -> Unit)?) {
        val taskIds = _piggyTaskId.value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val rules = taskIds.associateWith { id ->
            fromDateOverride?.let { PiggyTaskSyncRule("date", it.toString()) }
                ?: sanitizePiggyTaskSyncRule(id, _piggyTaskSyncRules.value[id] ?: PiggyTaskSyncRule("start"))
        }
        syncPiggyTasksByRules(rules, notify)
    }

    private suspend fun syncPiggyTasksByRules(
        rules: Map<String, PiggyTaskSyncRule>,
        notify: ((Boolean, String) -> Unit)?
    ) {
        val taskIds = rules.keys.filter { it.isNotBlank() }.distinct()
        if (taskIds.isEmpty()) {
            notify?.invoke(false, "\u8bf7\u5148\u7ed1\u5b9a\u4e00\u4e2a\u4efb\u52a1")
            return
        }
        val boundTasks = taskIds.mapNotNull { id -> tasks.value.firstOrNull { it.id == id && it.isInvest } }
        if (boundTasks.isEmpty()) {
            notify?.invoke(false, "\u7ed1\u5b9a\u4efb\u52a1\u6ca1\u6709\u6295\u8d44\u91d1\u989d")
            return
        }
        val today = LocalDate.now()
        val startByTask = boundTasks.associateWith { task ->
            val rule = sanitizePiggyTaskSyncRule(task.id, rules[task.id])
            if (rule.mode == "start") {
                taskStartDate(task)
            } else {
                runCatching { LocalDate.parse(rule.date) }.getOrDefault(LocalDate.now())
            }
        }
        val earliest = startByTask.values.minOrNull() ?: today
        ensureTradingYearsLoaded(earliest, today)

        val inserts = mutableListOf<Pair<Task, LocalDate>>()
        boundTasks.filter { it.investAmount != 0.0 }.forEach { task ->
            var cursor = startByTask[task] ?: taskStartDate(task)
            if (cursor.isBefore(taskStartDate(task))) cursor = taskStartDate(task)
            while (!cursor.isAfter(today)) {
                val desc = "\u4efb\u52a1\u540c\u6b65\uff1a${task.name}"
                val dateText = cursor.toString()
                if (taskMatchesDate(task, cursor) &&
                    piggyEntries.value.none { it.date == dateText && it.desc == desc } &&
                    inserts.none { it.first.id == task.id && it.second == cursor }
                ) {
                    inserts += task to cursor
                }
                cursor = cursor.plusDays(1)
            }
        }
        if (inserts.isEmpty()) {
            notify?.invoke(false, "\u6ca1\u6709\u9700\u8981\u540c\u6b65\u7684\u4efb\u52a1\u91d1\u989d")
            return
        }
        inserts.forEachIndexed { index, (task, date) ->
            repo.savePiggy(
                PiggyEntry(
                    id = System.currentTimeMillis() + index,
                    amount = kotlin.math.abs(task.investAmount),
                    desc = "\u4efb\u52a1\u540c\u6b65\uff1a${task.name}",
                    cardId = task.cardId.ifBlank { _pigCardId.value },
                    date = date.toString()
                )
            )
        }
        notify?.invoke(true, "\u5df2\u540c\u6b65 ${inserts.size} \u6761\u4efb\u52a1\u91d1\u989d")
    }

    private suspend fun ensureTradingYearsLoaded(from: LocalDate, to: LocalDate) {
        val app = getApplication<Application>()
        (from.year..to.year).forEach { year ->
            if (!TradingDayService.isLoaded(year)) {
                TradingDayService.loadYear(year, app)
            }
        }
    }

    private suspend fun ensureAssetPlanTradingYearsLoaded(plans: List<AssetPlan>): Boolean {
        if (plans.isEmpty()) return false
        val today = LocalDate.now()
        val dates = plans.flatMap { plan ->
            buildList {
                AssetCalculator.parseDate(plan.startDate)?.let { add(it) }
                AssetCalculator.parseDate(plan.initialDate)?.let { add(it) }
                AssetPlanCodec.decodeRatePlans(plan.ratePlansJson).forEach { rate ->
                    AssetCalculator.parseDate(rate.startDate)?.let { add(it) }
                }
            }
        }
        val from = listOfNotNull(dates.minOrNull(), today).minOrNull() ?: today
        val to = listOfNotNull(dates.maxOrNull(), today).maxOrNull() ?: today
        val years = (from.year..to.year).toSet()
        val yearsToLoad = years.filter { it !in loadedAssetPlanTradingYears || !TradingDayService.isLoaded(it) }
        if (yearsToLoad.isEmpty()) return false
        val app = getApplication<Application>()
        yearsToLoad.forEach { year ->
            if (!TradingDayService.isLoaded(year)) {
                TradingDayService.loadYear(year, app)
            }
        }
        loadedAssetPlanTradingYears = loadedAssetPlanTradingYears + years
        return true
    }

    fun exportBackup(uri: android.net.Uri, password: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                repo.exportBackup(app, uri, password)
                onResult(true, app.getString(R.string.backup_success))
            } catch (e: Exception) { onResult(false, app.getString(R.string.backup_failed, e.message ?: "")) }
        }
    }

    fun importBackup(uri: android.net.Uri, mode: ImportMode, password: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val summary = repo.importBackup(app, uri, mode, password)
                reloadImportedSettings()
                onResult(true, app.getString(R.string.import_success_summary,
                    summary.groupCount, summary.cardCount, summary.taskCount, summary.pigCount, summary.assetPlanCount))
            } catch (e: Exception) { onResult(false, app.getString(R.string.import_failed, e.message ?: "")) }
        }
    }

    private suspend fun reloadImportedSettings() {
        val theme = sanitizeThemeMode(repo.getSetting("theme", _themeMode.value))
        val portraitRows = repo.getSetting("cardsPerRowPortrait", _cardsPerRowPortrait.value.toString())
            .toIntOrNull()?.coerceIn(1, 4) ?: 2
        val landscapeRows = repo.getSetting("cardsPerRowLandscape", _cardsPerRowLandscape.value.toString())
            .toIntOrNull()?.coerceIn(1, 6) ?: 3
        val ungrouped = repo.getSetting("ungroupedMode", _ungroupedMode.value.toString()).toBoolean()
        val cardGalleryMode = repo.getSetting("cardGalleryMode", _cardGalleryMode.value.toString()).toBoolean()
        val tabOrder = sanitizeTabOrder(repo.getSetting("tabOrder", _tabOrder.value.joinToString(",")))
        val visibleTabs = sanitizeVisibleOptionalTabs(repo.getSetting("visibleOptionalTabs", _visibleOptionalTabs.value.joinToString(",")))
        val visibleCharts = sanitizeDataCharts(repo.getSetting("visibleDataCharts", _visibleDataCharts.value.joinToString(",")))
        val chartOrder = sanitizeDataChartOrder(repo.getSetting("dataChartOrder", _dataChartOrder.value.joinToString(",")))
        val visibleOverview = sanitizeDataOverview(repo.getSetting("visibleDataOverview", _visibleDataOverview.value.joinToString(",")))
        val overviewOrder = sanitizeDataOverviewOrder(repo.getSetting("dataOverviewOrder", _dataOverviewOrder.value.joinToString(",")))
        val piggyTaskRaw = repo.getSetting("piggyTask", _piggyTaskId.value)
        val legacyPiggySyncFromStart = repo.getSetting("piggySyncFromStart", _piggySyncFromStart.value.toString()).toBoolean()
        val preferHighRefreshRate = repo.getSetting("preferHighRefreshRate", _preferHighRefreshRate.value.toString()).toBoolean()
        val vaultCurrency = ExchangeRateService.sanitizeCurrency(repo.getSetting("vaultCurrency", _vaultCurrency.value))

        _themeMode.value = theme
        _isDark.value = theme == "dark"
        _pigCardId.value = repo.getSetting("pigCard", _pigCardId.value)
        _piggyTaskId.value = piggyTaskRaw
        _piggySyncFromStart.value = legacyPiggySyncFromStart
        _piggyTaskSyncRules.value = parsePiggyTaskSyncRules(
            repo.getSetting("piggyTaskSyncRules", ""),
            piggyTaskRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            legacyPiggySyncFromStart
        )
        _cardsPerRowPortrait.value = portraitRows
        _cardsPerRowLandscape.value = landscapeRows
        _ungroupedMode.value = ungrouped
        _cardGalleryMode.value = cardGalleryMode
        _tabOrder.value = tabOrder
        _visibleOptionalTabs.value = visibleTabs
        _visibleDataCharts.value = visibleCharts
        _dataChartOrder.value = chartOrder
        _visibleDataOverview.value = visibleOverview
        _dataOverviewOrder.value = overviewOrder
        _vaultCurrency.value = vaultCurrency
        _preferHighRefreshRate.value = preferHighRefreshRate
        refreshExchangeRates()

        prefs.edit()
            .putString("theme", theme)
            .putString("cardsPerRowPortrait", portraitRows.toString())
            .putString("cardsPerRowLandscape", landscapeRows.toString())
            .putString("ungroupedMode", ungrouped.toString())
            .putString("cardGalleryMode", cardGalleryMode.toString())
            .putString("tabOrder", tabOrder.joinToString(","))
            .putString("visibleOptionalTabs", visibleTabs.joinToString(","))
            .putString("visibleDataCharts", visibleCharts.joinToString(","))
            .putString("dataChartOrder", chartOrder.joinToString(","))
            .putString("visibleDataOverview", visibleOverview.joinToString(","))
            .putString("dataOverviewOrder", overviewOrder.joinToString(","))
            .putString("vaultCurrency", vaultCurrency)
            .putString("preferHighRefreshRate", preferHighRefreshRate.toString())
            .apply()
    }

    /** 兼容旧版 PWA JSON 导入（不含图片） */
    fun importPwaJson(json: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = PwaImporter.parse(json, getApplication())
                result.groups.forEach { repo.saveGroup(it) }
                result.cards.forEach { repo.saveCard(it) }
                result.tasks.forEach { repo.saveTask(it) }
                result.piggy.forEach { repo.savePiggy(it) }
                repo.migrateLegacyInvestTasksToAssetPlans()
                val app = getApplication<Application>()
                onResult(true, app.getString(R.string.import_success_summary,
                    result.groupCount, result.cardCount, result.taskCount, result.piggyCount, 0))
            } catch (e: Exception) {
                val app = getApplication<Application>()
                onResult(false, app.getString(R.string.import_failed, e.message ?: ""))
            }
        }
    }

    // ── Settings helpers（给 UI 直接调用）────────────────
    suspend fun getSetting(key: String, default: String = "") = repo.getSetting(key, default)
    fun setSetting(key: String, value: String) { viewModelScope.launch { repo.setSetting(key, value) } }

    // ── Helpers ────────────────────────────────────────────
    fun cardName(id: String): String {
        val c = cards.value.find { it.id == id } ?: return ""
        return "${c.bank}${if (c.tail.isNotEmpty()) " *${c.tail}" else ""}"
    }

}
