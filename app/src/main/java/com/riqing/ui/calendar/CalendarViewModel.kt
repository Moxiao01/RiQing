package com.riqing.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.AgendaRepository
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.DayAgenda
import com.riqing.core.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class CalendarMode { DAY, WEEK, MONTH }

/** 月视图某日的安排标记：决定日期下方圆点（课程/日程/待办）的显示。 */
data class MonthDayMarks(
    val hasCourse: Boolean = false,
    val hasEvent: Boolean = false,
    val hasTodo: Boolean = false,
)

data class CalendarUiState(
    val mode: CalendarMode = CalendarMode.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val agenda: DayAgenda? = null,
    val weekAgendas: Map<String, DayAgenda> = emptyMap(),
    val monthMarks: Map<Int, MonthDayMarks> = emptyMap(),
    /** 选中日之后仍未截止的未完成待办，供周/月视图底部「非本日 DDL」展示。 */
    val upcomingDdls: List<Todo> = emptyList(),
    val loading: Boolean = false,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val agendaRepository: AgendaRepository,
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CalendarUiState())
    val ui: StateFlow<CalendarUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun setMode(mode: CalendarMode) {
        _ui.update { it.copy(mode = mode) }
        refresh()
    }

    fun setAnchor(date: LocalDate) {
        _ui.update { it.copy(anchor = date) }
        refresh()
    }

    fun shift(direction: Int) {
        val cur = _ui.value.anchor
        val next = when (_ui.value.mode) {
            CalendarMode.DAY -> cur.plusDays(direction.toLong())
            CalendarMode.WEEK -> cur.plusWeeks(direction.toLong())
            CalendarMode.MONTH -> cur.plusMonths(direction.toLong())
        }
        setAnchor(next)
    }

    fun goToday() = setAnchor(LocalDate.now())

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val anchor = _ui.value.anchor
            when (_ui.value.mode) {
                CalendarMode.DAY -> {
                    val agenda = agendaRepository.dayAgenda(anchor)
                    _ui.update { s -> s.copy(agenda = agenda, upcomingDdls = emptyList(), loading = false) }
                }
                CalendarMode.WEEK -> {
                    val monday = TimeUtils.mondayOfWeek(anchor)
                    val map = (0..6).associate { i ->
                        val d = monday.plusDays(i.toLong())
                        TimeUtils.formatIsoDate(d) to agendaRepository.dayAgenda(d)
                    }
                    // 不限于本周：DDL 在下周时（如今天周六、DDL 下周一），点击本周边日期也能看到
                    val ddls = todoRepository.listUpcoming(TimeUtils.endOfDayExclusive(anchor))
                    _ui.update { s -> s.copy(weekAgendas = map, upcomingDdls = ddls, loading = false) }
                }
                CalendarMode.MONTH -> {
                    val first = anchor.withDayOfMonth(1)
                    val marks = mutableMapOf<Int, MonthDayMarks>()
                    for (day in 1..first.lengthOfMonth()) {
                        val agenda = agendaRepository.dayAgenda(first.withDayOfMonth(day))
                        marks[day] = MonthDayMarks(
                            hasCourse = agenda.items.any { it is AgendaItem.CourseOccurrence },
                            hasEvent = agenda.items.any { it is AgendaItem.EventOccurrence },
                            // 黄点 = 有截止时间的未完成待办（DDL）；无截止待办每天都出现，不标记
                            hasTodo = agenda.todos.any { it.dueAt != null },
                        )
                    }
                    // 选中日（anchor）的完整安排，供底部面板展示
                    val selected = agendaRepository.dayAgenda(anchor)
                    val ddls = todoRepository.listUpcoming(TimeUtils.endOfDayExclusive(anchor))
                    _ui.update { s ->
                        s.copy(monthMarks = marks, agenda = selected, upcomingDdls = ddls, loading = false)
                    }
                }
            }
        }
    }
}
