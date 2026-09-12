package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PeriodSlot(
    val id: String,
    val semesterId: String,
    val periodIndex: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
