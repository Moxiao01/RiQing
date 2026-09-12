package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.data.validate.FieldValidator
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.entity.EventExceptionEntity
import com.riqing.core.database.toEntity
import com.riqing.core.database.toModel
import com.riqing.core.model.Event
import com.riqing.core.model.EventException
import com.riqing.core.model.RepeatFreq
import com.riqing.core.model.RiQingError
import com.riqing.core.model.Source
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val db: RiQingDatabase,
    private val undo: UndoRepository,
) {
    private val eventDao get() = db.eventDao()
    private val exceptionDao get() = db.eventExceptionDao()

    suspend fun get(id: String): Event? = eventDao.getById(id)?.toModel()

    suspend fun getOrThrow(id: String): Event =
        eventDao.getById(id)?.toModel() ?: throw RiQingError.NotFound("找不到日程")

    suspend fun listExceptions(eventId: String): List<EventException> =
        exceptionDao.listByEvent(eventId).map { it.toModel() }

    suspend fun allExceptions(): List<EventException> =
        exceptionDao.getAll().map { it.toModel() }

    suspend fun search(keyword: String): List<Event> =
        eventDao.search(keyword).map { it.toModel() }

    suspend fun create(
        title: String,
        startAt: Long,
        endAt: Long,
        isAllDay: Boolean = false,
        notes: String? = null,
        reminderOffsets: List<Int> = emptyList(),
        repeatFreq: RepeatFreq? = null,
        repeatUntil: Long? = null,
        repeatCount: Int? = null,
        repeatWeekdays: List<Int>? = null,
        source: Source = Source.MANUAL,
    ): Event {
        val t = FieldValidator.requireTitle(title)
        FieldValidator.validateEventTime(startAt, endAt, isAllDay)
        FieldValidator.optionalNotes(notes)
        FieldValidator.requireOffsets(reminderOffsets)
        FieldValidator.validateRepeat(repeatFreq?.name, repeatUntil, repeatCount, startAt)
        val weekdays = FieldValidator.requireWeekdays(repeatWeekdays).orEmpty().ifEmpty { null }

        val now = TimeUtils.nowMillis()
        val event = Event(
            id = TimeUtils.newId(),
            title = t,
            startAt = startAt,
            endAt = endAt,
            isAllDay = isAllDay,
            notes = FieldValidator.optionalNotes(notes),
            reminderOffsets = reminderOffsets,
            repeatFreq = repeatFreq,
            repeatUntil = repeatUntil,
            repeatCount = repeatCount,
            repeatWeekdays = weekdays,
            source = source,
            createdAt = now,
            updatedAt = now,
        )
        eventDao.upsert(event.toEntity())
        undo.record(UndoEntityType.EVENT, event.id, UndoAction.CREATE, undo.encodeEvent(event))
        return event
    }

    suspend fun update(
        id: String,
        title: String? = null,
        startAt: Long? = null,
        endAt: Long? = null,
        isAllDay: Boolean? = null,
        notes: String? = null,
        reminderOffsets: List<Int>? = null,
        repeatFreq: RepeatFreq? = null,
        clearRepeat: Boolean = false,
        repeatUntil: Long? = null,
        repeatCount: Int? = null,
        /** null = 保留原值；非 null（可空列表）= 覆盖，空列表清除自选星期几。 */
        repeatWeekdays: List<Int>? = null,
    ): Event {
        val old = getOrThrow(id)
        val nextWeekdays = when {
            clearRepeat -> null
            repeatWeekdays != null -> FieldValidator.requireWeekdays(repeatWeekdays).orEmpty().ifEmpty { null }
            else -> old.repeatWeekdays
        }
        val next = old.copy(
            title = title?.let { FieldValidator.requireTitle(it) } ?: old.title,
            startAt = startAt ?: old.startAt,
            endAt = endAt ?: old.endAt,
            isAllDay = isAllDay ?: old.isAllDay,
            notes = notes?.let { FieldValidator.optionalNotes(it) } ?: old.notes,
            reminderOffsets = reminderOffsets?.let { FieldValidator.requireOffsets(it) } ?: old.reminderOffsets,
            repeatFreq = if (clearRepeat) null else (repeatFreq ?: old.repeatFreq),
            repeatUntil = if (clearRepeat) null else (repeatUntil ?: old.repeatUntil),
            repeatCount = if (clearRepeat) null else (repeatCount ?: old.repeatCount),
            repeatWeekdays = nextWeekdays,
            updatedAt = TimeUtils.nowMillis(),
        )
        FieldValidator.validateEventTime(next.startAt, next.endAt, next.isAllDay)
        FieldValidator.validateRepeat(
            next.repeatFreq?.name,
            next.repeatUntil,
            next.repeatCount,
            next.startAt,
        )
        undo.record(UndoEntityType.EVENT, id, UndoAction.UPDATE, undo.encodeEvent(old))
        eventDao.upsert(next.toEntity())
        return next
    }

    suspend fun delete(id: String, occurrenceStartAt: Long? = null) {
        val event = getOrThrow(id)
        if (occurrenceStartAt != null && event.repeatFreq != null) {
            val existing = exceptionDao.find(id, occurrenceStartAt)?.toModel()
            val exception = EventException(
                id = existing?.id ?: TimeUtils.newId(),
                eventId = id,
                originalStartAt = occurrenceStartAt,
                isDeletedInstance = true,
                createdAt = existing?.createdAt ?: TimeUtils.nowMillis(),
                updatedAt = TimeUtils.nowMillis(),
            )
            undo.record(
                UndoEntityType.EVENT_EXCEPTION,
                exception.id,
                UndoAction.CREATE,
                undo.encodeException(exception),
            )
            exceptionDao.upsert(exception.toEntity())
            return
        }
        undo.record(UndoEntityType.EVENT, id, UndoAction.DELETE, undo.encodeEvent(event))
        eventDao.softDelete(id, TimeUtils.nowMillis())
    }

    suspend fun updateOccurrence(
        eventId: String,
        occurrenceStartAt: Long,
        title: String? = null,
        startAt: Long? = null,
        endAt: Long? = null,
    ): EventException {
        val event = getOrThrow(eventId)
        if (event.repeatFreq == null) {
            update(eventId, title = title, startAt = startAt, endAt = endAt)
            return EventException(
                id = TimeUtils.newId(),
                eventId = eventId,
                originalStartAt = occurrenceStartAt,
                overrideStartAt = startAt,
                overrideEndAt = endAt,
                overrideTitle = title,
                createdAt = TimeUtils.nowMillis(),
                updatedAt = TimeUtils.nowMillis(),
            )
        }
        val now = TimeUtils.nowMillis()
        val existing = exceptionDao.find(eventId, occurrenceStartAt)?.toModel()
        val exception = (existing ?: EventException(
            id = TimeUtils.newId(),
            eventId = eventId,
            originalStartAt = occurrenceStartAt,
            createdAt = now,
            updatedAt = now,
        )).copy(
            isDeletedInstance = false,
            overrideTitle = title?.let { FieldValidator.requireTitle(it) },
            overrideStartAt = startAt ?: existing?.overrideStartAt,
            overrideEndAt = endAt ?: existing?.overrideEndAt,
            updatedAt = now,
        )
        val oStart = exception.overrideStartAt
        val oEnd = exception.overrideEndAt
        if (oStart != null && oEnd != null) {
            FieldValidator.validateEventTime(oStart, oEnd, isAllDay = false)
        }
        exceptionDao.upsert(exception.toEntity())
        return exception
    }

    suspend fun restore(eventId: String) {
        eventDao.restore(eventId, TimeUtils.nowMillis())
    }

    suspend fun undoById(snapshotId: String): String {
        val snap = undo.getById(snapshotId) ?: throw RiQingError.NotFound("撤销窗口已过期")
        when (snap.entityType) {
            UndoEntityType.EVENT -> when (snap.action) {
                UndoAction.CREATE -> {
                    val payload = undo.decodeEvent(snap.payloadJson)
                    eventDao.softDelete(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.DELETE -> {
                    val payload = undo.decodeEvent(snap.payloadJson)
                    eventDao.restore(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.UPDATE -> {
                    val payload = undo.decodeEvent(snap.payloadJson)
                    eventDao.upsert(payload.toEntity())
                }
            }
            UndoEntityType.EVENT_EXCEPTION -> {
                val payload = undo.decodeException(snap.payloadJson)
                exceptionDao.softDelete(payload.id, TimeUtils.nowMillis())
            }
            else -> throw RiQingError.Validation("该快照不支持在此处撤销")
        }
        undo.consume(snap.id)
        return "已撤销：${snap.action.name} ${snap.entityType}"
    }
}
