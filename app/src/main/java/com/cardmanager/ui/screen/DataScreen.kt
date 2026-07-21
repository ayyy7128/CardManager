package com.cardmanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardmanager.R
import com.cardmanager.data.AssetCalculator
import com.cardmanager.data.CardCreditLimitTools
import com.cardmanager.ui.components.AppPanel
import com.cardmanager.ui.components.MetricTile
import com.cardmanager.ui.components.SectionHeader
import com.cardmanager.ui.components.bottomSpacer
import com.cardmanager.ui.components.screenPaddingFor
import com.cardmanager.ui.theme.ColorActive
import com.cardmanager.ui.theme.ColorFrozen
import com.cardmanager.ui.theme.ColorGold
import com.cardmanager.ui.theme.ColorPending
import com.cardmanager.ui.theme.ColorRed
import com.cardmanager.viewmodel.MainViewModel
import java.text.DecimalFormat

private data class ChartItem(val label: String, val count: Int, val color: Color)
private data class ChartSpec(val id: String, val title: String, val total: Int, val items: List<ChartItem>)
private const val CREDIT_LIMIT_OVERVIEW_ID = "creditLimitOverview"

private data class CreditLimitBankItem(val bank: String, val tails: List<String>, val amount: Double)
private data class MetricSpec(
    val id: String,
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: (() -> Unit)? = null
)
private data class DataOptionSpec(
    val id: String,
    val title: String,
    val reorderable: Boolean = true,
    val modeLabel: String? = null,
    val modeChecked: Boolean? = null,
    val onModeChange: ((Boolean) -> Unit)? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(vm: MainViewModel) {
    val cards by vm.cards.collectAsState()
    val groups by vm.groups.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val piggyEntries by vm.piggyEntries.collectAsState()
    val total by vm.piggyTotal.collectAsState()
    val vaultCurrency by vm.vaultCurrency.collectAsState()
    val exchangeRates by vm.exchangeRates.collectAsState()
    val visibleCharts by vm.visibleDataCharts.collectAsState()
    val chartOrder by vm.dataChartOrder.collectAsState()
    val visibleOverview by vm.visibleDataOverview.collectAsState()
    val showCreditLimitOverview by vm.showCreditLimitOverview.collectAsState()
    val creditLimitGroupMode by vm.creditLimitGroupMode.collectAsState()
    val overviewOrder by vm.dataOverviewOrder.collectAsState()
    val cs = MaterialTheme.colorScheme
    val df = remember { DecimalFormat("#,##0.00") }

    val activeCards = cards.count { it.status == "active" }
    val frozenCards = cards.count { it.status == "frozen" }
    val pendingCards = cards.count { it.status == "pending" }
    val cancelledCards = cards.count { it.status == "cancelled" }
    val abnormalCards = cards.filter { it.status != "active" }
    var showAbnormalCards by remember { mutableStateOf(false) }
    var showOverviewSettings by remember { mutableStateOf(false) }
    var showChartSettings by remember { mutableStateOf(false) }

    val normalLabel = stringResource(R.string.normal)
    val frozenLabel = stringResource(R.string.frozen)
    val pendingLabel = stringResource(R.string.pending)
    val cancelledLabel = stringResource(R.string.cancelled)
    val physicalLabel = stringResource(R.string.physical_card)
    val virtualLabel = stringResource(R.string.virtual_card)
    val accountLabel = stringResource(R.string.pure_account)
    val debitLabel = stringResource(R.string.debit_card)
    val creditLabel = stringResource(R.string.credit_card)
    val notSetLabel = stringResource(R.string.not_set)
    val ungroupedLabel = stringResource(R.string.ungrouped_cards)
    val otherLabel = stringResource(R.string.other_items)
    val monthlyLabel = stringResource(R.string.freq_monthly)
    val weeklyLabel = stringResource(R.string.freq_weekly)
    val quarterlyLabel = stringResource(R.string.freq_quarterly)
    val ndaysLabel = stringResource(R.string.freq_ndays)
    val onceLabel = stringResource(R.string.freq_once)
    val linkedTaskLabel = stringResource(R.string.linked_tasks)
    val unlinkedTaskLabel = stringResource(R.string.unlinked_tasks)
    val depositLabel = stringResource(R.string.deposit)
    val withdrawLabel = stringResource(R.string.withdraw)

    val palette = listOf(
        cs.primary,
        ColorActive,
        Color(0xFF7C3AED),
        Color(0xFF0F766E),
        ColorGold,
        ColorPending,
        ColorRed,
        ColorFrozen
    )
    fun colorAt(index: Int) = palette[index % palette.size]
    fun List<Pair<String, Int>>.toChartItems(): List<ChartItem> =
        mapIndexed { index, item -> ChartItem(item.first, item.second, colorAt(index)) }

    fun countByLabel(values: List<String>): List<Pair<String, Int>> =
        values.groupingBy { it.ifBlank { notSetLabel } }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    fun topRows(rows: List<Pair<String, Int>>, maxRows: Int = 6): List<Pair<String, Int>> {
        if (rows.size <= maxRows) return rows
        val visible = rows.take(maxRows - 1)
        val otherCount = rows.drop(maxRows - 1).sumOf { it.second }
        return visible + (otherLabel to otherCount)
    }

    val groupNameById = remember(groups) { groups.associate { it.id to it.name } }
    val creditCards = remember(cards) {
        cards.filter { card ->
            !card.noCard &&
                card.status != "cancelled" &&
                (card.cardCategory == "信用卡" || card.cardCategory.equals("credit", ignoreCase = true))
        }
    }
    val creditLimitBankItems = remember(
        creditCards,
        vaultCurrency,
        exchangeRates,
        notSetLabel,
        creditLimitGroupMode
    ) {
        val perCardItems = creditCards
            .asSequence()
            .groupBy { card ->
                card.sharedCreditLimitGroupId.takeIf { it.isNotBlank() } ?: "card:${card.id}"
            }
            .mapNotNull { (_, members) ->
                val card = members.firstOrNull() ?: return@mapNotNull null
                val fallbackCurrency = card.sharedCreditLimitCurrency.ifBlank { card.currency }
                val limits = CardCreditLimitTools.effective(
                    CardCreditLimitTools.decode(card.creditLimitsJson, fallbackCurrency, card.creditLimit)
                )
                val amount = limits.sumOf { limit ->
                    vm.convertMoney(limit.amount, limit.currency, vaultCurrency)
                }
                if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
                CreditLimitBankItem(
                    bank = card.bank.ifBlank { notSetLabel },
                    tails = members.map { it.tail.trim() }.filter { it.isNotBlank() },
                    amount = amount
                )
            }
            .toList()
        val groupedItems = if (creditLimitGroupMode == "bank") {
            perCardItems
                .groupBy { it.bank }
                .map { (bank, items) ->
                    CreditLimitBankItem(
                        bank = bank,
                        tails = emptyList(),
                        amount = items.sumOf { it.amount }
                    )
                }
        } else {
            perCardItems
        }
        groupedItems
            .sortedByDescending { it.amount }
            .let { rows ->
                if (rows.size <= 6) rows
                else rows.take(5) + CreditLimitBankItem(
                    bank = otherLabel,
                    tails = emptyList(),
                    amount = rows.drop(5).sumOf { it.amount }
                )
            }
    }
    val totalCreditLimit = remember(creditLimitBankItems) {
        creditLimitBankItems.sumOf { it.amount }
    }
    val metricSpecs = listOf(
        MetricSpec("totalCards", stringResource(R.string.total_cards), "${cards.size}", Icons.Default.CreditCard, cs.primary),
        MetricSpec("groupCount", stringResource(R.string.group_count), "${groups.size}", Icons.Default.FolderOpen, Color(0xFF7C3AED)),
        MetricSpec("activeCards", stringResource(R.string.active_cards), "$activeCards", Icons.Default.Verified, ColorActive),
        MetricSpec("totalTasks", stringResource(R.string.total_tasks), "${tasks.size}", Icons.Default.Task, Color(0xFF0F766E)),
        MetricSpec("savingsBalance", stringResource(R.string.savings_balance), "¥${df.format(total)}", Icons.Default.Savings, ColorGold),
        MetricSpec("abnormalCards", stringResource(R.string.abnormal_cards), "${abnormalCards.size}", Icons.Default.ReportProblem, ColorRed) {
            showAbnormalCards = true
        }
    )
    val orderedMetricSpecs = overviewOrder.mapNotNull { id -> metricSpecs.firstOrNull { it.id == id } }
    val selectedMetricSpecs = orderedMetricSpecs.filter { it.id in visibleOverview }

    val chartSpecs = listOf(
        ChartSpec(
            "status",
            stringResource(R.string.card_status_distribution),
            cards.size,
            listOf(
                ChartItem(normalLabel, activeCards, ColorActive),
                ChartItem(frozenLabel, frozenCards, ColorFrozen),
                ChartItem(pendingLabel, pendingCards, ColorPending),
                ChartItem(cancelledLabel, cancelledCards, ColorRed)
            )
        ),
        ChartSpec(
            "composition",
            stringResource(R.string.card_composition),
            cards.size,
            listOf(
                physicalLabel to cards.count { !it.noCard && !it.isVirtual },
                virtualLabel to cards.count { !it.noCard && it.isVirtual },
                accountLabel to cards.count { it.noCard }
            ).toChartItems()
        ),
        ChartSpec(
            "category",
            stringResource(R.string.card_category_distribution),
            cards.size,
            countByLabel(cards.map {
                when {
                    it.noCard -> accountLabel
                    it.cardCategory == "储蓄卡" || it.cardCategory.equals("debit", ignoreCase = true) -> debitLabel
                    it.cardCategory == "信用卡" || it.cardCategory.equals("credit", ignoreCase = true) -> creditLabel
                    else -> notSetLabel
                }
            }).toChartItems()
        ),
        ChartSpec("network", stringResource(R.string.network), cards.size, topRows(countByLabel(cards.map { if (it.noCard) accountLabel else it.network })).toChartItems()),
        ChartSpec("currency", stringResource(R.string.currency), cards.size, topRows(countByLabel(cards.map { it.currency })).toChartItems()),
        ChartSpec("group", stringResource(R.string.card_group), cards.size, topRows(countByLabel(cards.map { groupNameById[it.groupId] ?: ungroupedLabel })).toChartItems()),
        ChartSpec(
            "taskFrequency",
            stringResource(R.string.frequency),
            tasks.size,
            countByLabel(tasks.map {
                when (it.freq) {
                    "monthly" -> monthlyLabel
                    "weekly" -> weeklyLabel
                    "quarterly" -> quarterlyLabel
                    "ndays" -> ndaysLabel
                    "once" -> onceLabel
                    else -> notSetLabel
                }
            }).toChartItems()
        ),
        ChartSpec(
            "taskLink",
            stringResource(R.string.task_link_distribution),
            tasks.size,
            listOf(
                linkedTaskLabel to tasks.count { it.cardId.isNotBlank() },
                unlinkedTaskLabel to tasks.count { it.cardId.isBlank() }
            ).toChartItems()
        ),
        ChartSpec(
            "piggyFlow",
            stringResource(R.string.piggy_flow_distribution),
            piggyEntries.size,
            listOf(
                depositLabel to piggyEntries.count { it.amount >= 0.0 },
                withdrawLabel to piggyEntries.count { it.amount < 0.0 }
            ).toChartItems()
        )
    )
    val orderedChartSpecs = chartOrder.mapNotNull { id -> chartSpecs.firstOrNull { it.id == id } }
    val selectedChartSpecs = orderedChartSpecs.filter { it.id in visibleCharts }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagePadding = screenPaddingFor(maxWidth)
        val metricColumns = if (maxWidth < 340.dp) 1 else 2
        val chartColumns = when {
            maxWidth < 340.dp -> 1
            else -> 2
        }

        LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showCreditLimitOverview) {
                item {
                    CreditLimitOverviewPanel(
                        cardCount = creditCards.size,
                        totalLimit = totalCreditLimit,
                        currency = vaultCurrency,
                        groupMode = creditLimitGroupMode,
                        bankItems = creditLimitBankItems
                    )
                }
            }

            item {
                SectionHeader(
                    stringResource(R.string.data_overview),
                    subtitle = stringResource(
                        R.string.count_items,
                        selectedMetricSpecs.size + if (showCreditLimitOverview) 1 else 0
                    ),
                    trailing = {
                        IconButton(onClick = { showOverviewSettings = true }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.data_overview_settings), tint = cs.onSurfaceVariant)
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = metricColumns
                ) {
                    selectedMetricSpecs.forEach { spec ->
                        Box(Modifier.weight(1f)) {
                            MetricTile(
                                spec.title,
                                spec.value,
                                spec.icon,
                                spec.color,
                                Modifier.fillMaxWidth(),
                                onClick = spec.onClick
                            )
                            if (spec.onClick != null) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    null,
                                    tint = spec.color.copy(alpha = 0.72f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.data_charts),
                    subtitle = stringResource(R.string.count_items, selectedChartSpecs.size),
                    trailing = {
                        IconButton(onClick = { showChartSettings = true }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.data_chart_settings), tint = cs.onSurfaceVariant)
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = chartColumns
                ) {
                    selectedChartSpecs.forEach { spec ->
                        HistogramPanel(spec = spec, modifier = Modifier.weight(1f))
                    }
                }
            }

            bottomSpacer(24.dp)
        }
    }

    if (showOverviewSettings) {
        val creditLimitOption = DataOptionSpec(
            id = CREDIT_LIMIT_OVERVIEW_ID,
            title = stringResource(R.string.credit_limit_overview_setting),
            reorderable = false,
            modeLabel = stringResource(
                if (creditLimitGroupMode == "bank") R.string.credit_limit_display_by_bank
                else R.string.credit_limit_display_by_card
            ),
            modeChecked = creditLimitGroupMode == "bank",
            onModeChange = { groupByBank ->
                vm.setCreditLimitGroupMode(if (groupByBank) "bank" else "card")
            }
        )
        DataVisibilityDialog(
            title = stringResource(R.string.data_overview_settings),
            options = listOf(creditLimitOption) + orderedMetricSpecs.map { DataOptionSpec(it.id, it.title) },
            visibleIds = visibleOverview + if (showCreditLimitOverview) {
                setOf(CREDIT_LIMIT_OVERVIEW_ID)
            } else {
                emptySet()
            },
            onVisibleChange = { id, visible ->
                if (id == CREDIT_LIMIT_OVERVIEW_ID) vm.setCreditLimitOverviewVisible(visible)
                else vm.setDataOverviewVisible(id, visible)
            },
            onMove = { id, delta ->
                if (id != CREDIT_LIMIT_OVERVIEW_ID) vm.moveDataOverview(id, delta)
            },
            onDismiss = { showOverviewSettings = false }
        )
    }

    if (showChartSettings) {
        DataVisibilityDialog(
            title = stringResource(R.string.data_chart_settings),
            options = orderedChartSpecs.map { DataOptionSpec(it.id, it.title) },
            visibleIds = visibleCharts,
            onVisibleChange = vm::setDataChartVisible,
            onMove = vm::moveDataChart,
            onDismiss = { showChartSettings = false }
        )
    }

    if (showAbnormalCards) {
        AlertDialog(
            onDismissRequest = { showAbnormalCards = false },
            title = { Text(stringResource(R.string.abnormal_cards)) },
            text = {
                if (abnormalCards.isEmpty()) {
                    Text(stringResource(R.string.no_abnormal_cards))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(abnormalCards, key = { it.id }) { card ->
                            val statusLabel = when (card.status) {
                                "frozen" -> frozenLabel
                                "pending" -> pendingLabel
                                "cancelled" -> cancelledLabel
                                else -> card.status
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape).background(
                                        when (card.status) {
                                            "frozen" -> ColorFrozen
                                            "pending" -> ColorPending
                                            "cancelled" -> ColorRed
                                            else -> cs.onSurfaceVariant
                                        }
                                    )
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(card.cardTypeName.ifBlank { card.bank }, fontSize = 13.sp, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${card.bank}${if (card.tail.isNotBlank()) " *${card.tail}" else ""}", fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(statusLabel, fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbnormalCards = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun CreditLimitOverviewPanel(
    cardCount: Int,
    totalLimit: Double,
    currency: String,
    groupMode: String,
    bankItems: List<CreditLimitBankItem>
) {
    val cs = MaterialTheme.colorScheme
    val formattedTotal = remember(totalLimit, currency) {
        AssetCalculator.formatMoney(totalLimit, currency)
    }
    val totalFontSize = when {
        formattedTotal.length <= 10 -> 22.sp
        formattedTotal.length <= 15 -> 19.sp
        else -> 16.sp
    }

    AppPanel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                title = stringResource(R.string.credit_limit_overview),
                subtitle = stringResource(R.string.credit_limit_base_currency, currency)
            )
            HorizontalDivider(color = cs.outline.copy(alpha = 0.18f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CreditLimitSummary(
                    label = stringResource(R.string.credit_card_count),
                    value = cardCount.toString(),
                    valueColor = cs.primary,
                    modifier = Modifier.weight(0.8f)
                )
                CreditLimitSummary(
                    label = stringResource(R.string.total_credit_limit),
                    value = formattedTotal,
                    valueColor = cs.primary,
                    valueFontSize = totalFontSize,
                    modifier = Modifier.weight(1.2f)
                )
            }
            HorizontalDivider(color = cs.outline.copy(alpha = 0.18f))
            Text(
                stringResource(
                    if (groupMode == "bank") R.string.bank_credit_limit_distribution
                    else R.string.card_credit_limit_distribution
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            if (bankItems.isEmpty()) {
                Text(
                    stringResource(R.string.no_credit_limit_data),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            } else {
                val maxAmount = bankItems.maxOf { it.amount }.coerceAtLeast(1.0)
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    bankItems.forEach { item ->
                        val amountText = remember(item.amount, currency) {
                            AssetCalculator.formatMoney(item.amount, currency)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val tailsText = item.tails.joinToString("、") { "••$it" }
                                Text(
                                    if (tailsText.isBlank()) item.bank else "${item.bank} · $tailsText",
                                    fontSize = 12.sp,
                                    color = cs.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    amountText,
                                    fontSize = if (amountText.length <= 15) 12.sp else 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(cs.surfaceVariant)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((item.amount / maxAmount).toFloat().coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cs.primary.copy(alpha = 0.82f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditLimitSummary(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 22.sp
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DataVisibilityDialog(
    title: String,
    options: List<DataOptionSpec>,
    visibleIds: Set<String>,
    onVisibleChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.drag_to_reorder), fontSize = 12.sp, color = cs.onSurfaceVariant)
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(options, key = { it.id }) { option ->
                        var dragOffset by remember(option.id) { mutableStateOf(0f) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onVisibleChange(option.id, option.id !in visibleIds) }
                                .then(
                                    if (option.reorderable) {
                                        Modifier.pointerInput(option.id, options) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { dragOffset = 0f },
                                                onDragCancel = { dragOffset = 0f },
                                                onDragEnd = { dragOffset = 0f },
                                                onDrag = { _, dragAmount ->
                                                    dragOffset += dragAmount.y
                                                    if (dragOffset > 46f) {
                                                        onMove(option.id, 1)
                                                        dragOffset = 0f
                                                    } else if (dragOffset < -46f) {
                                                        onMove(option.id, -1)
                                                        dragOffset = 0f
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (option.reorderable) {
                                Icon(Icons.Default.DragIndicator, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            } else {
                                Spacer(Modifier.size(22.dp))
                            }
                            Checkbox(
                                checked = option.id in visibleIds,
                                onCheckedChange = { onVisibleChange(option.id, it) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(option.title, fontSize = 14.sp, color = cs.onSurface)
                                option.modeLabel?.let { modeLabel ->
                                    Text(
                                        modeLabel,
                                        fontSize = 11.sp,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            option.modeChecked?.let { checked ->
                                Switch(
                                    checked = checked,
                                    onCheckedChange = option.onModeChange
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}

@Composable
private fun HistogramPanel(spec: ChartSpec, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    AppPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(spec.title, trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.BarChart, null, tint = cs.primary, modifier = Modifier.size(15.dp))
                    Text(spec.total.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                }
            })
            if (spec.total <= 0 || spec.items.isEmpty()) {
                Text(stringResource(R.string.no_data_to_display), fontSize = 12.sp, color = cs.onSurfaceVariant)
            } else {
                Histogram(items = spec.items)
            }
        }
    }
}

@Composable
private fun Histogram(items: List<ChartItem>) {
    val cs = MaterialTheme.colorScheme
    val maxCount = items.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth().height(118.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { item ->
            val fraction = (item.count.toFloat() / maxCount).coerceIn(0.02f, 1f)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(item.count.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(16.dp)
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(item.color.copy(alpha = 0.86f))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.label,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
