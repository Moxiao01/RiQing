package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class UndoEntityType {
    EVENT, TODO, COURSE, SEMESTER, PERIOD_SLOT, EVENT_EXCEPTION
}
