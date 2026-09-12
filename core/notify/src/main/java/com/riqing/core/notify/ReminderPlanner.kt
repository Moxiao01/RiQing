package com.riqing.core.notify

import android.content.Context
import com.riqing.core.common.TimeUtils
import com.riqing.core.data.agenda.AgendaProjector
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.toModel
import com.riqing.core.model.Course
import com.riqing.core.model.Event
import com.riqing.core.model.PeriodSlot
import com.riqing.core.model.Semester
import com.riqing.core.model.Todo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒重排器（D7/D8：EL-05、TD-03、CR-09、NT-03~06）。
 *
 * 每个实体占用一个稳定的 alarm requestCode（由 entityType+entityId 派生），
 * 实体的下一个触发点变化时以 FLAG_UPDATE_CURRENT 覆盖；实体被删/无未来触发点时取消，
 * 从而支持「删除后不再提醒」「改期按新时间」「重启/改时区后补排」。
 * 触发一次后由 ReminderReceiver 回调 [replanEntity] 排下一个偏移。
 */
@Singleton
class ReminderPlanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RiQingDatabase,
    private val semesterRepository: SemesterRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun replanAll(): Unit = withContext(Dispatchers.IO) {
        val now = TimeUtils.nowMillis()
        db.eventDao().getAllIncludingDeleted().forEach { replanEvent(it.toModel(), now) }
        db.todoDao().getAllIncludingDeleted().forEach { replanTodo(it.toModel(), now) }
        val semester = semesterRepository.getCurrent()
        val slots = semester
            ?.let { db.periodSlotDao().listBySemester(it.id).map { s -> s.toModel() } }
            .orEmpty()
        db.courseDao().getAllIncludingDeleted().forEach { replanCourse(it.toModel(), semester, slots, now) }
    }

    suspend fun replanEntity(entityType: String, entityId: String) {
        val now = TimeUtils.nowMillis()
        when (entityType) {
            TYPE_EVENT -> db.eventDao().getById(entityId)?.let { replanEvent(it.toModel(), now) }
                ?: scheduler.cancel(requestCode(TYPE_EVENT, entityId))
            TYPE_TODO -> db.todoDao().getById(entityId)?.let { replanTodo(it.toModel(), now) }
                ?: scheduler.cancel(requestCode(TYPE_TODO, entityId))
            TYPE_COURSE -> {
                val semester = semesterRepository.getCurrent()
                val slots = semester
                    ?.let { db.periodSlotDao().listBySemester(it.id).map { s -> s.toModel() } }
                    .orEmpty()
                db.courseDao().getById(entityId)?.let { replanCourse(it.toModel(), semester, slots, now) }
                    ?: scheduler.cancel(requestCode(TYPE_COURSE, entityId))
            }
        }
    }

    private suspend fun replanEvent(event: Event, now: Long) {
        val code = requestCode(TYPE_EVENT, event.id)
        if (event.isDeleted) return scheduler.cancel(code)
        val nextStart = nextEventStart(event, now) ?: return scheduler.cancel(code)
        val trigger = earliestTrigger(event.reminderOffsets, nextStart, now) ?: return scheduler.cancel(code)
        scheduler.schedule(
            ReminderSpec(
                notificationId = code,
                requestCode = code,
                channelId = NotificationChannels.EVENT_REMINDER,
                title = "⏰ ${event.title}",
                text = "将于 ${TimeUtils.formatAbs(nextStart)} 开始",
                triggerAtMillis = trigger,
                contentIntentAction = ACTION_OPEN_APP,
                extraEntityId = event.id,
                extraEntityType = TYPE_EVENT,
            ),
        )
    }

    private suspend fun replanTodo(todo: Todo, now: Long) {
        val code = requestCode(TYPE_TODO, todo.id)
        if (todo.isDeleted || todo.isDone) return scheduler.cancel(code)
        val due = todo.dueAt ?: return scheduler.cancel(code)
        // 待办默认到点提醒（TD-03）；配置了偏移则按偏移
        val offsets = todo.reminderOffsets.ifEmpty { listOf(0) }
        val trigger = earliestTrigger(offsets, due, now) ?: return scheduler.cancel(code)
        scheduler.schedule(
            ReminderSpec(
                notificationId = code,
                requestCode = code,
                channelId = NotificationChannels.TODO_DUE,
                title = "待办：${todo.title}",
                text = "截止 ${TimeUtils.formatAbs(due)}",
                triggerAtMillis = trigger,
                contentIntentAction = ACTION_OPEN_APP,
                extraEntityId = todo.id,
                extraEntityType = TYPE_TODO,
            ),
        )
    }

    private fun replanCourse(
        course: Course,
        semester: Semester?,
        slots: List<PeriodSlot>,
        now: Long,
    ) {
        val code = requestCode(TYPE_COURSE, course.id)
        if (course.isDeleted || semester == null || course.semesterId != semester.id) {
            return scheduler.cancel(code)
        }
        var day = TimeUtils.localDateOf(now)
        val end = minOf(
            TimeUtils.parseIsoDate(semester.endDate),
            day.plusDays(MAX_SCAN_DAYS),
        )
        while (!day.isAfter(end)) {
            val occ = AgendaProjector.courseOccurrenceOnDay(course, day, semester, slots)
            if (occ != null) {
                val trigger = earliestTrigger(course.reminderOffsets, occ.startAt, now)
                if (trigger != null) {
                    scheduler.schedule(
                        ReminderSpec(
                            notificationId = code,
                            requestCode = code,
                            channelId = NotificationChannels.EVENT_REMINDER,
                            title = "即将上课：${course.name}",
                            text = listOfNotNull(
                                "第${course.periodStart}-${course.periodEnd}节",
                                course.location,
                                TimeUtils.formatAbs(occ.startAt),
                            ).joinToString(" · "),
                            triggerAtMillis = trigger,
                            contentIntentAction = ACTION_OPEN_APP,
                            extraEntityId = course.id,
                            extraEntityType = TYPE_COURSE,
                        ),
                    )
                    return
                }
                // 该实例的所有触发点已过，继续找下一堂课
            }
            day = day.plusDays(1)
        }
        scheduler.cancel(code)
    }

    private suspend fun nextEventStart(event: Event, now: Long): Long? {
        if (event.repeatFreq == null) return event.startAt.takeIf { it > now }
        val exceptions = db.eventExceptionDao().listByEvent(event.id).map { it.toModel() }
        var day = maxOf(TimeUtils.localDateOf(now), TimeUtils.localDateOf(event.startAt))
        val untilDate = event.repeatUntil?.let { TimeUtils.localDateOf(it) }
        val deadline = minOf(
            untilDate ?: day.plusDays(MAX_SCAN_DAYS),
            TimeUtils.localDateOf(now).plusDays(MAX_SCAN_DAYS),
        )
        while (!day.isAfter(deadline)) {
            val occ = AgendaProjector.occurrenceStartOnDay(event, day)
            if (occ != null && occ > now &&
                exceptions.none { it.originalStartAt == occ && it.isDeletedInstance && !it.isDeleted }
            ) {
                return occ
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun earliestTrigger(offsets: List<Int>, anchorAt: Long, now: Long): Long? =
        offsets.map { anchorAt - it * 60_000L }
            .filter { it > now }
            .minOrNull()

    private fun requestCode(entityType: String, entityId: String): Int =
        "$entityType:$entityId".hashCode()

    companion object {
        const val TYPE_EVENT = "event"
        const val TYPE_TODO = "todo"
        const val TYPE_COURSE = "course"
        const val ACTION_OPEN_APP = "com.riqing.action.OPEN_HOME"
        private const val MAX_SCAN_DAYS = 366L
    }
}
