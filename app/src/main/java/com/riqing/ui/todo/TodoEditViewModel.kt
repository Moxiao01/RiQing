package com.riqing.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TodoEditUiState(
    val todoId: String? = null,
    val title: String = "",
    val dueAt: Long? = null,
    val notes: String = "",
    val isDone: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class TodoEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todos: TodoRepository,
    private val planner: ReminderPlanner,
) : ViewModel() {
    private val todoId: String? = savedStateHandle["todoId"]
    private val _ui = MutableStateFlow(TodoEditUiState(todoId = todoId))
    val ui: StateFlow<TodoEditUiState> = _ui.asStateFlow()

    init {
        if (todoId != null) load(todoId)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val t = todos.get(id) ?: return@launch
            _ui.update {
                it.copy(
                    title = t.title,
                    dueAt = t.dueAt,
                    notes = t.notes.orEmpty(),
                    isDone = t.isDone,
                )
            }
        }
    }

    fun setTitle(v: String) = _ui.update { it.copy(title = v) }

    /** 只改截止日期，保留原时刻；此前未设置时默认 20:00。 */
    fun setDueDate(date: LocalDate) = _ui.update {
        val time = it.dueAt?.let { d -> TimeUtils.localDateTimeOf(d).toLocalTime() }
            ?: LocalTime.of(20, 0)
        it.copy(dueAt = TimeUtils.toEpoch(date.atTime(time)))
    }

    /** 只改截止时刻；此前未设置时按今天。 */
    fun setDueTime(hour: Int, minute: Int) = _ui.update {
        val date = it.dueAt?.let { d -> TimeUtils.localDateOf(d) } ?: LocalDate.now()
        it.copy(dueAt = TimeUtils.toEpoch(date.atTime(hour, minute)))
    }

    fun clearDue() = _ui.update { it.copy(dueAt = null) }
    fun setNotes(v: String) = _ui.update { it.copy(notes = v) }
    fun setDone(v: Boolean) = _ui.update { it.copy(isDone = v) }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val due = s.dueAt
                val saved = if (s.todoId == null) {
                    todos.create(
                        title = s.title,
                        dueAt = due,
                        notes = s.notes.ifBlank { null },
                    )
                } else {
                    todos.update(
                        id = s.todoId,
                        title = s.title,
                        dueAt = due,
                        clearDue = due == null,
                        notes = s.notes.ifBlank { null },
                        isDone = s.isDone,
                    )
                }
                runCatching { planner.replanEntity(ReminderPlanner.TYPE_TODO, saved.id) }
                saved
            }.onSuccess {
                _ui.update { it.copy(loading = false, saved = true) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun delete() {
        val id = todoId ?: return
        viewModelScope.launch {
            runCatching { todos.delete(id) }
            runCatching { planner.replanEntity(ReminderPlanner.TYPE_TODO, id) }
            _ui.update { it.copy(deleted = true) }
        }
    }
}
