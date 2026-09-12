package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.common.WeeksSpecParser
import com.riqing.core.data.validate.FieldValidator
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.toEntity
import com.riqing.core.database.toModel
import com.riqing.core.model.Course
import com.riqing.core.model.RiQingError
import com.riqing.core.model.Source
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val db: RiQingDatabase,
    private val undo: UndoRepository,
    private val semesterRepository: SemesterRepository,
) {
    private val courseDao get() = db.courseDao()

    fun observeBySemester(semesterId: String): Flow<List<Course>> =
        courseDao.observeBySemester(semesterId).map { list -> list.map { it.toModel() } }

    fun observeAll(): Flow<List<Course>> =
        courseDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Course? = courseDao.getById(id)?.toModel()

    suspend fun getOrThrow(id: String): Course =
        courseDao.getById(id)?.toModel() ?: throw RiQingError.NotFound("找不到课程")

    suspend fun listBySemester(semesterId: String): List<Course> =
        courseDao.listBySemester(semesterId).map { it.toModel() }

    suspend fun search(keyword: String): List<Course> =
        courseDao.search(keyword).map { it.toModel() }

    suspend fun create(
        name: String,
        semesterId: String? = null,
        teacher: String? = null,
        location: String? = null,
        weekday: Int,
        weekdays: List<Int>? = null,
        periodStart: Int,
        periodEnd: Int,
        weeksSpec: String,
        isBiweekly: Boolean = false,
        biweeklyOdd: Boolean? = null,
        reminderOffsets: List<Int> = listOf(10),
        colorToken: String = "c1",
        source: Source = Source.MANUAL,
    ): Course {
        val sem = if (semesterId != null) {
            semesterRepository.getOrThrow(semesterId)
        } else {
            semesterRepository.getCurrent() ?: throw RiQingError.Validation("尚未配置学期", "semesterId")
        }
        val withSlots = semesterRepository.getWithSlots(sem.id)
            ?: throw RiQingError.Validation("学期无效")
        val maxWeek = withSlots.maxTeachingWeek

        val courseName = FieldValidator.requireCourseName(name)
        val days = weekdays
            ?.takeIf { it.isNotEmpty() }
            ?.let { FieldValidator.requireWeekdays(it) }
            ?: listOf(FieldValidator.requireWeekday(weekday))
        val (pStart, pEnd) = FieldValidator.requirePeriods(periodStart, periodEnd)
        val weeks = FieldValidator.requireWeeksSpec(weeksSpec, maxWeek)
        FieldValidator.optionalShort(teacher, 40, "teacher")
        FieldValidator.optionalShort(location, 80, "location")
        FieldValidator.requireOffsets(reminderOffsets)
        if (isBiweekly && biweeklyOdd == null) {
            throw RiQingError.Validation("单双周课程必须指定单周/双周", "biweeklyOdd")
        }
        val slotIndices = withSlots.slots.map { it.periodIndex }.toSet()
        if (slotIndices.isNotEmpty()) {
            (pStart..pEnd).forEach {
                if (it !in slotIndices) {
                    throw RiQingError.Validation("学期未配置第 $it 节次", "periodStart")
                }
            }
        }

        val now = TimeUtils.nowMillis()
        val course = Course(
            id = TimeUtils.newId(),
            semesterId = sem.id,
            name = courseName,
            teacher = FieldValidator.optionalShort(teacher, 40, "teacher"),
            location = FieldValidator.optionalShort(location, 80, "location"),
            weekday = days.first(),
            weekdays = days,
            periodStart = pStart,
            periodEnd = pEnd,
            weeksSpec = weeks,
            isBiweekly = isBiweekly,
            biweeklyOdd = biweeklyOdd,
            reminderOffsets = reminderOffsets,
            colorToken = colorToken,
            source = source,
            createdAt = now,
            updatedAt = now,
        )
        courseDao.upsert(course.toEntity())
        undo.record(UndoEntityType.COURSE, course.id, UndoAction.CREATE, undo.encodeCourse(course))
        return course
    }

    suspend fun update(
        id: String,
        name: String? = null,
        teacher: String? = null,
        location: String? = null,
        weekday: Int? = null,
        weekdays: List<Int>? = null,
        periodStart: Int? = null,
        periodEnd: Int? = null,
        weeksSpec: String? = null,
        isBiweekly: Boolean? = null,
        biweeklyOdd: Boolean? = null,
        clearBiweekly: Boolean = false,
        reminderOffsets: List<Int>? = null,
        colorToken: String? = null,
    ): Course {
        val old = getOrThrow(id)
        val withSlots = semesterRepository.getWithSlots(old.semesterId)
            ?: throw RiQingError.Validation("学期无效")
        val maxWeek = withSlots.maxTeachingWeek

        val nextBiweekly = if (clearBiweekly) false else (isBiweekly ?: old.isBiweekly)
        val nextOdd = when {
            clearBiweekly -> null
            isBiweekly != null && !isBiweekly -> null
            biweeklyOdd != null -> biweeklyOdd
            isBiweekly == true && old.biweeklyOdd == null ->
                throw RiQingError.Validation("单双周课程必须指定单周/双周", "biweeklyOdd")
            else -> old.biweeklyOdd
        }
        if (nextBiweekly && nextOdd == null) {
            throw RiQingError.Validation("单双周课程必须指定单周/双周", "biweeklyOdd")
        }

        val pStart = periodStart ?: old.periodStart
        val pEnd = periodEnd ?: old.periodEnd
        val (ps, pe) = FieldValidator.requirePeriods(pStart, pEnd)
        val days = weekdays
            ?.takeIf { it.isNotEmpty() }
            ?.let { FieldValidator.requireWeekdays(it) }
            ?: listOf(weekday?.let { FieldValidator.requireWeekday(it) } ?: old.weekday)

        val next = old.copy(
            name = name?.let { FieldValidator.requireCourseName(it) } ?: old.name,
            teacher = teacher?.let { FieldValidator.optionalShort(it, 40, "teacher") } ?: old.teacher,
            location = location?.let { FieldValidator.optionalShort(it, 80, "location") } ?: old.location,
            weekday = days.first(),
            weekdays = days,
            periodStart = ps,
            periodEnd = pe,
            weeksSpec = weeksSpec?.let { FieldValidator.requireWeeksSpec(it, maxWeek) } ?: old.weeksSpec,
            isBiweekly = nextBiweekly,
            biweeklyOdd = nextOdd,
            reminderOffsets = reminderOffsets?.let { FieldValidator.requireOffsets(it) } ?: old.reminderOffsets,
            colorToken = colorToken ?: old.colorToken,
            updatedAt = TimeUtils.nowMillis(),
        )
        undo.record(UndoEntityType.COURSE, id, UndoAction.UPDATE, undo.encodeCourse(old))
        courseDao.upsert(next.toEntity())
        return next
    }

    suspend fun delete(id: String) {
        val course = getOrThrow(id)
        undo.record(UndoEntityType.COURSE, id, UndoAction.DELETE, undo.encodeCourse(course))
        courseDao.softDelete(id, TimeUtils.nowMillis())
    }

    suspend fun weeksOf(course: Course): Set<Int> =
        WeeksSpecParser.parse(course.weeksSpec).getOrDefault(emptySet())

    suspend fun undoById(snapshotId: String): String {
        val snap = undo.getById(snapshotId) ?: throw RiQingError.NotFound("撤销窗口已过期")
        when (snap.entityType) {
            UndoEntityType.COURSE -> when (snap.action) {
                UndoAction.CREATE -> {
                    val payload = undo.decodeCourse(snap.payloadJson)
                    courseDao.softDelete(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.DELETE -> {
                    val payload = undo.decodeCourse(snap.payloadJson)
                    courseDao.restore(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.UPDATE -> {
                    val payload = undo.decodeCourse(snap.payloadJson)
                    courseDao.upsert(payload.toEntity())
                }
            }
            UndoEntityType.SEMESTER -> {
                val payload = undo.decodeSemester(snap.payloadJson)
                db.semesterDao().restore(payload.id, TimeUtils.nowMillis())
            }
            else -> throw RiQingError.Validation("该快照不支持在此处撤销")
        }
        undo.consume(snap.id)
        return "已撤销：${snap.action.name} ${snap.entityType}"
    }
}
