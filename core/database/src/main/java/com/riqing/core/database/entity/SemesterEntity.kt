package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "semesters",
    indices = [
        Index(value = ["is_deleted", "is_current"]),
    ],
)
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "week1_monday") val week1Monday: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "is_current") val isCurrent: Boolean = false,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
