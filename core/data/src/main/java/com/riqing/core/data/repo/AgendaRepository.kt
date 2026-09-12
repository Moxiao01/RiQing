package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.data.agenda.AgendaProjector
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.toModel
import com.riqing.core.model.DayAgenda
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaRepository @Inject constructor(
    private val db: RiQingDatabase,
    private val semesterRepository: SemesterRepository,
) {
    suspend fun dayAgenda(date: LocalDate): DayAgenda {
        val dayStart = TimeUtils.startOfDay(date)
        val dayEnd = TimeUtils.endOfDayExclusive(date)

        // SQL 预筛（重复事件含 repeatUntil 未过期系列），展开/过滤在 Projector 内完成
        val events = db.eventDao().listActiveInRange(dayStart, dayEnd).map { it.toModel() }

        val exceptions = db.eventExceptionDao().getAll().map { it.toModel() }

        val semester = semesterRepository.getCurrent()
        val slots = semester
            ?.let { db.periodSlotDao().listBySemester(it.id).map { s -> s.toModel() } }
            .orEmpty()
        val courses = semester
            ?.let { db.courseDao().listBySemester(it.id).map { c -> c.toModel() } }
            .orEmpty()

        val todos = db.todoDao().listForDayAgenda(dayStart, dayEnd).map { it.toModel() }

        return AgendaProjector.buildDay(
            day = date,
            events = events,
            exceptions = exceptions,
            courses = courses,
            semester = semester,
            slots = slots,
            todos = todos,
        )
    }
}
