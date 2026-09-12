package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatFreq {
    DAILY, WEEKLY, WORKDAYS
}
