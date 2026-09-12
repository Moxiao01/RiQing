package com.riqing.widget

import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.AgendaRepository
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.DayAgenda
import com.riqing.core.model.Semester
import com.riqing.core.model.Todo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** 今日日程小组件的一行。 */
data class WidgetAgendaRow(
    val id: String,
    val timeText: String,
    val title: String,
    val subtitle: String?,
    val colorArgb: Int,
    val isOngoing: Boolean,
)

/** 今日待办小组件的一行。 */
data class WidgetTodoRow(
    val id: String,
    val title: String,
    val rightText: String?,
    val rightIsOverdue: Boolean,
    val isDone: Boolean,
)

/** 下节课小组件的内容。 */
data class WidgetNextItem(
    val statusText: String,
    val title: String,
    val subtitle: String?,
)

/** 连堂课程块在网格里的段位：单节 / 首节 / 中段 / 尾节（决定圆角与贴合方式）。 */
enum class WidgetWeekSegment {
    /** 单节成块：四角圆角，上下留缝。 */
    SINGLE,

    /** 连堂首节：上圆角、下与后续行贴合。 */
    TOP,

    /** 连堂中段：直角、上下都贴合。 */
    MIDDLE,

    /** 连堂尾节：下圆角、上与前行贴合。 */
    BOTTOM,
}

/**
 * 本周课表网格的一个格子。
 * colorToken 为空 = 空格子；单节块只放课名，连堂块（非 SINGLE）另带教师/地点详情行。
 */
data class WidgetWeekCell(
    val name: String,
    val detailLines: List<String>,
    val colorToken: String?,
    val segment: WidgetWeekSegment,
) {
    companion object {
        val EMPTY = WidgetWeekCell("", emptyList(), null, WidgetWeekSegment.SINGLE)
    }
}

/** 本周课表网格的一行：节次号 + 周一~周日 7 格。 */
data class WidgetWeekRow(
    val gutterText: String,
    val cells: List<WidgetWeekCell>,
)

/** 本周课表小组件的内容。 */
data class WidgetWeekGrid(
    val title: String,
    val dayLabels: List<String>,
    val todayColumn: Int,
    val rows: List<WidgetWeekRow>,
    val emptyText: String?,
)

/** 小组件共用的数据快照：一次加载，四个小组件一起刷新。 */
data class WidgetSnapshot(
    val dateLabel: String,
    val weekLabel: String?,
    val agendaRows: List<WidgetAgendaRow>,
    val todoRows: List<WidgetTodoRow>,
    val todoActiveCount: Int,
    val todoDoneCount: Int,
    val next: WidgetNextItem,
    /** 本周课表网格（周一~周日 × 有课的节次）。 */
    val week: WidgetWeekGrid,
    /** 下一次需要刷新的时刻（课节起止 / 明天首项 / 午夜），用于驱动倒计时与跨天。 */
    val nextBoundaryAt: Long,
)

