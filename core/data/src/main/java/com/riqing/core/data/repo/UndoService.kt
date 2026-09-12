package com.riqing.core.data.repo

import com.riqing.core.model.UndoEntityType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一撤销入口：按快照类型路由到对应 Repository 的 undoById（D2 §6.2 / D4 §4，撤销不回模型）。
 */
@Singleton
class UndoService @Inject constructor(
    private val undo: UndoRepository,
    private val events: EventRepository,
    private val todos: TodoRepository,
    private val courses: CourseRepository,
) {
    suspend fun latest(): String? = undo.latestActive()?.let { undoById(it.id) }

    suspend fun undoById(snapshotId: String): String {
        val snap = undo.getById(snapshotId)
            ?: throw com.riqing.core.model.RiQingError.NotFound("撤销窗口已过期")
        return when (snap.entityType) {
            UndoEntityType.EVENT, UndoEntityType.EVENT_EXCEPTION -> events.undoById(snapshotId)
            UndoEntityType.TODO -> todos.undoById(snapshotId)
            UndoEntityType.COURSE, UndoEntityType.SEMESTER -> courses.undoById(snapshotId)
            // period_slot 快照未在写路径产生；出现时仅消费，避免占用撤销窗口
            UndoEntityType.PERIOD_SLOT -> "该操作无需回滚"
        }
    }
}
