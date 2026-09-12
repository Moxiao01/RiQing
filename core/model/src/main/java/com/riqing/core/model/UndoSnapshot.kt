package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UndoSnapshot(
    val id: String,
    val entityType: UndoEntityType,
    val entityId: String,
    val action: UndoAction,
    val payloadJson: String,
    val createdAt: Long,
    val expiresAt: Long,
)
