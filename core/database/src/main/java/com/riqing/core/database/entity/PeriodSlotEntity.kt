package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "period_slots",
    indices = [
        Index(value = ["semester_id", "period_index"], unique = true),
        Index(value = ["semester_id", "is_deleted"]),
    ],
)
data class PeriodSlotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "semester_id") val semesterId: String,
    @ColumnInfo(name = "period_index") val periodIndex: Int,
    @ColumnInfo(name = "start_minutes") val startMinutes: Int,
    @ColumnInfo(name = "end_minutes") val endMinutes: Int,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
