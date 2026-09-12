package com.riqing.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riqing.core.common.TimeUtils
import com.riqing.core.designsystem.ColorC7
import com.riqing.core.designsystem.ColorC8
import com.riqing.core.designsystem.IndigoDark
import com.riqing.core.designsystem.courseColor
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.DayAgenda
import com.riqing.core.model.Todo
import java.time.LocalDate

/** 日历圆点/卡片的类型色：课程=主题色，日程=蓝，待办=琥珀（与设计系统 C7/C8 一致）。 */
private val EventAccent: Color
    @Composable get() = ColorC8
private val TodoAccent: Color
    @Composable get() = ColorC7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarRoute(
    onOpenEvent: (String) -> Unit,
    onAddEvent: (Long, Long) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            CalendarMode.DAY -> "${TimeUtils.formatDayLabel(state.anchor)} ${TimeUtils.formatWeekday(state.anchor)}"
                            CalendarMode.WEEK -> {
                                val m = TimeUtils.mondayOfWeek(state.anchor)
                                "${TimeUtils.formatDayLabel(m)} - ${TimeUtils.formatDayLabel(m.plusDays(6))}"
                            }
                            CalendarMode.MONTH -> "${state.anchor.year}年${state.anchor.monthValue}月"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.shift(-1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上一")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.shift(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下一")
                    }
                    IconButton(onClick = { vm.goToday() }) { Text("今天") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val start = TimeUtils.toEpoch(state.anchor.atTime(9, 0))
                    onAddEvent(start, start + 3600_000)
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = state.mode == CalendarMode.DAY, onClick = { vm.setMode(CalendarMode.DAY) }, label = { Text("日") })
                FilterChip(selected = state.mode == CalendarMode.WEEK, onClick = { vm.setMode(CalendarMode.WEEK) }, label = { Text("周") })
                FilterChip(selected = state.mode == CalendarMode.MONTH, onClick = { vm.setMode(CalendarMode.MONTH) }, label = { Text("月") })
            }
            Spacer(Modifier.height(8.dp))
            when (state.mode) {
                CalendarMode.DAY -> DayContent(state, onOpenEvent)
                CalendarMode.WEEK -> WeekContent(state, onSelect = { vm.setAnchor(it) }, onOpenEvent)
                // 月视图：点日期只选中并在底部展示当日安排，不再跳走
                CalendarMode.MONTH -> MonthContent(state, onSelect = { vm.setAnchor(it) }, onOpenEvent)
            }
        }
    }
}

