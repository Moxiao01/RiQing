package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Source {
    MANUAL, AGENT, IMPORT
}
