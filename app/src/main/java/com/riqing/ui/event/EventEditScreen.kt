package com.riqing.ui.event

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riqing.core.common.TimeUtils
import com.riqing.core.model.RepeatFreq
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 当前打开的日期/时间选择弹窗（同一时刻至多一个）。 */
private enum class DateTimePicker { START_DATE, START_TIME, END_DATE, END_TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditRoute(
    onDone: () -> Unit,
    vm: EventEditViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<DateTimePicker?>(null) }
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text(if (state.eventId == null) "新建日程" else "编辑日程") },
                navigationIcon = { TextButton(onClick = onDone) { Text("取消") } },
                actions = {
                    TextButton(onClick = { vm.save() }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                label = { Text("标题 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("全天", Modifier.weight(1f))
                Switch(checked = state.isAllDay, onCheckedChange = vm::setAllDay)
            }
            // 开始：点日期弹日历、点时间弹时间选择；全天时没有时间概念，隐藏时间入口
            Text("开始", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickField(
                    text = TimeUtils.formatDateText(state.startAt),
                    onClick = { picker = DateTimePicker.START_DATE },
                    modifier = Modifier.weight(1f),
                )
                if (!state.isAllDay) {
                    PickField(
                        text = TimeUtils.formatTimeText(state.startAt),
                        onClick = { picker = DateTimePicker.START_TIME },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row {
                TextButton(onClick = { vm.setStart(state.startAt - 15 * 60_000) }) { Text("-15分") }
                TextButton(onClick = { vm.setStart(state.startAt + 15 * 60_000) }) { Text("+15分") }
                TextButton(onClick = { vm.setStart(state.startAt + 60 * 60_000) }) { Text("+1时") }
            }
            // 结束：同开始
            Text("结束", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickField(
                    text = TimeUtils.formatDateText(state.endAt),
                    onClick = { picker = DateTimePicker.END_DATE },
                    modifier = Modifier.weight(1f),
                )
                if (!state.isAllDay) {
                    PickField(
                        text = TimeUtils.formatTimeText(state.endAt),
                        onClick = { picker = DateTimePicker.END_TIME },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row {
                TextButton(onClick = { vm.setEnd(state.endAt - 15 * 60_000) }) { Text("-15分") }
                TextButton(onClick = { vm.setEnd(state.endAt + 15 * 60_000) }) { Text("+15分") }
                TextButton(onClick = { vm.setEnd(state.endAt + 60 * 60_000) }) { Text("+1时") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.reminderMinutesText,
                onValueChange = vm::setReminders,
                label = { Text("提醒（分钟，逗号分隔）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Text("重复")
            Row {
                FilterChip(selected = state.repeatFreq == null, onClick = { vm.setRepeat(null) }, label = { Text("不重复") })
                FilterChip(selected = state.repeatFreq == RepeatFreq.DAILY, onClick = { vm.setRepeat(RepeatFreq.DAILY) }, label = { Text("每天") })
                FilterChip(selected = state.repeatFreq == RepeatFreq.WEEKLY, onClick = { vm.setRepeat(RepeatFreq.WEEKLY) }, label = { Text("每周") })
                FilterChip(selected = state.repeatFreq == RepeatFreq.WORKDAYS, onClick = { vm.setRepeat(RepeatFreq.WORKDAYS) }, label = { Text("工作日") })
            }
            // 「每周」单独滑出星期几选择面板；不重复/每天/工作日无弹窗
            AnimatedVisibility(
                visible = state.repeatFreq == RepeatFreq.WEEKLY,
                enter = fadeIn(animationSpec = tween(220)) +
                    expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top),
                exit = fadeOut(animationSpec = tween(180)) +
                    shrinkVertically(animationSpec = tween(180), shrinkTowards = Alignment.Top),
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        "选择每周生效的星期几（可多选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    WeekdaySelector(
                        selected = state.repeatWeekdays,
                        onToggle = vm::toggleWeekday,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::setNotes,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.eventId != null) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { vm.delete() }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) {
                Text("保存")
            }
        }
    }

    when (picker) {
        DateTimePicker.START_DATE -> DatePickDialog(
            initialEpoch = state.startAt,
            onConfirm = { date ->
                // 仅改日期，保留原时间；开始变动按「保持时长」联动结束（沿用 setStart 语义）
                val time = TimeUtils.localDateTimeOf(state.startAt).toLocalTime()
                vm.setStart(TimeUtils.toEpoch(date.atTime(time)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        DateTimePicker.START_TIME -> TimePickDialog(
            title = "选择开始时间",
            initialEpoch = state.startAt,
            onConfirm = { hour, minute ->
                vm.setStart(TimeUtils.toEpoch(TimeUtils.localDateOf(state.startAt).atTime(hour, minute)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        DateTimePicker.END_DATE -> DatePickDialog(
            initialEpoch = state.endAt,
            onConfirm = { date ->
                // 仅改日期，保留结束时刻；早于开始时由保存校验提示
                val time = TimeUtils.localDateTimeOf(state.endAt).toLocalTime()
                vm.setEnd(TimeUtils.toEpoch(date.atTime(time)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        DateTimePicker.END_TIME -> TimePickDialog(
            title = "选择结束时间",
            initialEpoch = state.endAt,
            onConfirm = { hour, minute ->
                vm.setEnd(TimeUtils.toEpoch(TimeUtils.localDateOf(state.endAt).atTime(hour, minute)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

/** 可点击的日期/时间展示框。 */
@Composable
private fun PickField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 日历弹窗：确认后返回所选日期。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(
    initialEpoch: Long,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker 的 selectedDateMillis 是「该日 UTC 零点」的毫秒，读写都要按 UTC 换算
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = TimeUtils.localDateOf(initialEpoch)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** 时间弹窗：确认后返回时/分（24 小时制）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    title: String,
    initialEpoch: Long,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val init = TimeUtils.localDateTimeOf(initialEpoch)
    val state = rememberTimePickerState(
        initialHour = init.hour,
        initialMinute = init.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = { TimePicker(state = state) },
    )
}

/** 「每周」星期几选择：一行 日一二三四五六 七个小框，选中变色。 */
@Composable
private fun WeekdaySelector(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 展示顺序 日一二三四五六；ISO 值：周一=1 … 周日=7
    val days = listOf(7, 1, 2, 3, 4, 5, 6)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { dow ->
            val isSelected = dow in selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(180),
                label = "weekdayBg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(180),
                label = "weekdayFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onToggle(dow) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    TimeUtils.weekdayChinese(dow),
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
