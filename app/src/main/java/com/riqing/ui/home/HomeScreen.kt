package com.riqing.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.riqing.core.common.TimeUtils
import com.riqing.core.designsystem.TodoDoneGreen
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.Todo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onOpenEvent: (String) -> Unit,
    onOpenTodo: (String) -> Unit,
    onAddEvent: (Long, Long) -> Unit,
    onAddTodo: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh()
        }
    }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(msg, actionLabel = "撤销")
        if (result == SnackbarResult.ActionPerformed) vm.undo()
        vm.consumeSnackbar()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            CompactTopAppBar(
                title = {
                    Column {
                        Text("日清")
                        Text(
                            buildString {
                                append(TimeUtils.formatDayLabel(state.date))
                                append(" ")
                                append(TimeUtils.formatWeekday(state.date))
                                state.teachingWeek?.let { append(" · 第${it}教学周") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo() }) {
                        Icon(Icons.Filled.Undo, contentDescription = "撤销")
                    }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onAddTodo) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "加待办")
                }
                FloatingActionButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onAddEvent(now, now + 3600_000)
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "加日程")
                }
            }
        },
    ) { padding ->
        val agenda = state.agenda
        if (agenda == null || state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("加载中…")
            }
            return@Scaffold
        }

        if (agenda.items.isEmpty() && agenda.todos.isEmpty() && state.overdueTodos.isEmpty() && state.upcomingTodos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("今天没有安排")
                Spacer(Modifier.height(8.dp))
                Text("去添加日程或待办", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.overdueTodos.isNotEmpty()) {
                item {
                    Text("已逾期", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
                items(state.overdueTodos, key = { "overdue-${it.id}" }) { todo ->
                    TodoRow(todo = todo, onToggle = { vm.toggleTodo(todo.id) }, onOpen = { onOpenTodo(todo.id) })
                }
            }
            if (agenda.items.isNotEmpty()) {
                item { Text("时间轴", style = MaterialTheme.typography.titleMedium) }
                items(agenda.items, key = { it.id }) { item ->
                    // 课程实例是只读投影（D1 §3.3），点击不进入编辑器
                    AgendaCard(
                        item = item,
                        onClick = {
                            if (item is AgendaItem.EventOccurrence) onOpenEvent(item.eventId)
                        },
                        clickable = item is AgendaItem.EventOccurrence,
                    )
                }
            }
            if (agenda.todos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("今日待办", style = MaterialTheme.typography.titleMedium)
                }
                items(agenda.todos, key = { it.id }) { todo ->
                    TodoRow(todo = todo, onToggle = { vm.toggleTodo(todo.id) }, onOpen = { onOpenTodo(todo.id) })
                }
            }
            if (state.upcomingTodos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("即将到来", style = MaterialTheme.typography.titleMedium)
                }
                items(state.upcomingTodos, key = { it.id }) { todo ->
                    TodoRow(todo = todo, onToggle = { vm.toggleTodo(todo.id) }, onOpen = { onOpenTodo(todo.id) })
                }
            }
        }
    }
}

@Composable
private fun AgendaCard(item: AgendaItem, onClick: () -> Unit, clickable: Boolean = true) {
    Card(modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item is AgendaItem.EventOccurrence && item.isAllDay) "全天"
                else TimeUtils.formatAbs(item.startAt).substringAfter(" "),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                val subtitle = when (item) {
                    is AgendaItem.EventOccurrence -> item.notes ?: if (item.isSeries) "重复" else "日程"
                    is AgendaItem.CourseOccurrence -> listOfNotNull(
                        item.location,
                        "第${item.periodStart}-${item.periodEnd}节",
                        "第${item.teachingWeek}周",
                    ).joinToString(" · ")
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onOpen: () -> Unit) {
    // 已完成：整体置灰 + 标题删除线 + 绿色打勾，仍保留在原分组中
    val done = todo.isDone
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                CardDefaults.cardColors().containerColor
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                    contentDescription = if (done) "标记未完成" else "标记完成",
                    tint = if (done) TodoDoneGreen else MaterialTheme.colorScheme.outline,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done) mutedColor else Color.Unspecified,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Text(
                    todo.dueAt?.let { "截止 ${TimeUtils.formatAbs(it)}" } ?: "无截止",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) mutedColor else Color.Unspecified,
                )
                todo.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
