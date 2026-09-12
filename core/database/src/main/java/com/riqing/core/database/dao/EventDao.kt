package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): EventEntity?

    @Query(
        """
        SELECT * FROM events
        WHERE is_deleted = 0
          AND start_at < :endExclusive
          AND (
            repeat_freq IS NULL AND start_at >= :startInclusive
            OR repeat_freq IS NOT NULL AND (
              (repeat_until IS NULL OR repeat_until >= :startInclusive)
              AND start_at < :endExclusive
            )
          )
        ORDER BY start_at
        """,
    )
    suspend fun listActiveInRange(startInclusive: Long, endExclusive: Long): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE is_deleted = 0
          AND start_at < :endExclusive
          AND (
            repeat_freq IS NULL AND start_at >= :startInclusive
            OR repeat_freq IS NOT NULL AND (
              (repeat_until IS NULL OR repeat_until >= :startInclusive)
              AND start_at < :endExclusive
            )
          )
        ORDER BY start_at
        """,
    )
    fun observeActiveInRange(startInclusive: Long, endExclusive: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE is_deleted = 0 AND title LIKE '%' || :keyword || '%' ORDER BY start_at")
    suspend fun searchByTitle(keyword: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE is_deleted = 0 AND (notes LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%') ORDER BY start_at")
    suspend fun search(keyword: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE is_deleted = 0 AND start_at >= :from ORDER BY start_at LIMIT :limit")
    suspend fun listUpcoming(from: Long, limit: Int = 200): List<EventEntity>

    @Query("SELECT * FROM events WHERE is_deleted = 0")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun getAllIncludingDeleted(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EventEntity)

    @Query("UPDATE events SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE events SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}
