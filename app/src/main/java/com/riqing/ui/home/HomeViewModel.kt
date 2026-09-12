package com.riqing.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.AgendaRepository
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.data.repo.UndoService
import com.riqing.core.model.DayAgenda
import com.riqing.core.model.Todo
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val agenda: DayAgenda? = null,
    val overdueTodos: List<Todo> = emptyList(),
    val upcomingTodos: List<Todo> = emptyList(),
    val teachingWeek: Int? = null,
    val semesterName: String? = null,
    val loading: Boolean = true,
    val snackbar: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val agendaRepository: AgendaRepository,
    private val semesterRepository: SemesterRepository,
    private val todoRepository: TodoRepository,
    private val undoService: UndoService,
    private val planner: ReminderPlanner,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // 仅首次（尚无数据）显示加载占位；已有数据时静默刷新，
            // 避免整页换成加载页导致 LazyColumn 销毁重建、滚动位置跳回顶部
            _ui.update { if (it.agenda == null) it.copy(loading = true) else it }
            val date = LocalDate.now()
            val agenda = agendaRepository.dayAgenda(date)
            val overdue = todoRepository.listOverdue()
                .filter { !TimeUtils.isSameDay(it.dueAt ?: 0, TimeUtils.nowMillis()) }
            val upcoming = todoRepository.listUpcoming()
            val sem = semesterRepository.getCurrent()
            val week = sem?.let {
                TimeUtils.teachingWeekIndex(TimeUtils.parseIsoDate(it.week1Monday), date)
            }
            _ui.update {
                it.copy(
                    date = date,
                    agenda = agenda,
                    overdueTodos = overdue,
                    upcomingTodos = upcoming,
                    teachingWeek = week?.takeIf { w -> w >= 1 },
                    semesterName = sem?.name,
                    loading = false,
                )
            }
            // 回到今日页时重排提醒，兜底覆盖 Agent 写入 / 撤销 / 快照过期等路径
            runCatching { planner.replanAll() }
        }
    }

    fun toggleTodo(id: String) {
        viewModelScope.launch {
            val todo = todoRepository.get(id) ?: return@launch
            val msg = if (todo.isDone) {
                todoRepository.update(id, isDone = false)
                "已恢复「${todo.title}」为未完成"
            } else {
                todoRepository.complete(id)
                "已完成「${todo.title}」"
            }
            runCatching { planner.replanEntity(ReminderPlanner.TYPE_TODO, id) }
            refresh()
            _ui.update { it.copy(snackbar = msg) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            val msg = runCatching { undoService.latest() }.getOrNull()
            refresh()
            _ui.update { it.copy(snackbar = msg ?: "没有可撤销操作") }
        }
    }

    fun consumeSnackbar() {
        _ui.update { it.copy(snackbar = null) }
    }
}
