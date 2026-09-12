package com.riqing.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.riqing.core.database.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE id = :id AND is_deleted = 0")
    suspend fun getById(id: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE semester_id = :semesterId AND is_deleted = 0 ORDER BY weekday, period_start")
    suspend fun listBySemester(semesterId: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE semester_id = :semesterId AND is_deleted = 0 ORDER BY weekday, period_start")
    fun observeBySemester(semesterId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE is_deleted = 0 ORDER BY weekday, period_start")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses")
    suspend fun getAllIncludingDeleted(): List<CourseEntity>

    @Query(
        """
        SELECT * FROM courses
        WHERE is_deleted = 0 AND name LIKE '%' || :keyword || '%'
        ORDER BY weekday, period_start
        """,
    )
    suspend fun search(keyword: String): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CourseEntity)

    @Query("UPDATE courses SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE courses SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("UPDATE courses SET is_deleted = 1, updated_at = :now WHERE semester_id = :semesterId")
    suspend fun softDeleteBySemester(semesterId: String, now: Long)

    @Query("UPDATE courses SET is_deleted = 0, updated_at = :now WHERE semester_id = :semesterId")
    suspend fun restoreBySemester(semesterId: String, now: Long)
}
