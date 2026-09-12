package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val id: String,
    val title: String,
    val dueAt: Long? = null,
    val notes: String? = null,
    val isDone: Boolean = false,
    val doneAt: Long? = null,
    val reminderOffsets: List<Int> = emptyList(),
    val source: Source = Source.MANUAL,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
