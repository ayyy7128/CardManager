package com.cardmanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import com.cardmanager.R
import com.cardmanager.data.Task
import com.cardmanager.data.TaskHolidayPolicy
import com.cardmanager.data.TradingDayService
import com.cardmanager.ui.components.AppPanel
import com.cardmanager.ui.components.EmptyState
import com.cardmanager.ui.components.SectionHeader
import com.cardmanager.ui.components.StatusPill
import com.cardmanager.ui.components.bottomSpacer
import com.cardmanager.ui.components.responsiveDialogWidth
import com.cardmanager.ui.components.screenPaddingFor
import com.cardmanager.ui.theme.ColorActive
import com.cardmanager.ui.theme.ColorGold
import com.cardmanager.ui.theme.ColorRed
import com.cardmanager.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun CalendarScreen(vm: MainViewModel) {
    val today = LocalDate.now()
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthValue) }
    var selectedDay by remember { mutableStateOf(today.dayOfMonth) }
    var showAddTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var deletingTask by remember { mutableStateOf<Task?>(null) }
    val ctx = LocalContext.current

    val tasks by vm.tasks.collectAsState()
    var tradingDaysReady by remember(year) { mutableStateOf(TradingDayService.isLoaded(year)) }
    val taskDots by remember(year, month, tasks, tradingDaysReady) { derivedStateOf { vm.taskDatesForMonth(year, month) } }
    val postponedTaskDots by remember(year, month, tasks, tradingDaysReady) { derivedStateOf { vm.postponedTaskDatesForMonth(year, month) } }
    val dayTasks by remember(year, month, selectedDay, tasks, tradingDaysReady) { derivedStateOf { vm.tasksForDate(year, month, selectedDay) } }

    LaunchedEffect(year) {
        if (!TradingDayService.isLoaded(year)) {
            TradingDayService.loadYear(year, ctx)
        }
        tradingDaysReady = true
    }

    val cs = MaterialTheme.colorScheme
    val previousMonth = stringResource(R.string.previous_month)
    val nextMonth = stringResource(R.string.next_month)
    val newTask = stringResource(R.string.new_task)

    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val pagePadding = screenPaddingFor(maxWidth)
        val useTwoPane = maxWidth >= 720.dp || maxWidth > maxHeight

        if (useTwoPane) {
            Row(
                Modifier.fillMaxSize().background(cs.background).padding(pagePadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier.weight(0.95f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalendarMonthPanel(
                        year = year,
                        month = month,
                        selectedDay = selectedDay,
                        taskDots = taskDots,
                        postponedTaskDots = postponedTaskDots,
                        today = today,
                        tradingDaysReady = tradingDaysReady,
                        previousMonth = previousMonth,
                        nextMonth = nextMonth,
                        onPreviousMonth = {
                            if (month == 1) {
                                month = 12
                                year--
                            } else {
                                month--
                            }
                            selectedDay = 1
                        },
                        onNextMonth = {
                            if (month == 12) {
                                month = 1
                                year++
                            } else {
                                month++
                            }
                            selectedDay = 1
                        },
                        onDayClick = { selectedDay = it }
                    )
                    Spacer(Modifier.height(88.dp))
                }
                LazyColumn(
                    Modifier.weight(1.05f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    calendarTaskSections(
                        month = month,
                        selectedDay = selectedDay,
                        dayTasks = dayTasks,
                        tasks = tasks,
                        vm = vm,
                        onEdit = { editingTask = it }
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(cs.background),
                contentPadding = pagePadding,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    CalendarMonthPanel(
                        year = year,
                        month = month,
                        selectedDay = selectedDay,
                        taskDots = taskDots,
                        postponedTaskDots = postponedTaskDots,
                        today = today,
                        tradingDaysReady = tradingDaysReady,
                        previousMonth = previousMonth,
                        nextMonth = nextMonth,
                        onPreviousMonth = {
                            if (month == 1) {
                                month = 12
                                year--
                            } else {
                                month--
                            }
                            selectedDay = 1
                        },
                        onNextMonth = {
                            if (month == 12) {
                                month = 1
                                year++
                            } else {
                                month++
                            }
                            selectedDay = 1
                        },
                        onDayClick = { selectedDay = it }
                    )
                }
                calendarTaskSections(
                    month = month,
                    selectedDay = selectedDay,
                    dayTasks = dayTasks,
                    tasks = tasks,
                    vm = vm,
                    onEdit = { editingTask = it }
                )
                bottomSpacer()
            }
        }

        FloatingActionButton(
            onClick = { showAddTask = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = cs.primary
        ) {
            Icon(Icons.Default.Add, newTask, tint = Color.White)
        }
    }

    if (showAddTask) {
        TaskDialog(vm = vm, onDismiss = { showAddTask = false }) { t ->
            vm.addTask(
                t.name,
                t.freq,
                t.cardId,
                t.isInvest,
                t.investAmount,
                t.day,
                t.weekday,
                t.months,
                t.ndays,
                t.startDate,
                t.date,
                t.holidays
            )
            showAddTask = false
        }
    }
    editingTask?.let { task ->
        TaskDialog(
            vm = vm,
            initial = task,
            onDismiss = { editingTask = null },
            onDelete = {
                deletingTask = task
                editingTask = null
            }
        ) { t ->
            vm.updateTask(
                task.copy(
                    name = t.name,
                    freq = t.freq,
                    cardId = t.cardId,
                    isInvest = t.isInvest,
                    investAmount = t.investAmount,
                    day = t.day,
                    weekday = t.weekday,
                    months = t.months,
                    ndays = t.ndays,
                    startDate = t.startDate,
                    date = t.date,
                    holidays = t.holidays
                )
            )
            editingTask = null
        }
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_body, task.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteTask(task)
                        deletingTask = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorRed)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CalendarMonthPanel(
    year: Int,
    month: Int,
    selectedDay: Int,
    taskDots: Set<Int>,
    postponedTaskDots: Set<Int>,
    today: LocalDate,
    tradingDaysReady: Boolean,
    previousMonth: String,
    nextMonth: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AppPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Default.ChevronLeft, previousMonth, tint = cs.primary)
                }
                Text(
                    stringResource(R.string.month_title, year, month),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.ChevronRight, nextMonth, tint = cs.primary)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                LegendDot(ColorActive, stringResource(R.string.trading_day))
                LegendDot(ColorRed, stringResource(R.string.non_trading_day))
                LegendDot(ColorGold, stringResource(R.string.postponed_trading_day))
                LegendDot(cs.primary, stringResource(R.string.has_task))
            }
            CalendarGrid(year, month, selectedDay, taskDots, postponedTaskDots, today, tradingDaysReady, onDayClick)
        }
    }
}

