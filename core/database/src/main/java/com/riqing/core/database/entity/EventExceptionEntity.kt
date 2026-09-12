package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_exceptions",
    indices = [
        Index(value = ["event_id", "original_start_at"], unique = true),
        Index(value = ["is_deleted"]),
    ],
)
data class EventExceptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "original_start_at") val originalStartAt: Long,
    @ColumnInfo(name = "is_deleted_instance") val isDeletedInstance: Boolean = false,
    @ColumnInfo(name = "override_start_at") val overrideStartAt: Long? = null,
    @ColumnInfo(name = "override_end_at") val overrideEndAt: Long? = null,
    @ColumnInfo(name = "override_title") val overrideTitle: String? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
