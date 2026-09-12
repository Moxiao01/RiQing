package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Semester(
    val id: String,
    val name: String,
    val week1Monday: String,
    val endDate: String,
    val isCurrent: Boolean,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
