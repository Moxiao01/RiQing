package com.riqing.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.riqing.core.common.CourseTextParser
import com.riqing.core.common.WeeksSpecParser
import com.riqing.core.data.repo.CourseRepository
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.SemesterWithSlots
import com.riqing.core.model.Source
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 预览列表里的一条待导入课程；error 非空时不可勾选。 */
data class ImportPreviewItem(
    val name: String,
    val teacher: String?,
    val location: String?,
    val weekdays: List<Int>,
    val periodStart: Int,
    val periodEnd: Int,
    val weeksSpec: String,
    val isBiweekly: Boolean,
    val biweeklyOdd: Boolean?,
    val selected: Boolean = true,
    val error: String? = null,
)

data class ImportResult(
    val success: Int,
    val failed: Int,
    val messages: List<String>,
)

data class CourseImportUiState(
    val loading: Boolean = true,
    val semesterName: String = "",
    val maxWeek: Int = 0,
    val importText: String = "",
    val preview: List<ImportPreviewItem> = emptyList(),
    val failedLines: List<String> = emptyList(),
    val ocrRunning: Boolean = false,
    val ocrError: String? = null,
    val importing: Boolean = false,
    val result: ImportResult? = null,
)

/**
 * 一键导入课程：纯离线流程（不依赖大模型）。
 * 粘贴教务系统文字或截图端侧 OCR 识别 → 规则解析（CourseTextParser）→ 预览勾选 → 批量入库。
 */
@HiltViewModel
class CourseImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courses: CourseRepository,
    private val semesters: SemesterRepository,
    private val planner: ReminderPlanner,
) : ViewModel() {

    private val _ui = MutableStateFlow(CourseImportUiState())
    val ui: StateFlow<CourseImportUiState> = _ui.asStateFlow()

    private var recognizer: TextRecognizer? = null

    init {
        viewModelScope.launch {
            val withSlots = semesters.getWithSlots()
            _ui.update {
                it.copy(
                    loading = false,
                    semesterName = withSlots?.semester?.name.orEmpty(),
                    maxWeek = withSlots?.maxTeachingWeek ?: 0,
                )
            }
        }
    }

    fun setText(v: String) = _ui.update { it.copy(importText = v, result = null) }

    /** 填入示例并立即解析，展示完整导入流程。 */
    fun loadSample() {
        setText(CourseTextParser.SAMPLE)
        parse()
    }

    fun parse() {
        val text = _ui.value.importText
        if (text.isBlank()) return
        viewModelScope.launch {
            val withSlots = semesters.getWithSlots()
            if (withSlots == null) {
                _ui.update { it.copy(preview = emptyList(), failedLines = emptyList()) }
                return@launch
            }
            val parsed = CourseTextParser.parse(text)
            val items = parsed.sessions.map { s ->
                ImportPreviewItem(
                    name = s.name,
                    teacher = s.teacher,
                    location = s.location,
                    weekdays = s.weekdays,
                    periodStart = s.periodStart,
                    periodEnd = s.periodEnd,
                    weeksSpec = s.weeksSpec,
                    isBiweekly = s.isBiweekly,
                    biweeklyOdd = s.biweeklyOdd,
                    error = validate(s, withSlots),
                )
            }
            _ui.update { it.copy(preview = items, failedLines = parsed.failedLines, result = null) }
        }
    }

    private fun validate(
        s: CourseTextParser.ParsedSession,
        withSlots: SemesterWithSlots,
    ): String? {
        val slotIndices = withSlots.slots.map { it.periodIndex }.toSet()
        return when {
            s.name.isBlank() || s.name.length > 60 -> "课程名需 1-60 字"
            s.periodStart < 1 || s.periodEnd > 12 || s.periodEnd < s.periodStart -> "节次应为 1-12"
            slotIndices.isNotEmpty() && (s.periodStart..s.periodEnd).any { it !in slotIndices } ->
                "学期未配置第 ${(s.periodStart..s.periodEnd).first { it !in slotIndices }} 节次"
            s.weeksSpec.isNotBlank() &&
                (WeeksSpecParser.parse(s.weeksSpec, withSlots.maxTeachingWeek).isFailure) ->
                "周次超出学期范围（当前学期共 ${withSlots.maxTeachingWeek} 周）"
            else -> null
        }
    }

    fun toggle(index: Int) = _ui.update { state ->
        val items = state.preview.toMutableList()
        val item = items.getOrNull(index) ?: return@update state
        if (item.error == null) items[index] = item.copy(selected = !item.selected)
        state.copy(preview = items)
    }

    fun importSelected() {
        val state = _ui.value
        if (state.importing || state.preview.none { it.selected && it.error == null }) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true) }
            val withSlots = semesters.getWithSlots()
            if (withSlots == null) {
                _ui.update { it.copy(importing = false) }
                return@launch
            }
            val selected = state.preview.filter { it.selected && it.error == null }
            var success = 0
            val messages = ArrayList<String>()
            val colorByName = LinkedHashMap<String, String>()
            for (item in selected) {
                runCatching {
                    val token = colorByName.getOrPut(item.name) {
                        "c${(colorByName.size % 8) + 1}"
                    }
                    val created = courses.create(
                        name = item.name,
                        semesterId = withSlots.semester.id,
                        teacher = item.teacher,
                        location = item.location,
                        weekday = item.weekdays.first(),
                        weekdays = item.weekdays,
                        periodStart = item.periodStart,
                        periodEnd = item.periodEnd,
                        weeksSpec = item.weeksSpec.ifBlank {
                            WeeksSpecParser.format((1..withSlots.maxTeachingWeek).toList())
                        },
                        isBiweekly = item.isBiweekly,
                        biweeklyOdd = item.biweeklyOdd,
                        colorToken = token,
                        source = Source.IMPORT,
                    )
                    runCatching { planner.replanEntity(ReminderPlanner.TYPE_COURSE, created.id) }
                    created
                }.onSuccess { success++ }
                    .onFailure { messages.add("${item.name}：${it.message ?: "导入失败"}") }
            }
            // 无论成败都清空预览与文本，避免重复点导入产生重复课程
            _ui.update {
                it.copy(
                    importing = false,
                    importText = "",
                    preview = emptyList(),
                    failedLines = emptyList(),
                    result = ImportResult(success, selected.size - success, messages),
                )
            }
        }
    }

    /** 截图导入：端侧中文 OCR（离线）识别文字填入文本框并自动解析预览。 */
    fun onImagePicked(uri: Uri) {
        if (_ui.value.ocrRunning) return
        viewModelScope.launch {
            _ui.update { it.copy(ocrRunning = true, ocrError = null, result = null) }
            runCatching {
                withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }.let { image ->
                    recognize(image)
                }
            }.onSuccess { text ->
                if (text.isBlank()) {
                    _ui.update { it.copy(ocrRunning = false, ocrError = "未识别到文字，请换一张更清晰的截图") }
                } else {
                    setText(text)
                    _ui.update { it.copy(ocrRunning = false) }
                    parse()
                }
            }.onFailure { e ->
                _ui.update { it.copy(ocrRunning = false, ocrError = "识别失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    private suspend fun recognize(image: InputImage): String {
        val client = recognizer ?: TextRecognition
            .getClient(ChineseTextRecognizerOptions.Builder().build())
            .also { recognizer = it }
        return client.process(image).await().text
    }

    override fun onCleared() {
        recognizer?.close()
        recognizer = null
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}
