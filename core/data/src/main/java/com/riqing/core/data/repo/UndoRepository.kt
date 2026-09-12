package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.database.dao.UndoSnapshotDao
import com.riqing.core.database.entity.UndoSnapshotEntity
import com.riqing.core.database.toModel
import com.riqing.core.model.Course
import com.riqing.core.model.Event
import com.riqing.core.model.EventException
import com.riqing.core.model.Semester
import com.riqing.core.model.Todo
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import com.riqing.core.model.UndoSnapshot
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UndoRepository @Inject constructor(
    private val dao: UndoSnapshotDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun purgeExpired() {
        dao.purgeExpired(TimeUtils.nowMillis())
    }

    suspend fun latestActive(): UndoSnapshot? {
        purgeExpired()
        return dao.latestActive(TimeUtils.nowMillis())?.toModel()
    }

    suspend fun listActive(): List<UndoSnapshot> {
        purgeExpired()
        return dao.listActive(TimeUtils.nowMillis()).map { it.toModel() }
    }

    suspend fun getById(id: String): UndoSnapshot? = dao.getById(id)?.toModel()

    suspend fun record(
        entityType: UndoEntityType,
        entityId: String,
        action: UndoAction,
        payloadJson: String,
    ): UndoSnapshot {
        val now = TimeUtils.nowMillis()
        val snap = UndoSnapshotEntity(
            id = TimeUtils.newId(),
            entityType = entityType.name,
            entityId = entityId,
            action = action.name,
            payloadJson = payloadJson,
            createdAt = now,
            expiresAt = now + 10 * 60 * 1000L,
        )
        dao.upsert(snap)
        return snap.toModel()
    }

    suspend fun consume(id: String) {
        dao.delete(id)
    }

    fun encodeEvent(value: Event): String = json.encodeToString(Event.serializer(), value)
    fun encodeTodo(value: Todo): String = json.encodeToString(Todo.serializer(), value)
    fun encodeCourse(value: Course): String = json.encodeToString(Course.serializer(), value)
    fun encodeSemester(value: Semester): String = json.encodeToString(Semester.serializer(), value)
    fun encodeException(value: EventException): String =
        json.encodeToString(EventException.serializer(), value)

    fun decodeEvent(raw: String): Event = json.decodeFromString(Event.serializer(), raw)
    fun decodeTodo(raw: String): Todo = json.decodeFromString(Todo.serializer(), raw)
    fun decodeCourse(raw: String): Course = json.decodeFromString(Course.serializer(), raw)
    fun decodeSemester(raw: String): Semester = json.decodeFromString(Semester.serializer(), raw)
    fun decodeException(raw: String): EventException =
        json.decodeFromString(EventException.serializer(), raw)
}
