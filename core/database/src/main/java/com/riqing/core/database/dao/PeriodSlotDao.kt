package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.PeriodSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodSlotDao {
    @Query("SELECT * FROM period_slots WHERE is_deleted = 0 ORDER BY semester_id, period_index")
    fun observeAll(): Flow<List<PeriodSlotEntity>>

    @Query("SELECT * FROM period_slots WHERE semester_id = :semesterId AND is_deleted = 0 ORDER BY period_index")
    suspend fun listBySemester(semesterId: String): List<PeriodSlotEntity>

    @Query("SELECT * FROM period_slots WHERE semester_id = :semesterId AND is_deleted = 0 ORDER BY period_index")
    fun observeBySemester(semesterId: String): Flow<List<PeriodSlotEntity>>

    @Query(
        """
        SELECT * FROM period_slots
        WHERE semester_id = :semesterId AND period_index = :index AND is_deleted = 0
        LIMIT 1
        """,
    )
    suspend fun getByIndex(semesterId: String, index: Int): PeriodSlotEntity?

    @Query("SELECT * FROM period_slots WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): PeriodSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PeriodSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PeriodSlotEntity>)

    @Query("UPDATE period_slots SET is_deleted = 1, updated_at = :now WHERE semester_id = :semesterId")
    suspend fun softDeleteBySemester(semesterId: String, now: Long)

    @Query("UPDATE period_slots SET is_deleted = 0, updated_at = :now WHERE semester_id = :semesterId")
    suspend fun restoreBySemester(semesterId: String, now: Long)

    @Query("DELETE FROM period_slots WHERE semester_id = :semesterId")
    suspend fun hardDeleteBySemester(semesterId: String)
}
