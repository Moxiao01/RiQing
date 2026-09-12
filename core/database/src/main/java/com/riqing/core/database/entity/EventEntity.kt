package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["is_deleted", "start_at"]),
        Index(value = ["is_deleted", "title"]),
    ],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    @ColumnInfo(name = "is_all_day") val isAllDay: Boolean = false,
    val notes: String? = null,
    @ColumnInfo(name = "reminder_offsets_json") val reminderOffsetsJson: String = "[]",
    @ColumnInfo(name = "repeat_freq") val repeatFreq: String? = null,
    @ColumnInfo(name = "repeat_until") val repeatUntil: Long? = null,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int? = null,
    @ColumnInfo(name = "repeat_weekdays_json") val repeatWeekdaysJson: String? = null,
    val source: String = "MANUAL",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
