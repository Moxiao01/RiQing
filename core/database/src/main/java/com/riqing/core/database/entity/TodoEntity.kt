package com.riqing.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["is_deleted", "is_done", "due_at"]),
        Index(value = ["is_deleted", "title"]),
    ],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    @ColumnInfo(name = "reminder_offsets_json") val reminderOffsetsJson: String = "[]",
    val source: String = "MANUAL",
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