@Composable
private fun DayContent(state: CalendarUiState, onOpenEvent: (String) -> Unit) {
    val agenda = state.agenda
    if (agenda == null) {
        Text("加载中…", Modifier.padding(16.dp))
        return
    }
    if (agenda.items.isEmpty() && agenda.todos.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("这一天没有安排", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    DayAgendaList(agenda, onOpenEvent, Modifier.fillMaxSize())
}

@Composable
private fun WeekContent(state: CalendarUiState, onSelect: (LocalDate) -> Unit, onOpenEvent: (String) -> Unit) {
    val monday = TimeUtils.mondayOfWeek(state.anchor)
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val key = TimeUtils.formatIsoDate(d)
                val agenda = state.weekAgendas[key]
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(d) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(TimeUtils.formatWeekday(d), style = MaterialTheme.typography.labelMedium)
                    val isToday = d == LocalDate.now()
                    val isSelected = d == state.anchor
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> IndigoDark
                                    else -> Color.Transparent
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected || isToday) Color.White else Color.Unspecified,
                        )
                    }
                    // 当日安排圆点：课程/日程/待办 各一枚，与月视图一致
                    Row(
                        Modifier.height(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (agenda?.items?.any { it is AgendaItem.CourseOccurrence } == true) {
                            MarkDot(MaterialTheme.colorScheme.primary)
                        }
                        if (agenda?.items?.any { it is AgendaItem.EventOccurrence } == true) {
                            MarkDot(EventAccent)
                        }
                        if (agenda?.todos?.any { it.dueAt != null } == true) {
                            MarkDot(TodoAccent)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        MarksLegend()
        Spacer(Modifier.height(4.dp))
        AgendaBottomPanel(
            date = state.anchor,
            agenda = state.weekAgendas[TimeUtils.formatIsoDate(state.anchor)],
            nonTodayDdls = state.upcomingDdls,
            onOpenEvent = onOpenEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthContent(state: CalendarUiState, onSelect: (LocalDate) -> Unit, onOpenEvent: (String) -> Unit) {
    val first = state.anchor.withDayOfMonth(1)
    val leading = first.dayOfWeek.value - 1 // Monday=1
    val cells: List<LocalDate?> = buildList {
        repeat(leading) { add(null) }
        for (d in 1..state.anchor.lengthOfMonth()) add(first.withDayOfMonth(d))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        MonthCell(
                            date = date,
                            isSelected = date == state.anchor,
                            marks = date?.let { state.monthMarks[it.dayOfMonth] },
                            onClick = { date?.let(onSelect) },
                        )
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
        // 圆点图例
        MarksLegend(Modifier.padding(top = 2.dp, bottom = 6.dp))
        AgendaBottomPanel(
            date = state.anchor,
            agenda = state.agenda,
            nonTodayDdls = state.upcomingDdls,
            onOpenEvent = onOpenEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthCell(
    date: LocalDate?,
    isSelected: Boolean,
    marks: MonthDayMarks?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .height(60.dp)
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isToday = date != null && date == LocalDate.now()
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> IndigoDark
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected || isToday) Color.White else Color.Unspecified,
            )
        }
        Spacer(Modifier.height(3.dp))
        // 当日安排圆点：横向一排（课程 / 日程 / 待办 各一枚）
        Row(
            Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (marks?.hasCourse == true) MarkDot(MaterialTheme.colorScheme.primary)
            if (marks?.hasEvent == true) MarkDot(EventAccent)
            if (marks?.hasTodo == true) MarkDot(TodoAccent)
        }
    }
}

@Composable
private fun MarkDot(color: Color) {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MarkDot(color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 圆点图例：课程（主题蓝）/ 日程（浅蓝）/ 待办 DDL（琥珀黄）。 */
@Composable
private fun MarksLegend(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(MaterialTheme.colorScheme.primary, "课程")
        Spacer(Modifier.width(12.dp))
        LegendDot(EventAccent, "日程")
        Spacer(Modifier.width(12.dp))
        LegendDot(TodoAccent, "待办")
    }
}

/** 底部「当日安排」面板：圆角顶边 + 浅色底，展示选中日期的完整日程与待办；
 *  有非本日 DDL 时在列表最上方提示（避免未来截止的待办在周/月视图里不可见）。 */
@Composable
private fun AgendaBottomPanel(
    date: LocalDate,
    agenda: DayAgenda?,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    nonTodayDdls: List<Todo> = emptyList(),
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(top = 14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${TimeUtils.formatDayLabel(date)} ${TimeUtils.formatWeekday(date)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val total = (agenda?.items?.size ?: 0) + (agenda?.todos?.size ?: 0)
            Text(
                "$total 项安排",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val agendaEmpty = agenda == null || (agenda.items.isEmpty() && agenda.todos.isEmpty())
        if (agendaEmpty && nonTodayDdls.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("这一天没有安排", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            DayAgendaList(agenda, onOpenEvent, Modifier.fillMaxSize(), nonTodayDdls)
        }
    }
}

/** 「非本日 DDL」区：列出选中日之后仍未截止的待办，卡片样式与当日安排一致。 */
@Composable
private fun NonTodayDdlSection(ddls: List<Todo>) {
    val now = TimeUtils.nowMillis()
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkDot(TodoAccent)
            Spacer(Modifier.width(4.dp))
            Text(
                "非本日 DDL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ddls.forEach { todo -> NonTodayDdlCard(todo, now) }
    }
}

/** 非本日 DDL 卡片：时间列显示截止日期与时刻，副标题给星期和剩余时间。 */
@Composable
private fun NonTodayDdlCard(todo: Todo, now: Long) {
    val dueAt = todo.dueAt ?: return
    val dueDate = TimeUtils.localDateOf(dueAt)
    AgendaCardScaffold(
        accent = TodoAccent,
        badge = "待办",
        title = todo.title,
        subtitle = "${TimeUtils.formatWeekday(dueDate)} · ${remainingText(dueAt, now)}",
        startText = "${dueDate.monthValue}/${dueDate.dayOfMonth}",
        endText = TimeUtils.formatTimeText(dueAt),
        onClick = null,
    )
}

/** 剩余时间：≥1 天显示「剩余X天」，不足 1 天显示「剩余X小时」，已过截止显示「已截止」。 */
private fun remainingText(dueAt: Long, now: Long): String {
    val diffMs = dueAt - now
    if (diffMs <= 0) return "已截止"
    val totalHours = diffMs / 3_600_000
    return when {
        totalHours >= 24 -> "剩余${totalHours / 24}天"
        totalHours >= 1 -> "剩余${totalHours}小时"
        else -> "剩余不足1小时"
    }
}

/** 日程列表条目：课程/日程按开始时间、待办按截止时间混排；无截止待办排最后。 */
private data class AgendaEntry(
    val key: String,
    val sortAt: Long?,
    val item: AgendaItem? = null,
    val todo: Todo? = null,
)

/** 当日安排列表：课程/日程/本日待办按时间顺序混排，末尾为「非本日 DDL」提示（仅周/月视图传入）。 */
@Composable
private fun DayAgendaList(
    agenda: DayAgenda?,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    nonTodayDdls: List<Todo> = emptyList(),
) {
    val entries = agenda?.let { a ->
        buildList {
            a.items.forEach { add(AgendaEntry(key = it.id, sortAt = it.startAt, item = it)) }
            a.todos.forEach { add(AgendaEntry(key = "todo-${it.id}", sortAt = it.dueAt, todo = it)) }
        }.sortedWith(compareBy({ it.sortAt ?: Long.MAX_VALUE }, { if (it.item != null) 0 else 1 }))
    }.orEmpty()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.key }) { entry ->
            when {
                entry.item != null -> {
                    val item = entry.item
                    // 课程实例是只读投影（D1 §3.3），仅日程可进入编辑
                    AgendaCard(
                        item = item,
                        onClick = if (item is AgendaItem.EventOccurrence) {
                            { onOpenEvent(item.eventId) }
                        } else {
                            null
                        },
                    )
                }
                entry.todo != null -> TodoCard(entry.todo)
            }
        }
        if (nonTodayDdls.isNotEmpty()) {
            item(key = "non-today-ddl") { NonTodayDdlSection(nonTodayDdls) }
        }
    }
}

@Composable
private fun AgendaCard(item: AgendaItem, onClick: (() -> Unit)? = null) {
    val accent = when (item) {
        is AgendaItem.CourseOccurrence -> courseColor(item.colorToken)
        is AgendaItem.EventOccurrence -> EventAccent
    }
    val badge = when (item) {
        is AgendaItem.CourseOccurrence -> "课程"
        is AgendaItem.EventOccurrence -> "日程"
    }
    val subtitle = when (item) {
        is AgendaItem.CourseOccurrence ->
            listOfNotNull(item.location, item.teacher, "第${item.teachingWeek}周").joinToString(" · ")
        is AgendaItem.EventOccurrence -> item.notes ?: if (item.isSeries) "重复日程" else "日程"
    }
    AgendaCardScaffold(
        accent = accent,
        badge = badge,
        title = item.title,
        subtitle = subtitle,
        startText = if (item is AgendaItem.EventOccurrence && item.isAllDay) "全天" else TimeUtils.formatAbs(item.startAt).substringAfter(" "),
        endText = if (item is AgendaItem.EventOccurrence && item.isAllDay) "" else TimeUtils.formatAbs(item.endAt).substringAfter(" "),
        onClick = onClick,
    )
}

@Composable
private fun TodoCard(todo: Todo) {
    AgendaCardScaffold(
        accent = TodoAccent,
        badge = "待办",
        title = todo.title,
        subtitle = listOfNotNull(
            todo.dueAt?.let { "DDL · ${remainingText(it, TimeUtils.nowMillis())}" } ?: "无截止",
            todo.notes?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        startText = todo.dueAt?.let { TimeUtils.formatAbs(it).substringAfter(" ") } ?: "—",
        endText = "",
        done = todo.isDone,
        onClick = null,
    )
}

/** 统一卡片：左侧时间、色条、标题+徽标、副标题。 */
@Composable
private fun AgendaCardScaffold(
    accent: Color,
    badge: String,
    title: String,
    subtitle: String,
    startText: String,
    endText: String,
    done: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(startText, style = MaterialTheme.typography.labelLarge)
                if (endText.isNotEmpty()) {
                    Text(
                        endText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
