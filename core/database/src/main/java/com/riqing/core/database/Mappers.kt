package com.riqing.core.database

import com.riqing.core.database.entity.CourseEntity
import com.riqing.core.database.entity.EventEntity
import com.riqing.core.database.entity.EventExceptionEntity
import com.riqing.core.database.entity.PeriodSlotEntity
import com.riqing.core.database.entity.SemesterEntity
import com.riqing.core.database.entity.TodoEntity
import com.riqing.core.database.entity.UndoSnapshotEntity
import com.riqing.core.model.Course
import com.riqing.core.model.Event
import com.riqing.core.model.EventException
import com.riqing.core.model.PeriodSlot
import com.riqing.core.model.RepeatFreq
import com.riqing.core.model.Semester
import com.riqing.core.model.Source
import com.riqing.core.model.Todo
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import com.riqing.core.model.UndoSnapshot

object JsonLists {
    fun encodeInts(list: List<Int>): String =
        list.joinToString(prefix = "[", postfix = "]")

    fun decodeInts(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim().removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
    }
}

fun SemesterEntity.toModel(): Semester = Semester(
    id = id,
    name = name,
    week1Monday = week1Monday,
    endDate = endDate,
    isCurrent = isCurrent,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Semester.toEntity(): SemesterEntity = SemesterEntity(
    id = id,
    name = name,
    week1Monday = week1Monday,
    endDate = endDate,
    isCurrent = isCurrent,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PeriodSlotEntity.toModel(): PeriodSlot = PeriodSlot(
    id = id,
    semesterId = semesterId,
    periodIndex = periodIndex,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PeriodSlot.toEntity(): PeriodSlotEntity = PeriodSlotEntity(
    id = id,
    semesterId = semesterId,
    periodIndex = periodIndex,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CourseEntity.toModel(): Course = Course(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    location = location,
    weekday = weekday,
    weekdays = JsonLists.decodeInts(weekdaysJson).ifEmpty { listOf(weekday) }.distinct().sorted(),
    periodStart = periodStart,
    periodEnd = periodEnd,
    weeksSpec = weeksSpec,
    isBiweekly = isBiweekly,
    biweeklyOdd = biweeklyOdd,
    reminderOffsets = JsonLists.decodeInts(reminderOffsetsJson),
    colorToken = colorToken,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.MANUAL),
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    location = location,
    weekday = effectiveWeekdays.first(),
    weekdaysJson = JsonLists.encodeInts(effectiveWeekdays),
    periodStart = periodStart,
    periodEnd = periodEnd,
    weeksSpec = weeksSpec,
    isBiweekly = isBiweekly,
    biweeklyOdd = biweeklyOdd,
    reminderOffsetsJson = JsonLists.encodeInts(reminderOffsets),
    colorToken = colorToken,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source.name,
)

fun EventEntity.toModel(): Event = Event(
    id = id,
    title = title,
    startAt = startAt,
    endAt = endAt,
    isAllDay = isAllDay,
    notes = notes,
    reminderOffsets = JsonLists.decodeInts(reminderOffsetsJson),
    repeatFreq = repeatFreq?.let { runCatching { RepeatFreq.valueOf(it) }.getOrNull() },
    repeatUntil = repeatUntil,
    repeatCount = repeatCount,
    repeatWeekdays = repeatWeekdaysJson?.let { JsonLists.decodeInts(it) }?.ifEmpty { null },
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.MANUAL),
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    startAt = startAt,
    endAt = endAt,
    isAllDay = isAllDay,
    notes = notes,
    reminderOffsetsJson = JsonLists.encodeInts(reminderOffsets),
    repeatFreq = repeatFreq?.name,
    repeatUntil = repeatUntil,
    repeatCount = repeatCount,
    repeatWeekdaysJson = repeatWeekdays?.let { JsonLists.encodeInts(it) },
    source = source.name,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EventExceptionEntity.toModel(): EventException = EventException(
    id = id,
    eventId = eventId,
    originalStartAt = originalStartAt,
    isDeletedInstance = isDeletedInstance,
    overrideStartAt = overrideStartAt,
    overrideEndAt = overrideEndAt,
    overrideTitle = overrideTitle,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EventException.toEntity(): EventExceptionEntity = EventExceptionEntity(
    id = id,
    eventId = eventId,
    originalStartAt = originalStartAt,
    isDeletedInstance = isDeletedInstance,
    overrideStartAt = overrideStartAt,
    overrideEndAt = overrideEndAt,
    overrideTitle = overrideTitle,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TodoEntity.toModel(): Todo = Todo(
    id = id,
    title = title,
    dueAt = dueAt,
    notes = notes,
    isDone = isDone,
    doneAt = doneAt,
    reminderOffsets = JsonLists.decodeInts(reminderOffsetsJson),
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.MANUAL),
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    dueAt = dueAt,
    notes = notes,
    isDone = isDone,
    doneAt = doneAt,
    reminderOffsetsJson = JsonLists.encodeInts(reminderOffsets),
    source = source.name,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UndoSnapshotEntity.toModel(): UndoSnapshot = UndoSnapshot(
    id = id,
    entityType = runCatching { UndoEntityType.valueOf(entityType) }.getOrDefault(UndoEntityType.EVENT),
    entityId = entityId,
    action = runCatching { UndoAction.valueOf(action) }.getOrDefault(UndoAction.UPDATE),
    payloadJson = payloadJson,
    createdAt = createdAt,
    expiresAt = expiresAt,
)

fun UndoSnapshot.toEntity(): UndoSnapshotEntity = UndoSnapshotEntity(
    id = id,
    entityType = entityType.name,
    entityId = entityId,
    action = action.name,
    payloadJson = payloadJson,
    createdAt = createdAt,
    expiresAt = expiresAt,
)