private fun LazyListScope.calendarTaskSections(
    month: Int,
    selectedDay: Int,
    dayTasks: List<Task>,
    tasks: List<Task>,
    vm: MainViewModel,
    onEdit: (Task) -> Unit
) {
    item {
        SectionHeader(
            stringResource(R.string.day_tasks, month, selectedDay),
            subtitle = stringResource(R.string.count_items, dayTasks.size)
        )
    }
    if (dayTasks.isEmpty()) {
        item {
            EmptyState(
                Icons.Default.EventAvailable,
                stringResource(R.string.no_tasks_today_title),
                stringResource(R.string.no_tasks_today_body)
            )
        }
    } else {
        items(dayTasks, key = { "day_${it.id}" }) { task ->
            TaskItem(task, vm.cardName(task.cardId)) { onEdit(task) }
        }
    }

    item {
        Spacer(Modifier.height(4.dp))
        SectionHeader(
            stringResource(R.string.all_tasks),
            subtitle = stringResource(R.string.count_items, tasks.size)
        )
    }
    if (tasks.isEmpty()) {
        item {
            EmptyState(
                Icons.Default.TaskAlt,
                stringResource(R.string.no_tasks_title),
                stringResource(R.string.no_tasks_body)
            )
        }
    } else {
        items(tasks, key = { "all_${it.id}" }) { task ->
            TaskItem(task, vm.cardName(task.cardId)) { onEdit(task) }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDay: Int,
    taskDots: Set<Int>,
    postponedTaskDots: Set<Int>,
    today: LocalDate,
    tradingDaysReady: Boolean,
    onDayClick: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val firstDay = LocalDate.of(year, month, 1)
    val daysInMonth = firstDay.lengthOfMonth()
    val startDow = firstDay.dayOfWeek.value % 7
    val weekdays = listOf(
        stringResource(R.string.weekday_sun_short),
        stringResource(R.string.weekday_mon_short),
        stringResource(R.string.weekday_tue_short),
        stringResource(R.string.weekday_wed_short),
        stringResource(R.string.weekday_thu_short),
        stringResource(R.string.weekday_fri_short),
        stringResource(R.string.weekday_sat_short)
    )

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { d ->
                Text(
                    d,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val rows = (startDow + daysInMonth + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val dayNum = r * 7 + c - startDow + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = LocalDate.of(year, month, dayNum)
                        val isToday = today == date
                        val isSel = selectedDay == dayNum
                        val hasDot = dayNum in taskDots
                        val isPostponed = dayNum in postponedTaskDots
                        val isTrading = tradingDaysReady && TradingDayService.isTradingDay(date)
                        val isNonTrading = tradingDaysReady && !TradingDayService.isTradingDay(date)

                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSel -> cs.primary
                                        isToday -> cs.primary.copy(0.28f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDayClick(dayNum) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    dayNum.toString(),
                                    fontSize = 12.sp,
                                    color = when {
                                        isSel || isToday -> Color.White
                                        isNonTrading -> Color(0xFFEF4444).copy(0.7f)
                                        else -> cs.onSurface
                                    },
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasDot || isTrading || isNonTrading || isPostponed) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
                                        if (isTrading) Box(Modifier.size(3.dp).clip(CircleShape).background(ColorActive.copy(0.6f)))
                                        if (isNonTrading) Box(Modifier.size(3.dp).clip(CircleShape).background(ColorRed.copy(0.5f)))
                                        if (isPostponed) Box(Modifier.size(3.dp).clip(CircleShape).background(ColorGold.copy(0.8f)))
                                        if (hasDot) Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSel) Color.White else cs.primary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, cardName: String, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val weekdayLabels = listOf(
        stringResource(R.string.weekday_sun),
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat)
    )
    val freqLabel = when (task.freq) {
        "monthly" -> stringResource(R.string.freq_monthly_on, task.day)
        "weekly" -> stringResource(R.string.freq_weekly_on, weekdayLabels[task.weekday % 7])
        "quarterly" -> stringResource(R.string.freq_quarterly_on, task.day)
        "ndays" -> stringResource(R.string.freq_ndays_on, task.ndays)
        "once" -> task.date
        else -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable { onEdit() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Text(
                    task.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.isInvest) StatusPill(stringResource(R.string.invest), ColorGold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(freqLabel, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (task.isInvest && task.investAmount != 0.0) {
                    Text(
                        "¥${kotlin.math.abs(task.investAmount)}",
                        fontSize = 11.sp,
                        color = ColorGold
                    )
                }
            }
            if (cardName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.74f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(cardName, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    vm: MainViewModel,
    initial: Task? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (Task) -> Unit
) {
    val cards by vm.cards.collectAsState()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var freq by remember { mutableStateOf(initial?.freq ?: "monthly") }
    var cardId by remember { mutableStateOf(initial?.cardId ?: "") }
    var isInvest by remember { mutableStateOf(initial?.isInvest ?: false) }
    val initialInvestAmount = kotlin.math.abs(initial?.investAmount ?: 0.0)
    var investAmt by remember { mutableStateOf(if (initialInvestAmount != 0.0) initialInvestAmount.toString() else "") }
    var day by remember { mutableStateOf((initial?.day ?: 1).toString()) }
    var weekday by remember { mutableStateOf((initial?.weekday ?: 1).toString()) }
    var months by remember { mutableStateOf(initial?.months ?: "1,4,7,10") }
    var ndays by remember { mutableStateOf((initial?.ndays ?: 7).toString()) }
    var startDate by remember { mutableStateOf(initial?.startDate?.ifBlank { initial.date } ?: LocalDate.now().toString()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var avoidNonTradingDays by remember {
        mutableStateOf(initial?.let { !it.isInvest || TaskHolidayPolicy.avoidsNonTradingDays(it) } ?: true)
    }
    LaunchedEffect(freq, startDate) {
        if (freq == "once") date = startDate
    }

    val freqs = listOf(
        "monthly" to stringResource(R.string.freq_monthly),
        "weekly" to stringResource(R.string.freq_weekly),
        "quarterly" to stringResource(R.string.freq_quarterly),
        "ndays" to stringResource(R.string.freq_ndays),
        "once" to stringResource(R.string.freq_once)
    )
    val weekdays = listOf(
        1 to stringResource(R.string.weekday_mon),
        2 to stringResource(R.string.weekday_tue),
        3 to stringResource(R.string.weekday_wed),
        4 to stringResource(R.string.weekday_thu),
        5 to stringResource(R.string.weekday_fri),
        6 to stringResource(R.string.weekday_sat),
        7 to stringResource(R.string.weekday_sun)
    )

    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surface, modifier = Modifier.responsiveDialogWidth(620.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (initial == null) stringResource(R.string.new_task) else stringResource(R.string.edit_task),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = cs.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.task_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(stringResource(R.string.frequency), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    freqs.forEach { (v, l) ->
                        FilterChip(
                            selected = freq == v,
                            onClick = { freq = v },
                            label = { Text(l, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                DatePickerField(
                    label = stringResource(R.string.start_date_hint),
                    value = startDate,
                    onValueChange = {
                        startDate = it
                        if (freq == "once") date = it
                    }
                )

                when (freq) {
                    "monthly" -> DayOfMonthPickerField(
                        label = stringResource(R.string.monthly_day),
                        day = day,
                        onDayChange = { day = it }
                    )
                    "weekly" -> {
                        Text(stringResource(R.string.weekday), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            weekdays.forEach { (v, l) ->
                                FilterChip(
                                    selected = weekday == v.toString(),
                                    onClick = { weekday = v.toString() },
                                    label = { Text(l, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    "quarterly" -> {
                        DayOfMonthPickerField(
                            label = stringResource(R.string.quarterly_day),
                            day = day,
                            onDayChange = { day = it }
                        )
                        OutlinedTextField(
                            value = months,
                            onValueChange = { months = it },
                            label = { Text(stringResource(R.string.months_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    "ndays" -> {
                        OutlinedTextField(
                            value = ndays,
                            onValueChange = { ndays = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.interval_days)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    "once" -> Unit
                }

                if (cards.isNotEmpty()) {
                    var exp by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = it }) {
                        OutlinedTextField(
                            value = if (cardId.isEmpty()) stringResource(R.string.no_linked_card_action) else vm.cardName(cardId),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.linked_card_optional)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.unlink_card)) },
                                onClick = {
                                    cardId = ""
                                    exp = false
                                }
                            )
                            cards.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(vm.cardName(c.id)) },
                                    onClick = {
                                        cardId = c.id
                                        exp = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isInvest, onCheckedChange = { isInvest = it })
                    Text(stringResource(R.string.invest_task), fontSize = 13.sp)
                }
                if (isInvest) {
                    OutlinedTextField(
                        value = investAmt,
                        onValueChange = { raw ->
                            var hasDot = false
                            investAmt = buildString {
                                raw.forEach { ch ->
                                    when {
                                        ch.isDigit() -> append(ch)
                                        ch == '.' && !hasDot -> {
                                            append(ch)
                                            hasDot = true
                                        }
                                    }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.invest_amount_hint)) },
                        placeholder = { Text(stringResource(R.string.invest_amount_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.32f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.avoid_non_trading_days),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface
                            )
                            Text(
                                stringResource(R.string.avoid_non_trading_days_summary),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = avoidNonTradingDays,
                            onCheckedChange = { avoidNonTradingDays = it }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onDelete?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.delete), color = ColorRed)
                        }
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    Task(
                                        id = initial?.id ?: "",
                                        name = name.trim(),
                                        freq = freq,
                                        cardId = cardId,
                                        isInvest = isInvest,
                                        investAmount = (investAmt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                        day = day.toIntOrNull() ?: 1,
                                        weekday = weekday.toIntOrNull() ?: 1,
                                        months = months,
                                        ndays = (ndays.toIntOrNull() ?: 7).coerceAtLeast(1),
                                        startDate = startDate,
                                        date = date,
                                        holidays = if (isInvest && avoidNonTradingDays) {
                                            TaskHolidayPolicy.AVOID_NON_TRADING_DAYS
                                        } else if (isInvest) {
                                            TaskHolidayPolicy.ALLOW_NON_TRADING_DAYS
                                        } else {
                                            ""
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 14.sp, color = cs.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(18.dp))
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateStringToMillis(value))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onValueChange(millisToDateString(it)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfMonthPickerField(
    label: String,
    day: String,
    onDayChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val dayValue = (day.toIntOrNull() ?: 1).coerceIn(1, 31)

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${dayValue}日", fontSize = 14.sp, color = cs.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Icon(Icons.Default.EventAvailable, null, modifier = Modifier.size(18.dp))
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateStringToMillis(dateForDayOfMonth(dayValue).toString()))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDayChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).dayOfMonth.toString())
                    }
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

private fun dateStringToMillis(value: String): Long? =
    runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

private fun millisToDateString(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun dateForDayOfMonth(day: Int): LocalDate {
    val safeDay = day.coerceIn(1, 31)
    val firstOfMonth = LocalDate.now().withDayOfMonth(1)
    return generateSequence(firstOfMonth) { it.plusMonths(1) }
        .first { it.lengthOfMonth() >= safeDay }
        .withDayOfMonth(safeDay)
}
