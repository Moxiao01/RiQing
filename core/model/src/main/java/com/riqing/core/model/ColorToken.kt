package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ColorToken {
    C1, C2, C3, C4, C5, C6, C7, C8;

    companion object {
        fun from(raw: String?): ColorToken {
            if (raw.isNullOrBlank()) return C1
            val n = raw.lowercase().removePrefix("c").toIntOrNull() ?: 1
            return entries.getOrElse(n - 1) { C1 }
        }

        fun token(token: ColorToken): String = "c${token.ordinal + 1}"
    }
}
