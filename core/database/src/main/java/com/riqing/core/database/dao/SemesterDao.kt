package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.riqing.core.database.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Query("SELECT * FROM semesters WHERE is_deleted = 0 AND is_current = 1 LIMIT 1")
    suspend fun getCurrent(): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE is_deleted = 0 AND is_current = 1 LIMIT 1")
    fun observeCurrent(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE is_deleted = 0 ORDER BY is_current DESC, week1_monday DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE is_deleted = 0 ORDER BY is_current DESC, week1_monday DESC")
    suspend fun getAll(): List<SemesterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SemesterEntity)

    @Update
    suspend fun update(entity: SemesterEntity)

    @Query("UPDATE semesters SET is_current = 0 WHERE is_deleted = 0")
    suspend fun clearCurrent()

    @Query("UPDATE semesters SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE semesters SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}
