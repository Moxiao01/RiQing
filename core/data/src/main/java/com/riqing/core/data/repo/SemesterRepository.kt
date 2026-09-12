package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.toEntity
import com.riqing.core.database.toModel
import com.riqing.core.model.PeriodSlot
import com.riqing.core.model.RiQingError
import com.riqing.core.model.Semester
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SemesterWithSlots(
    val semester: Semester,
    val slots: List<PeriodSlot>,
    val maxTeachingWeek: Int,
)

@Singleton
class SemesterRepository @Inject constructor(
    private val db: RiQingDatabase,
    private val undo: UndoRepository,
) {
    private val semesterDao get() = db.semesterDao()
    private val slotDao get() = db.periodSlotDao()
    private val courseDao get() = db.courseDao()

    fun observeCurrent(): Flow<Semester?> =
        semesterDao.observeCurrent().map { it?.toModel() }

    fun observeAll(): Flow<List<Semester>> =
        semesterDao.observeAll().map { list -> list.map { it.toModel() } }

    /** 全部学期及其节次（响应式），供学期卡片列表使用；当前学期排在最前（见 DAO 排序）。 */
    fun observeAllWithSlots(): Flow<List<SemesterWithSlots>> =
        combine(semesterDao.observeAll(), slotDao.observeAll()) { semesters, slots ->
            semesters.map { entity ->
                val sem = entity.toModel()
                val week1 = TimeUtils.parseIsoDate(sem.week1Monday)
                val end = TimeUtils.parseIsoDate(sem.endDate)
                SemesterWithSlots(
                    semester = sem,
                    slots = slots.filter { it.semesterId == sem.id }.map { it.toModel() },
                    maxTeachingWeek = TimeUtils.maxTeachingWeek(week1, end),
                )
            }
        }

    suspend fun getCurrent(): Semester? = semesterDao.getCurrent()?.toModel()

    suspend fun getById(id: String): Semester? = semesterDao.getById(id)?.toModel()

    suspend fun getOrThrow(id: String): Semester =
        semesterDao.getById(id)?.toModel() ?: throw RiQingError.NotFound("找不到学期")

    suspend fun getWithSlots(semesterId: String? = null): SemesterWithSlots? {
        val sem = if (semesterId == null) getCurrent() else getOrThrow(semesterId)
        if (sem == null) return null
        val slots = slotDao.listBySemester(sem.id).map { it.toModel() }
        val week1 = TimeUtils.parseIsoDate(sem.week1Monday)
        val end = TimeUtils.parseIsoDate(sem.endDate)
        return SemesterWithSlots(
            semester = sem,
            slots = slots,
            maxTeachingWeek = TimeUtils.maxTeachingWeek(week1, end),
        )
    }

    suspend fun saveSemester(
        id: String? = null,
        name: String,
        week1Monday: String,
        endDate: String,
        isCurrent: Boolean,
        slots: List<Pair<Int, Pair<Int, Int>>>,
    ): Semester {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 40) {
            throw RiQingError.Validation("学期名称需 1-40 字", "name")
        }
        val w1 = week1Monday.trim()
        val end = endDate.trim()
        val w1Date = runCatching { TimeUtils.parseIsoDate(w1) }.getOrElse {
            throw RiQingError.Validation("日期格式应为 YYYY-MM-DD", "week1Monday")
        }
        val endDateD = runCatching { TimeUtils.parseIsoDate(end) }.getOrElse {
            throw RiQingError.Validation("日期格式应为 YYYY-MM-DD", "endDate")
        }
        if (w1Date.dayOfWeek.value != 1) {
            throw RiQingError.Validation("第 1 教学周必须是周一", "week1Monday")
        }
        if (endDateD.isBefore(w1Date)) {
            throw RiQingError.Validation("结束日不能早于 week1Monday", "endDate")
        }
        if (slots.isEmpty()) {
            throw RiQingError.Validation("至少配置一个节次", "slots")
        }

        val now = TimeUtils.nowMillis()
        val existing = id?.let { semesterDao.getById(it)?.toModel() }
        val newId = existing?.id ?: TimeUtils.newId()
        if (isCurrent) semesterDao.clearCurrent()
        val entity = existing?.copy(
            name = trimmedName,
            week1Monday = w1,
            endDate = end,
            isCurrent = isCurrent,
            updatedAt = now,
        )?.toEntity() ?: Semester(
            id = newId,
            name = trimmedName,
            week1Monday = w1,
            endDate = end,
            isCurrent = isCurrent,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
        semesterDao.upsert(entity)

        val slotEntities = slots.map { (index, startEnd) ->
            PeriodSlot(
                id = TimeUtils.newId(),
                semesterId = newId,
                periodIndex = index,
                startMinutes = startEnd.first,
                endMinutes = startEnd.second,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
        }
        slotDao.softDeleteBySemester(newId, now)
        slotDao.upsertAll(slotEntities)
        return semesterDao.getById(newId)!!.toModel()
    }

    suspend fun setCurrent(id: String) {
        val sem = getOrThrow(id)
        semesterDao.clearCurrent()
        semesterDao.upsert(sem.copy(isCurrent = true, updatedAt = TimeUtils.nowMillis()).toEntity())
    }

    suspend fun deleteSemester(id: String) {
        val sem = getOrThrow(id)
        undo.record(
            entityType = UndoEntityType.SEMESTER,
            entityId = id,
            action = UndoAction.DELETE,
            payloadJson = undo.encodeSemester(sem),
        )
        val now = TimeUtils.nowMillis()
        semesterDao.softDelete(id, now)
        slotDao.softDeleteBySemester(id, now)
        courseDao.softDeleteBySemester(id, now)
    }
}
