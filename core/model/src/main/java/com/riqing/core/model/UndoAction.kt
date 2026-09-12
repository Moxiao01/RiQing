package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class UndoAction {
    CREATE, UPDATE, DELETE
}
