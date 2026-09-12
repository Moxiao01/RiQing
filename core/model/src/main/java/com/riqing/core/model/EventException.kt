package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventException(
    val id: String,
    val eventId: String,
    val originalStartAt: Long,
    val isDeletedInstance: Boolean = false,
    val overrideStartAt: Long? = null,
    val overrideEndAt: Long? = null,
    val overrideTitle: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
