package com.riqing.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.SemesterWithSlots
import com.riqing.core.data.repo.CourseRepository
import com.riqing.core.model.Course
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TimetableUiState(
    val semester: SemesterWithSlots? = null,
    val teachingWeek: Int = 1,
    val courses: List<Course> = emptyList(),
    val loading: Boolean = true,
    val isCurrentWeek: Boolean = true,
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val semesterRepository: SemesterRepository,
    private val courseRepository: CourseRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TimetableUiState())
    val ui: StateFlow<TimetableUiState> = _ui.asStateFlow()

    /** 用户是否已手动切换过周次；未切换时跟随「当前教学周」。 */
    private var weekPinned = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val with = semesterRepository.getWithSlots()
            if (with == null) {
                _ui.update { it.copy(semester = null, courses = emptyList(), loading = false) }
                return@launch
            }
            val today = LocalDate.now()
            val currentWeek = TimeUtils.teachingWeekIndex(
                TimeUtils.parseIsoDate(with.semester.week1Monday),
                today,
            ).coerceIn(1, with.maxTeachingWeek.coerceAtLeast(1))
            val week = if (weekPinned) _ui.value.teachingWeek else currentWeek
            val courses = courseRepository.listBySemester(with.semester.id)
            _ui.update {
                it.copy(
                    semester = with,
                    teachingWeek = week,
                    courses = courses,
                    loading = false,
                    isCurrentWeek = week == currentWeek,
                )
            }
        }
    }

    /** 解除周次锁定并刷新，回到包含今天的教学周（今天在学期外时回到边界周）。 */
    fun backToCurrentWeek() {
        weekPinned = false
        refresh()
    }

    fun shiftWeek(delta: Int) {
        val max = _ui.value.semester?.maxTeachingWeek ?: 1
        val next = (_ui.value.teachingWeek + delta).coerceIn(1, max.coerceAtLeast(1))
        weekPinned = true
        _ui.update { it.copy(teachingWeek = next) }
        refresh()
    }

    /** 该星期在指定教学周的课程列表；week 由调用方从 UiState 传入，保证翻周后 UI 能重组刷新。 */
    fun coursesOn(weekday: Int, week: Int): List<Course> =
        _ui.value.courses.filter {
            weekday in it.effectiveWeekdays &&
                com.riqing.core.common.WeeksSpecParser.contains(it.weeksSpec, week) &&
                (!it.isBiweekly || (it.biweeklyOdd == true) == (week % 2 == 1))
        }
}
