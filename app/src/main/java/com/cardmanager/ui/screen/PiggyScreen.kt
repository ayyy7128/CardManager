package com.cardmanager.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardmanager.R
import com.cardmanager.data.AssetCalculator
import com.cardmanager.data.AssetLogType
import com.cardmanager.data.AssetOverrideLog
import com.cardmanager.data.AssetPlan
import com.cardmanager.data.AssetPlanCodec
import com.cardmanager.data.AssetPlanStatus
import com.cardmanager.data.AssetRatePlan
import com.cardmanager.data.Card
import com.cardmanager.data.PiggyEntry
import com.cardmanager.ui.components.AppPanel
import com.cardmanager.ui.components.CreateFabMenu
import com.cardmanager.ui.components.CreateFabMenuItem
import com.cardmanager.ui.components.EmptyState
import com.cardmanager.ui.components.SectionHeader
import com.cardmanager.ui.components.bottomSpacer
import com.cardmanager.ui.components.responsiveDialogWidth
import com.cardmanager.ui.components.screenPaddingFor
import com.cardmanager.ui.theme.*
import com.cardmanager.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal sealed class AssetOverlay {
    data class Detail(val plan: AssetPlan) : AssetOverlay()
    data class Editor(val plan: AssetPlan?) : AssetOverlay()
}

