package com.riqing.ui.course

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.data.repo.CourseRepository
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.model.Semester
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseEditUiState(
    val courseId: String? = null,
    val semesters: List<Semester> = emptyList(),
    val semesterId: String = "",
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val weekdays: Set<Int> = setOf(1),
    val periodStart: Int = 1,
    val periodEnd: Int = 2,
    val weeksSpec: String = "1-16",
    val isBiweekly: Boolean = false,
    val biweeklyOdd: Boolean = true,
    val colorToken: String = "c1",
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courses: CourseRepository,
    private val semesters: SemesterRepository,
    private val planner: ReminderPlanner,
) : ViewModel() {
    private val courseId: String? = savedStateHandle["courseId"]
    private val _ui = MutableStateFlow(CourseEditUiState(courseId = courseId))
    val ui: StateFlow<CourseEditUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val current = semesters.getCurrent()
            _ui.update {
                it.copy(semesterId = current?.id.orEmpty(), semesters = listOfNotNull(current))
            }
            if (courseId != null) load(courseId)
        }
    }

    private suspend fun load(id: String) {
        val c = courses.get(id) ?: return
        _ui.update {
            it.copy(
                semesterId = c.semesterId,
                name = c.name,
                teacher = c.teacher.orEmpty(),
                location = c.location.orEmpty(),
                weekdays = c.effectiveWeekdays.toSet(),
                periodStart = c.periodStart,
                periodEnd = c.periodEnd,
                weeksSpec = c.weeksSpec,
                isBiweekly = c.isBiweekly,
                biweeklyOdd = c.biweeklyOdd ?: true,
                colorToken = c.colorToken,
            )
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setTeacher(v: String) = _ui.update { it.copy(teacher = v) }
    fun setLocation(v: String) = _ui.update { it.copy(location = v) }

    /** 切换某天是否选中；一周多节的课可多选，至少保留一天。 */
    fun toggleWeekday(v: Int) = _ui.update {
        val next = if (v in it.weekdays) it.weekdays - v else it.weekdays + v
        if (next.isEmpty()) it else it.copy(weekdays = next)
    }

    fun setPeriodStart(v: Int) = _ui.update { it.copy(periodStart = v) }
    fun setPeriodEnd(v: Int) = _ui.update { it.copy(periodEnd = v) }
    fun setWeeks(v: String) = _ui.update { it.copy(weeksSpec = v) }
    fun setBiweekly(v: Boolean) = _ui.update { it.copy(isBiweekly = v) }
    fun setBiweeklyOdd(v: Boolean) = _ui.update { it.copy(biweeklyOdd = v) }
    fun setColor(v: String) = _ui.update { it.copy(colorToken = v) }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            runCatching {
                val saved = if (s.courseId == null) {
                    courses.create(
                        name = s.name,
                        semesterId = s.semesterId.ifBlank { null },
                        teacher = s.teacher.ifBlank { null },
                        location = s.location.ifBlank { null },
                        weekday = s.weekdays.first(),
                        weekdays = s.weekdays.sorted(),
                        periodStart = s.periodStart,
                        periodEnd = s.periodEnd,
                        weeksSpec = s.weeksSpec,
                        isBiweekly = s.isBiweekly,
                        biweeklyOdd = if (s.isBiweekly) s.biweeklyOdd else null,
                        colorToken = s.colorToken,
                    )
                } else {
                    courses.update(
                        id = s.courseId,
                        name = s.name,
                        teacher = s.teacher,
                        location = s.location,
                        weekday = s.weekdays.first(),
                        weekdays = s.weekdays.sorted(),
                        periodStart = s.periodStart,
                        periodEnd = s.periodEnd,
                        weeksSpec = s.weeksSpec,
                        isBiweekly = s.isBiweekly,
                        biweeklyOdd = if (s.isBiweekly) s.biweeklyOdd else null,
                        clearBiweekly = !s.isBiweekly,
                        colorToken = s.colorToken,
                    )
                }
                runCatching { planner.replanEntity(ReminderPlanner.TYPE_COURSE, saved.id) }
                saved
            }.onSuccess {
                _ui.update { it.copy(saved = true, error = null) }
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message) }
            }
        }
    }

    fun delete() {
        val id = courseId ?: return
        viewModelScope.launch {
            runCatching { courses.delete(id) }
            runCatching { planner.replanEntity(ReminderPlanner.TYPE_COURSE, id) }
            _ui.update { it.copy(deleted = true) }
        }
    }
}
