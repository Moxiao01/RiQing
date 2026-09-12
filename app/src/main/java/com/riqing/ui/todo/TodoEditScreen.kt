package com.riqing.ui.todo

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 当前打开的日期/时间选择弹窗（同一时刻至多一个）。 */
private enum class DuePicker { DUE_DATE, DUE_TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditRoute(
    onDone: () -> Unit,
    vm: TodoEditViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<DuePicker?>(null) }
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text(if (state.todoId == null) "新建待办" else "编辑待办") },
                navigationIcon = { TextButton(onClick = onDone) { Text("取消") } },
                actions = { TextButton(onClick = { vm.save() }) { Text("保存") } },
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
            // 截止：点日期弹日历、点时间弹时间选择；与新建日程同一套交互，截止可空
            Text("截止", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickField(
                    text = state.dueAt?.let { TimeUtils.formatDateText(it) } ?: "选择日期",
                    onClick = { picker = DuePicker.DUE_DATE },
                    modifier = Modifier.weight(1f),
                )
                PickField(
                    text = state.dueAt?.let { TimeUtils.formatTimeText(it) } ?: "选择时间",
                    onClick = { picker = DuePicker.DUE_TIME },
                    modifier = Modifier.weight(1f),
                )
                if (state.dueAt != null) {
                    TextButton(onClick = vm::clearDue) { Text("清除") }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::setNotes,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.todoId != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已完成", Modifier.weight(1f))
                    Switch(checked = state.isDone, onCheckedChange = vm::setDone)
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            if (state.todoId != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { vm.delete() }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除")
                }
            }
        }
    }

    when (picker) {
        DuePicker.DUE_DATE -> DatePickDialog(
            initialEpoch = state.dueAt ?: TimeUtils.nowMillis(),
            onConfirm = { date ->
                vm.setDueDate(date)
                picker = null
            },
            onDismiss = { picker = null },
        )
        DuePicker.DUE_TIME -> TimePickDialog(
            initialEpoch = state.dueAt ?: TimeUtils.nowMillis(),
            onConfirm = { hour, minute ->
                vm.setDueTime(hour, minute)
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
        title = { Text("选择截止时间") },
        text = { TimePicker(state = state) },
    )
}
