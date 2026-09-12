package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["is_deleted", "semester_id", "weekday"]),
        Index(value = ["is_deleted", "name"]),
    ],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "semester_id") val semesterId: String,
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int,
    @ColumnInfo(name = "weekdays_json") val weekdaysJson: String? = null,
    @ColumnInfo(name = "period_start") val periodStart: Int,
    @ColumnInfo(name = "period_end") val periodEnd: Int,
    @ColumnInfo(name = "weeks_spec") val weeksSpec: String,
    @ColumnInfo(name = "is_biweekly") val isBiweekly: Boolean = false,
    @ColumnInfo(name = "biweekly_odd") val biweeklyOdd: Boolean? = null,
    @ColumnInfo(name = "reminder_offsets_json") val reminderOffsetsJson: String = "[]",
    @ColumnInfo(name = "color_token") val colorToken: String = "c1",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val source: String = "MANUAL",
)