@Singleton
class WidgetDataLoader @Inject constructor(
    private val agendaRepository: AgendaRepository,
    private val todoRepository: TodoRepository,
    private val semesterRepository: SemesterRepository,
) {
    suspend fun load(nowMillis: Long = TimeUtils.nowMillis()): WidgetSnapshot {
        val today = TimeUtils.localDateOf(nowMillis)
        val agenda = runCatching { agendaRepository.dayAgenda(today) }
            .getOrElse { DayAgenda(TimeUtils.formatIsoDate(today), emptyList(), emptyList()) }
        val overdue = runCatching { todoRepository.listOverdue() }.getOrElse { emptyList() }
        val withSlots = runCatching { semesterRepository.getWithSlots() }.getOrNull()
        val semester = withSlots?.semester
        val week = semester?.let { s ->
            runCatching {
                TimeUtils.teachingWeekIndex(TimeUtils.parseIsoDate(s.week1Monday), today)
            }.getOrNull()
        }?.takeIf { it >= 1 }
        // 「每天最多节数」= 学期设置里配置的节次数（最后一节的节号），周课表按它拉伸行数
        val maxPeriodIndex = withSlots?.slots
            ?.filterNot { it.isDeleted }
            ?.maxOfOrNull { it.periodIndex }
            ?: 0

        val agendaRows = agenda.items.map { item ->
            when (item) {
                is AgendaItem.CourseOccurrence -> WidgetAgendaRow(
                    id = item.id,
                    timeText = TimeUtils.formatTimeText(item.startAt),
                    title = item.title,
                    subtitle = listOfNotNull(
                        item.location,
                        "第${item.periodStart}-${item.periodEnd}节",
                    ).joinToString(" · ").ifEmpty { null },
                    colorArgb = courseColorArgb(item.colorToken),
                    isOngoing = nowMillis >= item.startAt && nowMillis < item.endAt,
                )
                is AgendaItem.EventOccurrence -> WidgetAgendaRow(
                    id = item.id,
                    timeText = if (item.isAllDay) "全天" else TimeUtils.formatTimeText(item.startAt),
                    title = item.title,
                    subtitle = item.notes?.takeIf { it.isNotBlank() } ?: "日程",
                    colorArgb = EVENT_COLOR,
                    isOngoing = !item.isAllDay && nowMillis >= item.startAt && nowMillis < item.endAt,
                )
            }
        }

        // 待办 = 已逾期 + 今日截止 + 收件箱（与首页今日视图口径一致），去重合并
        val merged = LinkedHashMap<String, Todo>()
        overdue.forEach { merged[it.id] = it }
        agenda.todos.forEach { t -> merged.putIfAbsent(t.id, t) }
        val active = merged.values.filter { !it.isDone }.sortedWith { a, b ->
            val ad = a.dueAt
            val bd = b.dueAt
            when {
                ad == null && bd == null -> 0
                ad == null -> 1
                bd == null -> -1
                else -> ad.compareTo(bd)
            }
        }
        val done = merged.values.filter { it.isDone }.sortedBy { it.doneAt ?: 0 }
        val todoRows = (active + done).map { t ->
            val due = t.dueAt
            val overdueRow = !t.isDone && due != null && due < nowMillis
            WidgetTodoRow(
                id = t.id,
                title = t.title,
                rightText = when {
                    overdueRow -> "已逾期"
                    due == null -> null
                    TimeUtils.isSameDay(due, nowMillis) -> TimeUtils.formatTimeText(due)
                    else -> TimeUtils.localDateTimeOf(due).format(shortDueFormatter)
                },
                rightIsOverdue = overdueRow,
                isDone = t.isDone,
            )
        }

        // 下节课 / 下一件事：先看今天剩余，再看明天第一项
        val boundaries = sortedSetOf<Long>()
        agenda.items.forEach {
            if (it.startAt > nowMillis) boundaries.add(it.startAt)
            if (it.endAt > nowMillis) boundaries.add(it.endAt)
        }
        val midnight = TimeUtils.endOfDayExclusive(today)
        val upcoming = agenda.items.firstOrNull { it.endAt > nowMillis }
        val next: WidgetNextItem = when {
            upcoming != null && nowMillis < upcoming.startAt -> WidgetNextItem(
                statusText = countdownText(nowMillis, upcoming.startAt),
                title = upcoming.title,
                subtitle = subtitleFor(upcoming),
            )
            upcoming != null -> WidgetNextItem(
                statusText = "正在进行",
                title = upcoming.title,
                subtitle = subtitleFor(upcoming),
            )
            else -> {
                val tomorrowFirst = runCatching {
                    agendaRepository.dayAgenda(today.plusDays(1)).items.firstOrNull()
                }.getOrNull()
                if (tomorrowFirst != null) boundaries.add(tomorrowFirst.startAt)
                if (tomorrowFirst != null) {
                    WidgetNextItem(
                        statusText = "明天 ${TimeUtils.formatTimeText(tomorrowFirst.startAt)}",
                        title = tomorrowFirst.title,
                        subtitle = subtitleFor(tomorrowFirst),
                    )
                } else {
                    WidgetNextItem("今天没有安排", "点此打开日清添加", null)
                }
            }
        }

        // 本周课表网格（复用 AgendaProjector 的课程投影，周一对齐教学周）
        val weekGrid = buildWeekGrid(today, semester, week, maxPeriodIndex, agendaRepository)

        return WidgetSnapshot(
            dateLabel = "${TimeUtils.formatDayLabel(today)} ${TimeUtils.formatWeekday(today)}",
            weekLabel = week?.let { "第${it}教学周" },
            agendaRows = agendaRows,
            todoRows = todoRows,
            todoActiveCount = active.size,
            todoDoneCount = done.size,
            next = next,
            week = weekGrid,
            nextBoundaryAt = (boundaries + midnight).min(),
        )
    }

    private suspend fun buildWeekGrid(
        today: LocalDate,
        semester: Semester?,
        teachingWeek: Int?,
        maxPeriodIndex: Int,
        agendaRepository: AgendaRepository,
    ): WidgetWeekGrid {
        val dayLabels = (1..7).map { "周${TimeUtils.weekdayChinese(it)}" }
        val monday = TimeUtils.mondayOfWeek(today)
        val todayColumn = TimeUtils.dayOfWeekMonday1(today) - 1
        if (semester == null || teachingWeek == null || teachingWeek < 1) {
            return WidgetWeekGrid(
                title = "还未开学",
                dayLabels = dayLabels,
                todayColumn = todayColumn,
                rows = emptyList(),
                emptyText = "开学后这里显示本周课表",
            )
        }
        val sunday = monday.plusDays(6)
        val title = "第${teachingWeek}教学周 · ${monday.monthValue}/${monday.dayOfMonth}" +
            "–${sunday.monthValue}/${sunday.dayOfMonth}"

        // 投影本周 7 天的课程（weeksSpec / 单双周都在 AgendaProjector 内处理）
        val dayOccurrences: Map<Int, List<AgendaItem.CourseOccurrence>> = (0..6).associateWith { offset ->
            val day = monday.plusDays(offset.toLong())
            runCatching {
                agendaRepository.dayAgenda(day).items.filterIsInstance<AgendaItem.CourseOccurrence>()
            }.getOrElse { emptyList() }
        }
        if (dayOccurrences.values.all { it.isEmpty() }) {
            return WidgetWeekGrid(title, dayLabels, todayColumn, emptyList(), "本周没有课")
        }

        // 行数按「每天最多节数」拉伸（学期节次配置），而不是只画有课的节次
        val maxPeriod = maxPeriodIndex.coerceIn(1, MAX_WEEK_ROWS)
        val rows = (1..maxPeriod).map { period ->
            WidgetWeekRow(
                gutterText = "$period",
                cells = (0..6).map { day ->
                    val occ = dayOccurrences[day]?.firstOrNull {
                        period in it.periodStart..it.periodEnd
                    }
                    when (occ) {
                        null -> WidgetWeekCell.EMPTY
                        else -> {
                            val segment = when (period) {
                                occ.periodStart ->
                                    if (occ.periodEnd == occ.periodStart) WidgetWeekSegment.SINGLE
                                    else WidgetWeekSegment.TOP
                                occ.periodEnd -> WidgetWeekSegment.BOTTOM
                                else -> WidgetWeekSegment.MIDDLE
                            }
                            // 文本只放在块首（TOP）：连堂续行只着色不重复显示；
                            // 连堂块（≥2 节）三行平铺课名/教师/地点，单节块只放课名
                            val isBlockHead = segment == WidgetWeekSegment.TOP ||
                                segment == WidgetWeekSegment.SINGLE
                            WidgetWeekCell(
                                name = if (isBlockHead) occ.title else "",
                                detailLines = when {
                                    segment == WidgetWeekSegment.SINGLE -> emptyList()
                                    segment == WidgetWeekSegment.TOP -> listOfNotNull(
                                        occ.teacher?.takeIf { it.isNotBlank() },
                                        occ.location?.takeIf { it.isNotBlank() },
                                    )
                                    else -> emptyList()
                                },
                                colorToken = occ.colorToken,
                                segment = segment,
                            )
                        }
                    }
                },
            )
        }
        return WidgetWeekGrid(title, dayLabels, todayColumn, rows, emptyText = null)
    }

    private fun subtitleFor(item: AgendaItem): String? {
        val range = "${TimeUtils.formatTimeText(item.startAt)}–${TimeUtils.formatTimeText(item.endAt)}"
        return when (item) {
            is AgendaItem.CourseOccurrence ->
                listOfNotNull(range, item.location).joinToString(" · ").ifEmpty { null }
            is AgendaItem.EventOccurrence ->
                if (item.isAllDay) "全天" else item.notes?.takeIf { it.isNotBlank() } ?: range
        }
    }

    companion object {
        const val MAX_LIST_ROWS = 10

        /** 本周课表网格最多显示的节次数，防止节次过多把小组件撑爆。 */
        const val MAX_WEEK_ROWS = 14

        /** 日程事件统一用主题靛蓝色（课程才有多彩标记）。 */
        val EVENT_COLOR: Int = 0xFF3F51B5.toInt()

        // 与 core/designsystem 的 C1-C8 课程色一致（RemoteViews 不能直接用 Compose Color）
        private val COURSE_COLORS = mapOf(
            "c1" to 0xFF3F51B5.toInt(),
            "c2" to 0xFF009688.toInt(),
            "c3" to 0xFFFF7043.toInt(),
            "c4" to 0xFF7E57C2.toInt(),
            "c5" to 0xFF26A69A.toInt(),
            "c6" to 0xFFEC407A.toInt(),
            "c7" to 0xFFFFA726.toInt(),
            "c8" to 0xFF42A5F5.toInt(),
        )

        private val shortDueFormatter: DateTimeFormatter = DateTimeFormatterHolder.shortDue

        fun courseColorArgb(token: String): Int =
            COURSE_COLORS[token.lowercase().trim()] ?: COURSE_COLORS.getValue("c1")

        private fun countdownText(now: Long, startAt: Long): String {
            val minutes = (startAt - now + 59_999) / 60_000
            return when {
                minutes <= 0 -> "即将开始"
                minutes < 60 -> "还有 $minutes 分钟"
                minutes < 24 * 60 -> {
                    val h = minutes / 60
                    val m = minutes % 60
                    if (m == 0L) "还有 $h 小时" else "还有 $h 小时 $m 分"
                }
                else -> TimeUtils.formatTimeText(startAt)
            }
        }
    }
}

private object DateTimeFormatterHolder {
    val shortDue: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
}
