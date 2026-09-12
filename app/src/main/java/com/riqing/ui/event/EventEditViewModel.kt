package com.riqing.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.EventRepository
import com.riqing.core.model.RepeatFreq
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventEditUiState(
    val eventId: String? = null,
    val title: String = "",
    val startAt: Long = System.currentTimeMillis(),
    val endAt: Long = System.currentTimeMillis() + 3600_000,
    val isAllDay: Boolean = false,
    val notes: String = "",
    val repeatFreq: RepeatFreq? = null,
    /** 每周重复的星期几（ISO：周一=1 … 周日=7）；仅 repeatFreq == WEEKLY 时生效。 */
    val repeatWeekdays: Set<Int> = emptySet(),
    // 重复结束日/次数：表单已不再提供输入框，仅用于已有日程的值透传（保存原样带回）
    val repeatUntilText: String = "",
    val repeatCountText: String = "",
    val reminderMinutesText: String = "10",
    val loading: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventRepository,
    private val planner: ReminderPlanner,
) : ViewModel() {
    private val eventId: String? = savedStateHandle["eventId"]
    private val startAtArg: Long? = savedStateHandle.get<String>("startAt")?.toLongOrNull()
    private val endAtArg: Long? = savedStateHandle.get<String>("endAt")?.toLongOrNull()

    private val _ui = MutableStateFlow(
        EventEditUiState(
            eventId = eventId,
            startAt = startAtArg ?: System.currentTimeMillis(),
            endAt = endAtArg ?: (System.currentTimeMillis() + 3600_000),
        ),
    )
    val ui: StateFlow<EventEditUiState> = _ui.asStateFlow()

    init {
        if (eventId != null) load(eventId)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val e = events.get(id) ?: run {
                _ui.update { it.copy(error = "找不到日程") }
                return@launch
            }
            _ui.update {
                it.copy(
                    title = e.title,
                    startAt = e.startAt,
                    endAt = e.endAt,
                    isAllDay = e.isAllDay,
                    notes = e.notes.orEmpty(),
                    repeatFreq = e.repeatFreq,
                    repeatWeekdays = e.repeatWeekdays?.toSet().orEmpty(),
                    repeatUntilText = e.repeatUntil?.let { TimeUtils.formatDateText(it) }.orEmpty(),
                    repeatCountText = e.repeatCount?.toString().orEmpty(),
                    reminderMinutesText = e.reminderOffsets.joinToString(","),
                )
            }
        }
    }

    fun setTitle(v: String) = _ui.update { it.copy(title = v) }
    fun setNotes(v: String) = _ui.update { it.copy(notes = v) }
    fun setAllDay(v: Boolean) = _ui.update { it.copy(isAllDay = v) }
    fun setStart(v: Long) = _ui.update { s ->
        val dur = s.endAt - s.startAt
        s.copy(startAt = v, endAt = v + dur.coerceAtLeast(60_000L))
    }
    fun setEnd(v: Long) = _ui.update { it.copy(endAt = v) }
    fun setRepeat(freq: RepeatFreq?) = _ui.update { s ->
        // 首次切到「每周」时，默认勾上开始日的星期几，避免出现空选集
        val weekdays = if (freq == RepeatFreq.WEEKLY && s.repeatWeekdays.isEmpty()) {
            setOf(TimeUtils.dayOfWeekMonday1(TimeUtils.localDateOf(s.startAt)))
        } else {
            s.repeatWeekdays
        }
        s.copy(repeatFreq = freq, repeatWeekdays = weekdays)
    }

    fun toggleWeekday(weekday: Int) = _ui.update { s ->
        val next = s.repeatWeekdays.toMutableSet()
        if (weekday in next) next.remove(weekday) else next.add(weekday)
        s.copy(repeatWeekdays = next)
    }
    fun setReminders(v: String) = _ui.update { it.copy(reminderMinutesText = v) }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val reminders = s.reminderMinutesText.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 0..10080 }
                // 结束日支持 yyyy-MM-dd / yyyy-MM-dd HH:mm / epoch（仅来自已有日程的透传值）
                val repeatUntil = TimeUtils.parseFlexibleDateTime(s.repeatUntilText)
                // 表单不再提供结束日/次数输入：新建重复日程且未指定结束时，默认重复 365 次
                val repeatCount = s.repeatCountText.toIntOrNull()
                    ?: if (s.repeatFreq != null && repeatUntil == null) 365 else null
                // 仅「每周」保存自选星期几；未勾选任何天则存 null（回退为与 startAt 同一星期几）
                val repeatWeekdays = if (s.repeatFreq == RepeatFreq.WEEKLY) {
                    s.repeatWeekdays.toList()
                } else {
                    emptyList()
                }
                val saved = if (s.eventId == null) {
                    events.create(
                        title = s.title,
                        startAt = s.startAt,
                        endAt = s.endAt,
                        isAllDay = s.isAllDay,
                        notes = s.notes.ifBlank { null },
                        reminderOffsets = reminders,
                        repeatFreq = s.repeatFreq,
                        repeatUntil = repeatUntil,
                        repeatCount = repeatCount,
                        repeatWeekdays = repeatWeekdays,
                    )
                } else {
                    events.update(
                        id = s.eventId,
                        title = s.title,
                        startAt = s.startAt,
                        endAt = s.endAt,
                        isAllDay = s.isAllDay,
                        notes = s.notes.ifBlank { null },
                        reminderOffsets = reminders,
                        repeatFreq = s.repeatFreq,
                        clearRepeat = s.repeatFreq == null,
                        repeatUntil = repeatUntil,
                        repeatCount = repeatCount,
                        repeatWeekdays = repeatWeekdays,
                    )
                }
                runCatching { planner.replanEntity(ReminderPlanner.TYPE_EVENT, saved.id) }
                saved
            }.onSuccess {
                _ui.update { it.copy(loading = false, saved = true) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun delete() {
        val id = _ui.value.eventId ?: return
        viewModelScope.launch {
            runCatching { events.delete(id) }
            runCatching { planner.replanEntity(ReminderPlanner.TYPE_EVENT, id) }
            _ui.update { it.copy(deleted = true) }
        }
    }

    fun consumeError() = _ui.update { it.copy(error = null) }
}
