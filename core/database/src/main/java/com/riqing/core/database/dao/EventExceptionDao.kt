package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.EventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventExceptionDao {
    @Query("SELECT * FROM event_exceptions WHERE event_id = :eventId AND is_deleted = 0")
    suspend fun listByEvent(eventId: String): List<EventExceptionEntity>

    @Query("SELECT * FROM event_exceptions WHERE event_id = :eventId AND is_deleted = 0")
    fun observeByEvent(eventId: String): Flow<List<EventExceptionEntity>>

    @Query(
        """
        SELECT * FROM event_exceptions
        WHERE event_id = :eventId AND original_start_at = :originalStartAt AND is_deleted = 0
        LIMIT 1
        """,
    )
    suspend fun find(eventId: String, originalStartAt: Long): EventExceptionEntity?

    @Query("SELECT * FROM event_exceptions WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): EventExceptionEntity?

    @Query("SELECT * FROM event_exceptions WHERE is_deleted = 0")
    suspend fun getAll(): List<EventExceptionEntity>

    @Query("SELECT * FROM event_exceptions WHERE is_deleted = 0")
    fun observeAll(): Flow<List<EventExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventExceptionEntity)

    @Query("UPDATE event_exceptions SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
