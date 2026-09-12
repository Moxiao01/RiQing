package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.UndoSnapshotEntity

@Dao
interface UndoSnapshotDao {
    @Query("SELECT * FROM undo_snapshots WHERE expires_at > :now ORDER BY created_at DESC LIMIT 1")
    suspend fun latestActive(now: Long): UndoSnapshotEntity?

    @Query("SELECT * FROM undo_snapshots WHERE expires_at > :now ORDER BY created_at DESC LIMIT :limit")
    suspend fun listActive(now: Long, limit: Int = 20): List<UndoSnapshotEntity>

    @Query("SELECT * FROM undo_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UndoSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UndoSnapshotEntity)

    @Query("DELETE FROM undo_snapshots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM undo_snapshots WHERE expires_at <= :now")
    suspend fun purgeExpired(now: Long)
}
