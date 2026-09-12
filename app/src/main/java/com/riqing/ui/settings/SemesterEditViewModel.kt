package com.riqing.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.data.repo.SemesterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * id：界面内稳定标识（列表 key / 编辑态用）；index：节次号。
 * 课程通过 index 引用节次时间（见 AgendaProjector），因此删除节次后不重排编号。
 */
data class PeriodRow(val id: Long, val index: Int, val startHHmm: String, val endHHmm: String)

data class SemesterEditUiState(
    val semesterId: String? = null,
    val name: String = "",
    val week1Monday: String = LocalDate.now().with(DayOfWeek.MONDAY).toString(),
    val endDate: String = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(15).toString(),
    val isCurrent: Boolean = false,
    val template: SemesterTemplateKind = SemesterTemplateKind.CUSTOM,
    val periods: List<PeriodRow> = emptyList(),
    val editingPeriodId: Long? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

/** 严格解析 HH:mm，非法返回 null。 */
internal fun parsePeriodMinutes(hhmm: String): Int? {
    val parts = hhmm.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** 校验一对节次时间，非法返回可直接展示的文案。保存与卡片内联提示共用。 */
internal fun periodError(startHHmm: String, endHHmm: String): String? {
    val sm = parsePeriodMinutes(startHHmm) ?: return "开始时间格式应为 HH:mm（00:00-23:59）"
    val em = parsePeriodMinutes(endHHmm) ?: return "结束时间格式应为 HH:mm（00:00-23:59）"
    if (em <= sm) return "结束时间必须晚于开始时间"
    return null
}

/**
 * 单个学期的编辑页：semesterId 为空表示新建，否则编辑既有学期。
 * 所有修改先落在本地状态，按"保存"写入数据库后返回卡片列表页。
 */
@HiltViewModel
class SemesterEditViewModel @Inject constructor(
    private val semesters: SemesterRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val semesterId: String? = savedStateHandle.get<String>("semesterId")

    private val _ui = MutableStateFlow(SemesterEditUiState())
    val ui: StateFlow<SemesterEditUiState> = _ui.asStateFlow()

    private var nextRowId = 1L
    private fun nextId(): Long = nextRowId++

    init {
        viewModelScope.launch {
            if (semesterId != null) {
                val withSlots = semesters.getWithSlots(semesterId) ?: return@launch
                _ui.update {
                    it.copy(
                        semesterId = withSlots.semester.id,
                        name = withSlots.semester.name,
                        week1Monday = withSlots.semester.week1Monday,
                        endDate = withSlots.semester.endDate,
                        isCurrent = withSlots.semester.isCurrent,
                        periods = withSlots.slots.map { s ->
                            PeriodRow(
                                id = nextId(),
                                index = s.periodIndex,
                                startHHmm = "%02d:%02d".format(s.startMinutes / 60, s.startMinutes % 60),
                                endHHmm = "%02d:%02d".format(s.endMinutes / 60, s.endMinutes % 60),
                            )
                        },
                    )
                }
            } else {
                // 新建：尚无当前学期时默认勾选"设为当前学期"，避免保存后出现课表真空
                val hasCurrent = semesters.observeAll().first().any { it.isCurrent }
                _ui.update { it.copy(isCurrent = !hasCurrent) }
            }
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setWeek1(v: String) = _ui.update { it.copy(week1Monday = v) }
    fun setEnd(v: String) = _ui.update { it.copy(endDate = v) }
    fun setCurrent(v: Boolean) = _ui.update { it.copy(isCurrent = v) }

    fun startEdit(id: Long) = _ui.update { it.copy(editingPeriodId = id) }
    fun stopEdit() = _ui.update { it.copy(editingPeriodId = null) }

    fun updatePeriod(id: Long, start: String, end: String) {
        _ui.update { s ->
            s.copy(
                periods = s.periods.map {
                    if (it.id == id) it.copy(startHHmm = start, endHHmm = end) else it
                },
                template = SemesterTemplateKind.CUSTOM,
            )
        }
    }

    /** 新节次默认接在最后一节之后：休息 20 分钟、时长 100 分钟；越界时退回兜底值。 */
    fun addPeriod() {
        _ui.update { s ->
            val nextIndex = (s.periods.maxOfOrNull { it.index } ?: 0) + 1
            val fallbackStart = parsePeriodMinutes(s.periods.lastOrNull()?.endHHmm ?: "18:10") ?: 1100
            val startMin = fallbackStart + 20
            val endMin = startMin + 100
            val start: String
            val end: String
            if (endMin <= 23 * 60 + 59) {
                start = "%02d:%02d".format(startMin / 60, startMin % 60)
                end = "%02d:%02d".format(endMin / 60, endMin % 60)
            } else {
                start = "18:30"
                end = "20:10"
            }
            val row = PeriodRow(nextId(), nextIndex, start, end)
            s.copy(periods = s.periods + row, editingPeriodId = row.id, template = SemesterTemplateKind.CUSTOM)
        }
    }

    fun removePeriod(id: Long) {
        _ui.update { s ->
            s.copy(
                periods = s.periods.filterNot { it.id == id },
                editingPeriodId = if (s.editingPeriodId == id) null else s.editingPeriodId,
                template = SemesterTemplateKind.CUSTOM,
            )
        }
    }

    /** 应用模板：整体替换节次列表（仅本地状态，保存后入库）；选自定义则保持现状。 */
    fun applyTemplate(kind: SemesterTemplateKind) {
        _ui.update { s ->
            val periods = if (kind == SemesterTemplateKind.CUSTOM) s.periods
            else templateSlots(kind).mapIndexed { i, (start, end) ->
                PeriodRow(nextId(), i + 1, start, end)
            }
            s.copy(template = kind, periods = periods, editingPeriodId = null)
        }
    }

    fun save() {
        val s = _ui.value
        viewModelScope.launch {
            runCatching {
                val slots = s.periods.map { p ->
                    val err = periodError(p.startHHmm, p.endHHmm)
                    if (err != null) throw IllegalArgumentException("第${p.index}节$err")
                    val sm = parsePeriodMinutes(p.startHHmm)!!
                    val em = parsePeriodMinutes(p.endHHmm)!!
                    p.index to (sm to em)
                }
                semesters.saveSemester(
                    id = s.semesterId,
                    name = s.name,
                    week1Monday = s.week1Monday,
                    endDate = s.endDate,
                    isCurrent = s.isCurrent,
                    slots = slots,
                )
            }.onSuccess {
                _ui.update { it.copy(saved = true, error = null) }
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message) }
            }
        }
    }
}