@Composable
internal fun AssetOverlayHost(
    overlay: AssetOverlay?,
    cards: List<Card>,
    cardName: (String) -> String,
    logs: (AssetPlan) -> List<com.cardmanager.data.AssetTransactionLog>,
    amount: (AssetPlan) -> Double,
    onDismissDetail: () -> Unit,
    onEditPlan: (AssetPlan) -> Unit,
    onAdjustment: (AssetPlan) -> Unit,
    onDismissEditor: () -> Unit,
    onSavePlan: (AssetPlan) -> Unit,
    onDeletePlan: (AssetPlan) -> Unit
) {
    overlay?.let { overlayState ->
        Dialog(
            onDismissRequest = {
                when (overlayState) {
                    is AssetOverlay.Detail -> onDismissDetail()
                    is AssetOverlay.Editor -> onDismissEditor()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            AnimatedContent(
                targetState = overlayState,
                modifier = Modifier.fillMaxSize(),
                label = "asset_overlay_transition",
                transitionSpec = {
                    val duration = 260
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                }
            ) { page ->
                when (page) {
                    is AssetOverlay.Detail -> {
                        val plan = page.plan
                        AssetPlanDetailDialog(
                            plan = plan,
                            logs = logs(plan),
                            amount = amount(plan),
                            linkedCardName = cardName(plan.cardId),
                            onDismiss = onDismissDetail,
                            onEdit = { onEditPlan(plan) },
                            onAdjustment = onAdjustment,
                            fullScreen = true
                        )
                    }

                    is AssetOverlay.Editor -> {
                        val plan = page.plan
                        AssetPlanEditorDialog(
                            initial = plan,
                            cards = cards,
                            cardName = cardName,
                            onDismiss = onDismissEditor,
                            onSave = onSavePlan,
                            onDelete = plan?.let { { onDeletePlan(it) } },
                            fullScreen = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PiggyScreen(vm: MainViewModel) {
    val entries by vm.piggyEntries.collectAsState()
    val assetPlans by vm.assetPlans.collectAsState()
    val vaultCurrency by vm.vaultCurrency.collectAsState()
    val exchangeRates by vm.exchangeRates.collectAsState()
    val assetTradingCalendarVersion by vm.assetTradingCalendarVersion.collectAsState()
    val cards   by vm.cards.collectAsState()
    val cs = MaterialTheme.colorScheme
    val withdrawLabel = stringResource(R.string.withdraw)

    var amtStr  by remember { mutableStateOf("") }
    var desc    by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<PiggyEntry?>(null) }
    var editingAsset by remember { mutableStateOf<AssetPlan?>(null) }
    var viewingAsset by remember { mutableStateOf<AssetPlan?>(null) }
    var showAssetEditor by remember { mutableStateOf(false) }
    var showQuickRecord by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showQuickRecordDetail by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val runningAssetPlans = assetPlans.filter { it.status != AssetPlanStatus.STOPPED }
    val archivedAssetPlans = assetPlans.filter { it.status == AssetPlanStatus.STOPPED }
    val manualEntries = remember(entries) { entries.filter(::isManualQuickRecordEntry) }
    val vaultTotal = remember(vaultCurrency, exchangeRates, entries, assetPlans, assetTradingCalendarVersion) { vm.vaultTotalIn(vaultCurrency) }
    val assetOverlay = when {
        showAssetEditor -> AssetOverlay.Editor(editingAsset)
        viewingAsset != null -> AssetOverlay.Detail(viewingAsset!!)
        else -> null
    }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagePadding = screenPaddingFor(maxWidth)

    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AppPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            AssetCalculator.formatMoney(vaultTotal, vaultCurrency),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorGold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { vm.cycleVaultCurrency() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(vaultCurrency, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item { SectionHeader("投资项目", subtitle = "${runningAssetPlans.size} 个运行") }
        if (manualEntries.isNotEmpty()) {
            item {
                QuickRecordProjectRow(
                    amount = manualEntries.sumOf { it.amount },
                    convertedAmount = vm.convertMoney(manualEntries.sumOf { it.amount }, "CNY", vaultCurrency),
                    targetCurrency = vaultCurrency,
                    onClick = { showQuickRecordDetail = true }
                )
            }
        }
        if (runningAssetPlans.isNotEmpty()) {
            items(runningAssetPlans, key = { "asset_${it.id}" }) { plan ->
                val amount = vm.assetPlanDisplayAmount(plan)
                AssetPlanRow(
                    plan = plan,
                    amount = amount,
                    linkedCardName = vm.cardName(plan.cardId),
                    convertedAmount = vm.convertMoney(amount, plan.currency, vaultCurrency),
                    targetCurrency = vaultCurrency,
                    onClick = { viewingAsset = plan },
                    onEdit = { editingAsset = plan; showAssetEditor = true },
                    onArchive = { vm.archiveAssetPlan(plan, plan.countInTotal) },
                    onRestore = { vm.restoreAssetPlan(plan) },
                    onDelete = { vm.deleteAssetPlan(plan) }
                )
            }
        } else {
            item { EmptyState(Icons.Default.Savings, "还没有投资项目", "点右下角加号新增投资任务") }
        }
        if (archivedAssetPlans.isNotEmpty()) {
            item { SectionHeader("归档项目", subtitle = "${archivedAssetPlans.size} 个计划") }
            items(archivedAssetPlans, key = { "archived_asset_${it.id}" }) { plan ->
                val amount = vm.assetPlanDisplayAmount(plan)
                AssetPlanRow(
                    plan = plan,
                    amount = amount,
                    linkedCardName = vm.cardName(plan.cardId),
                    convertedAmount = vm.convertMoney(amount, plan.currency, vaultCurrency),
                    targetCurrency = vaultCurrency,
                    onClick = { viewingAsset = plan },
                    onEdit = { editingAsset = plan; showAssetEditor = true },
                    onArchive = { vm.archiveAssetPlan(plan, plan.countInTotal) },
                    onRestore = { vm.restoreAssetPlan(plan) },
                    onDelete = { vm.deleteAssetPlan(plan) }
                )
            }
        }
        bottomSpacer(24.dp)
    }
    }
        CreateFabMenu(
            expanded = showCreateMenu,
            onExpandedChange = { showCreateMenu = it },
            contentDescription = "新增",
            items = listOf(
                CreateFabMenuItem("快速记录", Icons.Default.Edit) { showQuickRecord = true },
                CreateFabMenuItem("投资任务", Icons.Default.AccountBalance) { editingAsset = null; showAssetEditor = true }
            ),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    AssetOverlayHost(
        overlay = assetOverlay,
        cards = cards,
        cardName = vm::cardName,
        logs = { vm.assetPlanLogs(it) },
        amount = { vm.assetPlanDisplayAmount(it) },
        onDismissDetail = { viewingAsset = null },
        onEditPlan = { plan -> editingAsset = plan; viewingAsset = null; showAssetEditor = true },
        onAdjustment = { updated -> vm.updateAssetPlan(updated); viewingAsset = updated },
        onDismissEditor = { showAssetEditor = false; editingAsset = null },
        onSavePlan = { plan ->
            if (editingAsset == null) vm.addAssetPlan(plan) else vm.updateAssetPlan(plan)
            showAssetEditor = false
            editingAsset = null
        },
        onDeletePlan = { plan -> vm.deleteAssetPlan(plan); showAssetEditor = false; editingAsset = null }
    )

    if (showQuickRecordDetail) {
        QuickRecordDetailDialog(
            entries = manualEntries,
            total = manualEntries.sumOf { it.amount },
            convertedTotal = vm.convertMoney(manualEntries.sumOf { it.amount }, "CNY", vaultCurrency),
            targetCurrency = vaultCurrency,
            onDismiss = { showQuickRecordDetail = false },
            onEdit = { editing = it },
            onDelete = { vm.deletePiggyEntry(it) }
        )
    }

    if (showQuickRecord) {
        QuickRecordDialog(
            amount = amtStr,
            desc = desc,
            total = vm.vaultTotalIn("CNY"),
            withdrawLabel = withdrawLabel,
            onAmountChange = { amtStr = it },
            onDescChange = { desc = it },
            onDismiss = { showQuickRecord = false },
            onDeposit = {
                val amt = amtStr.toDoubleOrNull()
                if (amt != null) {
                    vm.addPiggyEntry(amt, desc)
                    amtStr = ""
                    desc = ""
                    showQuickRecord = false
                }
            },
            onWithdraw = {
                val amt = amtStr.toDoubleOrNull()
                if (amt != null && amt <= vm.vaultTotalIn("CNY")) {
                    vm.addPiggyEntry(-amt, desc.ifEmpty { withdrawLabel })
                    amtStr = ""
                    desc = ""
                    showQuickRecord = false
                }
            }
        )
    }

    // 编辑记录 Dialog
    editing?.let { e ->
        var newAmt  by remember { mutableStateOf(Math.abs(e.amount).toString()) }
        var newDesc by remember { mutableStateOf(e.desc) }
        Dialog(onDismissRequest = { editing = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.responsiveDialogWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.edit_record), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(value = newAmt, onValueChange = { newAmt = it }, label = { Text(stringResource(R.string.amount)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = null }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            val amt = newAmt.toDoubleOrNull() ?: return@Button
                            val signed = if (e.amount < 0) -amt else amt
                            vm.updatePiggyEntry(e.copy(amount = signed, desc = newDesc)); editing = null
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }

}

@Composable
private fun QuickRecordDialog(
    amount: String,
    desc: String,
    total: Double,
    withdrawLabel: String,
    onAmountChange: (String) -> Unit,
    onDescChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.responsiveDialogWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.quick_record), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.amount)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    prefix = { Text("¥ ") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = onDescChange,
                    label = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = onDeposit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorActive)
                    ) {
                        Text(stringResource(R.string.deposit))
                    }
                    Button(
                        onClick = onWithdraw,
                        enabled = amount.toDoubleOrNull()?.let { it <= total } ?: false,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorRed)
                    ) {
                        Text(withdrawLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRecordProjectRow(
    amount: Double,
    convertedAmount: Double,
    targetCurrency: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(.35f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("快速记录", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Text("手动添加的收入和支出", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                AssetCalculator.formatMoney(amount, "CNY"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (amount >= 0.0) ColorGold else ColorRed
            )
            if (!targetCurrency.equals("CNY", ignoreCase = true)) {
                Text(
                    AssetCalculator.formatMoney(convertedAmount, targetCurrency),
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QuickRecordDetailDialog(
    entries: List<PiggyEntry>,
    total: Double,
    convertedTotal: Double,
    targetCurrency: String,
    onDismiss: () -> Unit,
    onEdit: (PiggyEntry) -> Unit,
    onDelete: (PiggyEntry) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    BackHandler { onDismiss() }
    Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChevronLeft, null, tint = cs.onSurface)
                    }
                    Text("快速记录", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        AssetCalculator.formatMoney(total, "CNY"),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (total >= 0.0) ColorGold else ColorRed
                    )
                    if (!targetCurrency.equals("CNY", ignoreCase = true)) {
                        Text(
                            AssetCalculator.formatMoney(convertedTotal, targetCurrency),
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item { EmptyState(Icons.Default.Edit, "还没有快速记录", "点击右下角加号添加") }
            } else {
                items(entries.sortedByDescending { it.timestamp }, key = { it.id }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surface)
                            .border(1.dp, cs.outline.copy(.24f), RoundedCornerShape(12.dp))
                            .clickable { onEdit(entry) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                entry.desc.ifBlank { if (entry.amount >= 0.0) stringResource(R.string.deposit) else stringResource(R.string.withdraw) },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface
                            )
                            Text(entry.date, fontSize = 11.sp, color = cs.onSurfaceVariant)
                        }
                        Text(
                            "${if (entry.amount >= 0) "+" else ""}${AssetCalculator.formatMoney(entry.amount, "CNY")}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.amount >= 0.0) ColorActive else ColorRed
                        )
                        IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = ColorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            bottomSpacer(8.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PiggyDateField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(16.dp))
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = piggyDateStringToMillis(value))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onValueChange(piggyMillisToDateString(it)) }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun piggyDateStringToMillis(value: String): Long? =
    runCatching {
        LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()

private fun piggyMillisToDateString(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()

@Composable
fun AssetPlanRow(
    plan: AssetPlan,
    amount: Double,
    linkedCardName: String = "",
    convertedAmount: Double? = null,
    targetCurrency: String = plan.currency,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val stopped = plan.status == AssetPlanStatus.STOPPED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(.35f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(plan.name.ifBlank { "未命名计划" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                if (stopped) {
                    Text("已归档", fontSize = 10.sp, color = ColorRed)
                }
            }
            val meta = listOf(plan.platform, plan.category, plan.code).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (linkedCardName.isNotBlank()) Text(linkedCardName, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(assetCycleText(plan), fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(AssetCalculator.formatMoney(amount, plan.currency), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (stopped) cs.onSurfaceVariant else ColorGold)
            if (convertedAmount != null && !targetCurrency.equals(plan.currency, ignoreCase = true)) {
                Text(AssetCalculator.formatMoney(convertedAmount, targetCurrency), fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = cs.onSurfaceVariant) }
                IconButton(onClick = if (stopped) onRestore else onArchive, modifier = Modifier.size(30.dp)) {
                    Icon(if (stopped) Icons.Default.Restore else Icons.Default.Inventory2, null, modifier = Modifier.size(14.dp), tint = cs.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = ColorRed) }
            }
        }
    }
}

@Composable
fun AssetPlanDetailDialog(
    plan: AssetPlan,
    logs: List<com.cardmanager.data.AssetTransactionLog>,
    amount: Double,
    linkedCardName: String = "",
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAdjustment: (AssetPlan) -> Unit,
    fullScreen: Boolean = false
) {
    var showAdjustment by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<com.cardmanager.data.AssetTransactionLog?>(null) }
    val cs = MaterialTheme.colorScheme
    val visibleLogs = remember(logs) {
        logs.filter { it.type != AssetLogType.SKIP_WEEKEND && it.type != AssetLogType.SKIP_PAUSE }
            .reversed()
    }
    if (fullScreen) {
        BackHandler { onDismiss() }
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.ChevronLeft, null, tint = cs.onBackground, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            plan.name.ifBlank { "未命名计划" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = cs.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOf(assetCycleText(plan), linkedCardName).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Edit, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(24.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .border(1.dp, cs.outline.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("当前金额", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Text(
                            AssetCalculator.formatMoney(amount, plan.currency),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showAdjustment = true },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Tune, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("资金调整")
                            }
                            OutlinedButton(
                                onClick = onEdit,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("编辑计划")
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(20.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .border(1.dp, cs.outline.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("计划信息", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        AssetPlanInfoRow("周期", assetCycleText(plan))
                        if (linkedCardName.isNotBlank()) AssetPlanInfoRow("关联卡片", linkedCardName)
                        AssetPlanInfoRow("币种", plan.currency)
                        if (plan.platform.isNotBlank()) AssetPlanInfoRow("平台", plan.platform)
                        if (plan.category.isNotBlank()) AssetPlanInfoRow("种类", plan.category)
                        if (plan.code.isNotBlank()) AssetPlanInfoRow("代码", plan.code)
                    }
                }
            }

            item { SectionHeader("流水明细", subtitle = "${visibleLogs.size} 条") }

            if (visibleLogs.isEmpty()) {
                item {
                    EmptyState(Icons.Default.Info, "暂无流水", "计划产生记录后会显示在这里")
                }
            } else {
                items(visibleLogs) { log ->
                    val editable = log.type == AssetLogType.PERIODIC || log.type == AssetLogType.ADJUSTMENT || log.type == AssetLogType.POSTPONED
                    AssetLogRow(log = log, editable = editable, onClick = { editingLog = log })
                }
            }
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(20.dp)) }
        }
    }
    if (fullScreen) {
        Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp))
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = RoundedCornerShape(18.dp), color = cs.surface, modifier = Modifier.responsiveDialogWidth()) {
                content(Modifier.heightIn(max = 640.dp).padding(20.dp))
            }
        }
    }
    if (showAdjustment) {
        AssetAdjustmentDialog(
            onDismiss = { showAdjustment = false },
            onSave = { date, amount, note ->
                val next = AssetPlanCodec.decodeAdjustments(plan.adjustmentsJson) + com.cardmanager.data.AssetAdjustment(date, amount, note)
                onAdjustment(plan.copy(adjustmentsJson = AssetPlanCodec.encodeAdjustments(next)))
                showAdjustment = false
            }
        )
    }
    editingLog?.let { log ->
        AssetLogEditDialog(
            log = log,
            onDismiss = { editingLog = null },
            onSave = { amount, status, note ->
                if (log.type == AssetLogType.ADJUSTMENT) {
                    val next = AssetPlanCodec.decodeAdjustments(plan.adjustmentsJson).map { adj ->
                        if (adj.date == log.date && adj.amount == log.amount && adj.note == log.note) {
                            adj.copy(amount = amount, note = note)
                        } else {
                            adj
                        }
                    }
                    onAdjustment(plan.copy(adjustmentsJson = AssetPlanCodec.encodeAdjustments(next)))
                } else {
                    val next = AssetPlanCodec.decodeOverrides(plan.overridesJson)
                        .filterNot { it.date == log.date && (it.type == log.type || it.type.isBlank()) } +
                        AssetOverrideLog(log.date, amount, status.ifBlank { log.status }, note, type = log.type)
                    onAdjustment(plan.copy(overridesJson = AssetPlanCodec.encodeOverrides(next)))
                }
                editingLog = null
            },
            onDelete = {
                if (log.type == AssetLogType.ADJUSTMENT) {
                    val next = AssetPlanCodec.decodeAdjustments(plan.adjustmentsJson).filterNot { adj ->
                        adj.date == log.date && adj.amount == log.amount && adj.note == log.note
                    }
                    onAdjustment(plan.copy(adjustmentsJson = AssetPlanCodec.encodeAdjustments(next)))
                } else {
                    val next = AssetPlanCodec.decodeOverrides(plan.overridesJson)
                        .filterNot { it.date == log.date && (it.type == log.type || it.type.isBlank()) } +
                        AssetOverrideLog(log.date, 0.0, "已删除", "", isDeleted = true, type = log.type)
                    onAdjustment(plan.copy(overridesJson = AssetPlanCodec.encodeOverrides(next)))
                }
                editingLog = null
            }
        )
    }
}

@Composable
private fun AssetPlanInfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AssetLogRow(
    log: com.cardmanager.data.AssetTransactionLog,
    editable: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val amountColor = if (log.amount >= 0) ColorActive else ColorRed
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(amountColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (log.amount >= 0) Icons.Default.Add else Icons.Default.Remove,
                    null,
                    tint = amountColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(log.status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(
                    "${log.date}${if (log.note.isNotBlank()) " · ${log.note}" else ""}",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${if (log.amount >= 0) "+" else ""}${AssetCalculator.formatMoney(log.amount, log.currency)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                if (editable) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AssetAdjustmentDialog(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.responsiveDialogWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("资金调整", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额（可为负数）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                PiggyDateField(value = date, onValueChange = { date = it })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.note_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { amount.toDoubleOrNull()?.let { onSave(date, it, note) } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
private fun AssetLogEditDialog(
    log: com.cardmanager.data.AssetTransactionLog,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var amount by remember(log) { mutableStateOf(AssetCalculator.displayAmount(log.amount)) }
    var status by remember(log) { mutableStateOf(log.status) }
    var note by remember(log) { mutableStateOf(log.note) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.responsiveDialogWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("编辑记录", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(log.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("状态") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.note_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.delete), color = ColorRed) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { amount.toDoubleOrNull()?.let { onSave(it, status, note) } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AssetPlanEditorDialog(
    initial: AssetPlan?,
    cards: List<Card> = emptyList(),
    cardName: (String) -> String = { "" },
    onDismiss: () -> Unit,
    onSave: (AssetPlan) -> Unit,
    onDelete: (() -> Unit)?,
    fullScreen: Boolean = false
) {
    val today = LocalDate.now().toString()
    val initialRatePlans = remember(initial) {
        AssetPlanCodec.decodeRatePlans(initial?.ratePlansJson.orEmpty()).ifEmpty {
            listOf(AssetRatePlan(initial?.startDate?.ifBlank { today } ?: today, 0.0))
        }
    }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var platform by remember(initial) { mutableStateOf(initial?.platform ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "") }
    var code by remember(initial) { mutableStateOf(initial?.code ?: "") }
    var currency by remember(initial) { mutableStateOf(initial?.currency ?: "CNY") }
    var cardId by remember(initial) { mutableStateOf(initial?.cardId ?: "") }
    var initialCapital by remember(initial) { mutableStateOf(initial?.initialCapital?.takeIf { it != 0.0 }?.let { AssetCalculator.displayAmount(it) } ?: "") }
    var initialDate by remember(initial) { mutableStateOf(initial?.initialDate?.ifBlank { initialRatePlans.first().startDate } ?: initialRatePlans.first().startDate) }
    var syncInitialDate by remember(initial) { mutableStateOf(initial == null || initial.initialDate.isBlank() || initial.initialDate == initialRatePlans.first().startDate) }
    val ratePlans = remember(initial) { mutableStateListOf<AssetRatePlan>().apply { addAll(initialRatePlans) } }
    var frequency by remember(initial) {
        mutableStateOf(
            when {
                initial == null -> "daily"
                initial.monthlyDay > 0 -> "monthly"
                initial.weeklyDay > 0 -> "weekly"
                initial.cycleDays == 0 -> "once"
                initial.cycleDays == 1 -> "daily"
                else -> "ndays"
            }
        )
    }
    var intervalDays by remember(initial) { mutableStateOf((initial?.cycleDays ?: 1).coerceAtLeast(1).toString()) }
    var monthlyDay by remember(initial) { mutableStateOf((initial?.monthlyDay ?: 1).coerceAtLeast(1).toString()) }
    var weeklyDay by remember(initial) { mutableStateOf((initial?.weeklyDay ?: 1).coerceIn(1, 7).toString()) }
    var skipMissingMonthlyDate by remember(initial) { mutableStateOf(initial?.skipMissingMonthlyDate ?: false) }
    var includeFirst by remember(initial) { mutableStateOf(initial?.includeFirstDay ?: true) }
    var skipWeekends by remember(initial) { mutableStateOf(initial?.skipWeekends ?: true) }
    var postpone by remember(initial) { mutableStateOf(initial?.postponeNonTrading ?: false) }
    var countInTotal by remember(initial) { mutableStateOf(initial?.countInTotal ?: true) }

    val cs = MaterialTheme.colorScheme
    if (fullScreen) {
        BackHandler { onDismiss() }
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (fullScreen) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ChevronLeft, null, tint = cs.onSurface)
                        }
                    }
                    Text(
                        if (initial == null) "新增投资任务" else "编辑投资任务",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), isError = name.isBlank())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = platform, onValueChange = { platform = it }, label = { Text("平台") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("种类") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("代码") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CNY", "USD", "HKD").forEach { curr ->
                        FilterChip(selected = currency == curr, onClick = { currency = curr }, label = { Text(curr) })
                    }
                }
                if (cards.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = if (cardId.isBlank()) stringResource(R.string.no_linked_card_action) else cardName(cardId),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.linked_card_optional)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.unlink_card)) },
                                onClick = {
                                    cardId = ""
                                    expanded = false
                                }
                            )
                            cards.forEach { card ->
                                DropdownMenuItem(
                                    text = { Text(cardName(card.id)) },
                                    onClick = {
                                        cardId = card.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(value = initialCapital, onValueChange = { initialCapital = it }, label = { Text("初始金额") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                if (initialCapital.isNotBlank() && ratePlans.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = syncInitialDate, onCheckedChange = { syncInitialDate = it })
                        Text("初始金额与首阶段同日", fontSize = 13.sp)
                    }
                    if (!syncInitialDate) {
                        Text("初始金额日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PiggyDateField(value = initialDate, onValueChange = { initialDate = it })
                    }
                }
                Text("定投阶段", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                ratePlans.forEachIndexed { index, rate ->
                    var amountText by remember(rate.startDate, rate.amount) {
                        mutableStateOf(rate.amount.takeIf { it != 0.0 }?.let { AssetCalculator.displayAmount(it) } ?: "")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("阶段 ${index + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (ratePlans.size > 1) {
                                IconButton(onClick = { ratePlans.removeAt(index) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.RemoveCircle, null, modifier = Modifier.size(16.dp), tint = ColorRed)
                                }
                            }
                        }
                        PiggyDateField(value = rate.startDate.ifBlank { today }, onValueChange = { next ->
                            ratePlans[index] = rate.copy(startDate = next)
                            if (index == 0 && syncInitialDate) initialDate = next
                        })
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { text ->
                                amountText = text
                                ratePlans[index] = rate.copy(amount = text.toDoubleOrNull() ?: 0.0)
                            },
                            label = { Text("每期金额") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
                TextButton(onClick = { ratePlans.add(AssetRatePlan(today, 0.0)) }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加阶段")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("daily" to "每天", "weekly" to "每周", "monthly" to "每月", "ndays" to "每N天", "once" to "单次").forEach { (id, label) ->
                        FilterChip(selected = frequency == id, onClick = { frequency = id }, label = { Text(label) })
                    }
                }
                when (frequency) {
                    "weekly" -> OutlinedTextField(value = weeklyDay, onValueChange = { weeklyDay = it.filter(Char::isDigit).take(1) }, label = { Text("星期（1=周一，7=周日）") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    "monthly" -> OutlinedTextField(value = monthlyDay, onValueChange = { monthlyDay = it.filter(Char::isDigit).take(2) }, label = { Text("每月几号") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    "ndays" -> OutlinedTextField(value = intervalDays, onValueChange = { intervalDays = it.filter(Char::isDigit).take(3) }, label = { Text("间隔天数") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                }
                if (frequency == "monthly" && (monthlyDay.toIntOrNull() ?: 1) > 28) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = skipMissingMonthlyDate, onCheckedChange = { skipMissingMonthlyDate = it })
                        Text("当月没有该日期时跳过", fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = includeFirst, onCheckedChange = { includeFirst = it }); Text("包含开始日期当天", fontSize = 13.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = skipWeekends, onCheckedChange = { skipWeekends = it }); Text("避开非交易日", fontSize = 13.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = postpone, onCheckedChange = { postpone = it }); Text("遇非交易日顺延", fontSize = 13.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = countInTotal, onCheckedChange = { countInTotal = it }); Text("计入小金库总额", fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onDelete?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.delete), color = ColorRed) } }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        if (name.isBlank()) return@Button
                        val sortedRates = ratePlans
                            .map { it.copy(startDate = it.startDate.ifBlank { today }) }
                            .sortedBy { it.startDate }
                        val firstStartDate = sortedRates.firstOrNull()?.startDate ?: today
                        val planSum = sortedRates.sumOf { it.amount }
                        val cycle = when (frequency) {
                            "once" -> 0
                            "daily" -> 1
                            "weekly" -> 7
                            "monthly" -> 30
                            else -> intervalDays.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }
                        val finalCycle = if (planSum == 0.0) 0 else cycle
                        val finalInitialDate = if (syncInitialDate) firstStartDate else initialDate.ifBlank { firstStartDate }
                        val plan = AssetPlan(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            cardId = cardId,
                            platform = platform.trim(),
                            category = category.trim(),
                            code = code.trim(),
                            currency = currency.ifBlank { "CNY" },
                            initialCapital = initialCapital.toDoubleOrNull() ?: 0.0,
                            initialDate = finalInitialDate,
                            ratePlansJson = AssetPlanCodec.encodeRatePlans(sortedRates),
                            pauseRangesJson = "",
                            adjustmentsJson = initial?.adjustmentsJson ?: AssetPlanCodec.encodeAdjustments(emptyList()),
                            overridesJson = initial?.overridesJson ?: AssetPlanCodec.encodeOverrides(emptyList()),
                            cycleDays = finalCycle,
                            monthlyDay = if (finalCycle != 0 && frequency == "monthly") monthlyDay.toIntOrNull()?.coerceIn(1, 31) ?: 1 else 0,
                            weeklyDay = if (finalCycle != 0 && frequency == "weekly") weeklyDay.toIntOrNull()?.coerceIn(1, 7) ?: 1 else 0,
                            skipMissingMonthlyDate = skipMissingMonthlyDate,
                            postponeNonTrading = postpone,
                            includeFirstDay = includeFirst,
                            status = initial?.status ?: AssetPlanStatus.RUNNING,
                            frozenAmount = initial?.frozenAmount ?: 0.0,
                            countInTotal = countInTotal,
                            skipWeekends = skipWeekends,
                            orderIndex = initial?.orderIndex ?: System.currentTimeMillis(),
                            startDate = firstStartDate
                        )
                        onSave(plan)
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                }
            }
    }
    if (fullScreen) {
        Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp))
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = RoundedCornerShape(18.dp), color = cs.surface, modifier = Modifier.responsiveDialogWidth()) {
                content(Modifier.padding(20.dp))
            }
        }
    }
}

fun assetCycleText(plan: AssetPlan): String {
    val amount = AssetPlanCodec.decodeRatePlans(plan.ratePlansJson).firstOrNull()?.amount ?: 0.0
    val amountText = "${AssetCalculator.displayAmount(amount)} ${plan.currency}"
    val weekdays = listOf("", "一", "二", "三", "四", "五", "六", "日")
    val cycle = when {
        plan.cycleDays == 0 && plan.monthlyDay == 0 && plan.weeklyDay == 0 -> "单次"
        plan.monthlyDay > 0 -> "每月${plan.monthlyDay}日"
        plan.weeklyDay > 0 -> "每周${weekdays[plan.weeklyDay.coerceIn(1, 7)]}"
        plan.cycleDays == 1 -> "每天"
        else -> "每${plan.cycleDays}天"
    }
    return "$amountText · $cycle"
}

private fun isManualQuickRecordEntry(entry: PiggyEntry): Boolean =
    !entry.desc.startsWith("任务同步：")
