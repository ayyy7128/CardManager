package com.cardmanager.ui.screen

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
import com.cardmanager.R
import com.cardmanager.data.PiggyEntry
import com.cardmanager.data.Task
import com.cardmanager.ui.components.AppPanel
import com.cardmanager.ui.components.EmptyState
import com.cardmanager.ui.components.SectionHeader
import com.cardmanager.ui.components.bottomSpacer
import com.cardmanager.ui.components.responsiveDialogWidth
import com.cardmanager.ui.components.screenPaddingFor
import com.cardmanager.ui.theme.*
import com.cardmanager.viewmodel.MainViewModel
import com.cardmanager.viewmodel.PiggyTaskSyncRule
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PiggyScreen(vm: MainViewModel) {
    val entries by vm.piggyEntries.collectAsState()
    val total   by vm.piggyTotal.collectAsState()
    val pigCard by vm.pigCardId.collectAsState()
    val piggyTask by vm.piggyTaskId.collectAsState()
    val piggySyncRules by vm.piggyTaskSyncRules.collectAsState()
    val cards   by vm.cards.collectAsState()
    val tasks   by vm.tasks.collectAsState()
    val cs = MaterialTheme.colorScheme
    val df = remember { DecimalFormat("#,##0.00") }
    val noLinkedCard = stringResource(R.string.no_linked_card)
    val notSet = stringResource(R.string.not_set)
    val withdrawLabel = stringResource(R.string.withdraw)
    val noLinkedTask = stringResource(R.string.no_linked_task)

    var amtStr  by remember { mutableStateOf("") }
    var desc    by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<PiggyEntry?>(null) }
    var showCardPicker by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    val boundTaskIds = piggyTask.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val investTasks = tasks.filter { it.isInvest }
    val boundTaskNames = investTasks.filter { it.id in boundTaskIds }.map { it.name }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }
    LaunchedEffect(piggyTask, piggySyncRules, tasks) {
        if (boundTaskIds.isNotEmpty()) {
            vm.syncPiggyTasksToToday()
        }
    }

    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagePadding = screenPaddingFor(maxWidth)

    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── 余额卡 ────────────────────────────────────────
        item {
            AppPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¥ ${df.format(total)}", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = ColorGold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.linked_card), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Text(if (pigCard.isEmpty()) notSet else vm.cardName(pigCard),
                            fontSize = 12.sp, color = cs.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showCardPicker = true }.padding(vertical = 2.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Edit, null, tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp).clickable { showCardPicker = true })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.linked_task), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Text(
                            when {
                                boundTaskNames.isEmpty() -> noLinkedTask
                                boundTaskNames.size == 1 -> boundTaskNames.first()
                                else -> stringResource(R.string.count_items, boundTaskNames.size)
                            },
                            fontSize = 12.sp,
                            color = cs.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showTaskPicker = true }
                                .padding(vertical = 2.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Edit, null, tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp).clickable { showTaskPicker = true })
                    }
                }
            }
        }

        // ── 输入区（单行紧凑）────────────────────────────
        item {
            AppPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.quick_record), subtitle = stringResource(R.string.quick_record_subtitle))
                // 金额 + 两个按钮同一行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amtStr, onValueChange = { amtStr = it },
                        placeholder = { Text(stringResource(R.string.amount)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        prefix = { Text("¥ ") },
                        singleLine = true
                    )
                    Button(onClick = {
                        val amt = amtStr.toDoubleOrNull() ?: return@Button
                        vm.addPiggyEntry(amt, desc); amtStr = ""; desc = ""
                    }, modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorActive),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(stringResource(R.string.deposit))
                    }
                    Button(onClick = {
                        val amt = amtStr.toDoubleOrNull() ?: return@Button
                        if (amt > total) return@Button
                        vm.addPiggyEntry(-amt, desc.ifEmpty { withdrawLabel }); amtStr = ""; desc = ""
                    }, modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorRed),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(withdrawLabel)
                    }
                }
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    placeholder = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), singleLine = true
                )
            }
            }
        }

        // ── 记录列表 ──────────────────────────────────────
        if (entries.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.records), subtitle = stringResource(R.string.record_count, entries.size)) }
            items(entries, key = { it.id }) { entry ->
                val isOut = entry.amount < 0
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(cs.surface).border(1.dp, cs.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${if (isOut) "－" else "＋"}¥${df.format(Math.abs(entry.amount))}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (isOut) ColorRed else ColorGold)
                        if (entry.desc.isNotEmpty()) Text(entry.desc, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.cardId.isNotEmpty()) Text("🏦 ${vm.cardName(entry.cardId)}", fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.date, fontSize = 11.sp, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Row {
                        IconButton(onClick = { editing = entry }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                        IconButton(onClick = { vm.deletePiggyEntry(entry) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint = ColorRed, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        } else {
            item { EmptyState(Icons.Default.Savings, stringResource(R.string.no_records_title), stringResource(R.string.no_records_body)) }
        }
        bottomSpacer(24.dp)
    }
    }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
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

    // 卡片选择 Dialog
    if (showCardPicker) {
        Dialog(onDismissRequest = { showCardPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.responsiveDialogWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.select_linked_card), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    DropdownMenuItem(text = { Text(stringResource(R.string.unlink_card)) }, onClick = { vm.setPigCard(""); showCardPicker = false })
                    HorizontalDivider()
                    cards.forEach { c ->
                        DropdownMenuItem(text = { Text(vm.cardName(c.id)) }, onClick = { vm.setPigCard(c.id); showCardPicker = false })
                    }
                }
            }
        }
    }

    if (showTaskPicker) {
        Dialog(onDismissRequest = { showTaskPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.responsiveDialogWidth()) {
                var selectedTaskIds by remember(piggyTask) { mutableStateOf(boundTaskIds) }
                var syncRules by remember(piggyTask, piggySyncRules) {
                    mutableStateOf(
                        investTasks.associate { task ->
                            task.id to (piggySyncRules[task.id] ?: PiggyTaskSyncRule("date", LocalDate.now().toString()))
                        }
                    )
                }
                Column(
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.select_linked_task), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.unlink_task)) },
                        onClick = { selectedTaskIds = emptySet() }
                    )
                    HorizontalDivider()
                    if (investTasks.isEmpty()) {
                        Text(stringResource(R.string.no_invest_tasks), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        investTasks.forEach { task ->
                            val selected = task.id in selectedTaskIds
                            val rule = syncRules[task.id] ?: PiggyTaskSyncRule("date", LocalDate.now().toString())
                            PiggyTaskSyncRow(
                                task = task,
                                selected = selected,
                                rule = rule,
                                onSelectedChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                onRuleChange = { next ->
                                    syncRules = syncRules + (task.id to next)
                                }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showTaskPicker = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.cancel)) }
                        Button(
                            onClick = {
                                vm.setPiggyTasksWithSyncRules(selectedTaskIds, syncRules)
                                showTaskPicker = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PiggyTaskSyncRow(
    task: Task,
    selected: Boolean,
    rule: PiggyTaskSyncRule,
    onSelectedChange: (Boolean) -> Unit,
    onRuleChange: (PiggyTaskSyncRule) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Column(Modifier.weight(1f)) {
                Text(task.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                Text(
                    "\u00a5${kotlin.math.abs(task.investAmount)}",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Column(Modifier.padding(start = 42.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = rule.mode == "start",
                        onClick = { onRuleChange(rule.copy(mode = "start")) }
                    )
                    Text(stringResource(R.string.piggy_sync_from_task_start), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = rule.mode != "start",
                        onClick = { onRuleChange(rule.copy(mode = "date")) }
                    )
                    Text(
                        stringResource(R.string.piggy_sync_from_custom_date),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rule.mode != "start") {
                    PiggyDateField(
                        value = rule.date.ifBlank { LocalDate.now().toString() },
                        onValueChange = { onRuleChange(rule.copy(mode = "date", date = it)) }
                    )
                }
            }
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
