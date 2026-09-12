package com.riqing.ui.course

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riqing.core.common.TimeUtils
import com.riqing.core.designsystem.courseColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditRoute(
    onDone: () -> Unit,
    vm: CourseEditViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.courseId == null) "新建课程" else "编辑课程") },
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
            OutlinedTextField(state.name, vm::setName, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(state.teacher, vm::setTeacher, label = { Text("教师") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(state.location, vm::setLocation, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("星期（可多选）")
            // 一行七个等宽小框（weight 保证不跨行）；选中变色，与日程编辑页的星期选择样式一致
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..7).forEach { d ->
                    val selected = d in state.weekdays
                    val bg by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        label = "courseWeekdayBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "courseWeekdayFg",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { vm.toggleWeekday(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            TimeUtils.weekdayChinese(d),
                            color = fg,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    state.periodStart.toString(),
                    { v -> vm.setPeriodStart(v.toIntOrNull() ?: 1) },
                    label = { Text("开始节") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    state.periodEnd.toString(),
                    { v -> vm.setPeriodEnd(v.toIntOrNull() ?: 1) },
                    label = { Text("结束节") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(state.weeksSpec, vm::setWeeks, label = { Text("周次 如 1-16 或 1,3,5-8") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("单双周", Modifier.weight(1f))
                Switch(checked = state.isBiweekly, onCheckedChange = vm::setBiweekly)
            }
            if (state.isBiweekly) {
                Row {
                    FilterChip(selected = state.biweeklyOdd, onClick = { vm.setBiweeklyOdd(true) }, label = { Text("单周") })
                    FilterChip(selected = !state.biweeklyOdd, onClick = { vm.setBiweeklyOdd(false) }, label = { Text("双周") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("颜色")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..8).forEach { i ->
                    val token = "c$i"
                    FilterChip(
                        selected = state.colorToken == token,
                        onClick = { vm.setColor(token) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(courseColor(token)),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(token.uppercase())
                            }
                        },
                    )
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            if (state.courseId != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.delete() }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除课程（之后不再投影）")
                }
            }
        }
    }
}
