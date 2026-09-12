package com.riqing.core.data.repo

import com.riqing.core.common.TimeUtils
import com.riqing.core.data.validate.FieldValidator
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.database.toEntity
import com.riqing.core.database.toModel
import com.riqing.core.model.RiQingError
import com.riqing.core.model.Source
import com.riqing.core.model.Todo
import com.riqing.core.model.UndoAction
import com.riqing.core.model.UndoEntityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val db: RiQingDatabase,
    private val undo: UndoRepository,
) {
    private val todoDao get() = db.todoDao()

    fun observeActive(): Flow<List<Todo>> =
        todoDao.observeActive().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Todo? = todoDao.getById(id)?.toModel()

    suspend fun getOrThrow(id: String): Todo =
        todoDao.getById(id)?.toModel() ?: throw RiQingError.NotFound("找不到待办")

    suspend fun listForDay(dateIso: String): List<Todo> {
        val date = TimeUtils.parseIsoDate(dateIso)
        return todoDao.listForDayAgenda(
            TimeUtils.startOfDay(date),
            TimeUtils.endOfDayExclusive(date),
        ).map { it.toModel() }
    }

    suspend fun listActive(): List<Todo> = todoDao.listActive().map { it.toModel() }

    /** 有截止且已过期的未完成待办（今日视图「已逾期」分组用，D2 §5.3）。 */
    suspend fun listOverdue(now: Long = TimeUtils.nowMillis()): List<Todo> =
        todoDao.listOverdue(now).map { it.toModel() }

    /** 有截止且尚未到期的未完成待办（今日视图「即将到来」分组用）。 */
    suspend fun listUpcoming(after: Long = TimeUtils.endOfDayExclusive(LocalDate.now())): List<Todo> =
        todoDao.listUpcoming(after).map { it.toModel() }

    suspend fun search(keyword: String): List<Todo> =
        todoDao.search(keyword).map { it.toModel() }

    suspend fun create(
        title: String,
        dueAt: Long? = null,
        notes: String? = null,
        reminderOffsets: List<Int> = emptyList(),
        source: Source = Source.MANUAL,
    ): Todo {
        val t = FieldValidator.requireTitle(title)
        FieldValidator.optionalNotes(notes)
        FieldValidator.requireOffsets(reminderOffsets)
        val now = TimeUtils.nowMillis()
        val todo = Todo(
            id = TimeUtils.newId(),
            title = t,
            dueAt = dueAt,
            notes = FieldValidator.optionalNotes(notes),
            reminderOffsets = if (dueAt == null) emptyList() else reminderOffsets,
            source = source,
            createdAt = now,
            updatedAt = now,
        )
        todoDao.upsert(todo.toEntity())
        undo.record(UndoEntityType.TODO, todo.id, UndoAction.CREATE, undo.encodeTodo(todo))
        return todo
    }

    suspend fun update(
        id: String,
        title: String? = null,
        dueAt: Long? = null,
        clearDue: Boolean = false,
        notes: String? = null,
        reminderOffsets: List<Int>? = null,
        isDone: Boolean? = null,
    ): Todo {
        val old = getOrThrow(id)
        val now = TimeUtils.nowMillis()
        val nextDone = isDone ?: old.isDone
        val next = old.copy(
            title = title?.let { FieldValidator.requireTitle(it) } ?: old.title,
            dueAt = if (clearDue) null else (dueAt ?: old.dueAt),
            notes = notes?.let { FieldValidator.optionalNotes(it) } ?: old.notes,
            reminderOffsets = reminderOffsets?.let { FieldValidator.requireOffsets(it) } ?: old.reminderOffsets,
            isDone = nextDone,
            doneAt = when {
                nextDone && old.doneAt == null -> now
                !nextDone -> null
                else -> old.doneAt
            },
            updatedAt = now,
        )
        undo.record(UndoEntityType.TODO, id, UndoAction.UPDATE, undo.encodeTodo(old))
        todoDao.upsert(next.toEntity())
        return next
    }

    suspend fun complete(id: String): Todo {
        val old = getOrThrow(id)
        if (old.isDone) return old
        val now = TimeUtils.nowMillis()
        val next = old.copy(isDone = true, doneAt = now, updatedAt = now)
        undo.record(UndoEntityType.TODO, id, UndoAction.UPDATE, undo.encodeTodo(old))
        todoDao.upsert(next.toEntity())
        return next
    }

    suspend fun delete(id: String) {
        val todo = getOrThrow(id)
        undo.record(UndoEntityType.TODO, id, UndoAction.DELETE, undo.encodeTodo(todo))
        todoDao.softDelete(id, TimeUtils.nowMillis())
    }

    suspend fun undoById(snapshotId: String): String {
        val snap = undo.getById(snapshotId) ?: throw RiQingError.NotFound("撤销窗口已过期")
        when (snap.entityType) {
            UndoEntityType.TODO -> when (snap.action) {
                UndoAction.CREATE -> {
                    val payload = undo.decodeTodo(snap.payloadJson)
                    todoDao.softDelete(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.DELETE -> {
                    val payload = undo.decodeTodo(snap.payloadJson)
                    todoDao.restore(payload.id, TimeUtils.nowMillis())
                }
                UndoAction.UPDATE -> {
                    val payload = undo.decodeTodo(snap.payloadJson)
                    todoDao.upsert(payload.toEntity())
                }
            }
            else -> throw RiQingError.Validation("该快照不支持在此处撤销")
        }
        undo.consume(snap.id)
        return "已撤销：${snap.action.name} ${snap.entityType}"
    }
}
