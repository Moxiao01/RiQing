package com.riqing.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SemesterEditRoute(
    onDone: () -> Unit,
    vm: SemesterEditViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.semesterId == null) "新建学期" else "编辑学期") },
                navigationIcon = { TextButton(onClick = onDone) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .imeNestedScroll()
                .verticalScroll(rememberScrollState()),
        ) {
            TemplateDropdown(selected = state.template, onSelect = vm::applyTemplate)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.name, vm::setName, label = { Text("学期名称 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                state.week1Monday,
                vm::setWeek1,
                label = { Text("第 1 教学周周一（YYYY-MM-DD，须周一）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.endDate,
                vm::setEnd,
                label = { Text("学期结束日（含）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设为当前学期", Modifier.weight(1f))
                Switch(checked = state.isCurrent, onCheckedChange = vm::setCurrent)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("节次时段", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "共 ${state.periods.size} 节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "点按卡片可编辑；课程按节次号引用时间，删除后编号不会自动重排。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))

            state.periods.forEach { p ->
                PeriodCard(
                    row = p,
                    editing = state.editingPeriodId == p.id,
                    onClick = { if (state.editingPeriodId == p.id) vm.stopEdit() else vm.startEdit(p.id) },
                    onStartChange = { v -> vm.updatePeriod(p.id, v, p.endHHmm) },
                    onEndChange = { v -> vm.updatePeriod(p.id, p.startHHmm, v) },
                    onDone = vm::stopEdit,
                    onDelete = { vm.removePeriod(p.id) },
                )
                Spacer(Modifier.height(10.dp))
            }
            AddPeriodCard(onClick = vm::addPeriod)

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}

/** "是否使用模板"下拉：自定义 / 北校区上课时间模板 / 南校区上课时间模板。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDropdown(
    selected: SemesterTemplateKind,
    onSelect: (SemesterTemplateKind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("是否使用模板") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SemesterTemplateKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.label) },
                    onClick = {
                        expanded = false
                        if (kind != selected) onSelect(kind)
                    },
                )
            }
        }
    }
}

/** 节次卡片：折叠时展示摘要，点按展开进入编辑模式。 */
@Composable
private fun PeriodCard(
    row: PeriodRow,
    editing: Boolean,
    onClick: () -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val error = if (editing) periodError(row.startHHmm, row.endHHmm) else null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (editing) 0.dp else 1.dp),
        border = if (editing) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IndexBadge(row.index)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("第 ${row.index} 节", style = MaterialTheme.typography.titleMedium)
                    if (editing) {
                        Text(
                            "编辑中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            durationLabel(row.startHHmm, row.endHHmm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = !editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${row.startHHmm} – ${row.endHHmm}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = editing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedTextField(
                            row.startHHmm,
                            onStartChange,
                            label = { Text("开始") },
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            singleLine = true,
                            isError = error != null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            row.endHHmm,
                            onEndChange,
                            label = { Text("结束") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = error != null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onDone() }),
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = onDone) { Text("完成") }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexBadge(index: Int) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun AddPeriodCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加节次", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 时长文案，如 "1 小时 40 分钟"；时间非法时返回空串。 */
private fun durationLabel(startHHmm: String, endHHmm: String): String {
    val sm = parsePeriodMinutes(startHHmm) ?: return ""
    val em = parsePeriodMinutes(endHHmm) ?: return ""
    val mins = em - sm
    return when {
        mins <= 0 -> ""
        mins < 60 -> "$mins 分钟"
        mins % 60 == 0 -> "${mins / 60} 小时"
        else -> "${mins / 60} 小时 ${mins % 60} 分钟"
    }
}
