package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): TodoEntity?

    @Query("SELECT * FROM todos WHERE is_deleted = 0 ORDER BY is_done, due_at IS NULL, due_at")
    fun observeActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE is_deleted = 0 ORDER BY is_done, due_at IS NULL, due_at")
    suspend fun listActive(): List<TodoEntity>

    // 已完成待办不再从列表中过滤掉（原地置灰展示），仅软删除的不可见
    @Query(
        """
        SELECT * FROM todos
        WHERE is_deleted = 0
          AND (
            due_at IS NULL
            OR (due_at >= :dayStart AND due_at < :dayEndExclusive)
          )
        ORDER BY due_at IS NULL, due_at
        """,
    )
    suspend fun listForDayAgenda(dayStart: Long, dayEndExclusive: Long): List<TodoEntity>

    @Query(
        """
        SELECT * FROM todos
        WHERE is_deleted = 0 AND due_at IS NOT NULL AND due_at < :now
        ORDER BY due_at
        """,
    )
    suspend fun listOverdue(now: Long): List<TodoEntity>

    @Query(
        """
        SELECT * FROM todos
        WHERE is_deleted = 0 AND due_at IS NOT NULL AND due_at >= :after
        ORDER BY due_at
        """,
    )
    suspend fun listUpcoming(after: Long): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE is_deleted = 0 AND title LIKE '%' || :keyword || '%' ORDER BY due_at")
    suspend fun search(keyword: String): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE is_deleted = 0")
    suspend fun getAll(): List<TodoEntity>

    @Query("SELECT * FROM todos")
    suspend fun getAllIncludingDeleted(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TodoEntity)

    @Query("UPDATE todos SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE todos SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}
