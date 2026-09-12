package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val isAllDay: Boolean = false,
    val notes: String? = null,
    val reminderOffsets: List<Int> = emptyList(),
    val repeatFreq: RepeatFreq? = null,
    val repeatUntil: Long? = null,
    val repeatCount: Int? = null,
    /** WEEKLY 专用：生效星期几（ISO：周一=1 … 周日=7）；null/空 = 与 startAt 同一星期几。 */
    val repeatWeekdays: List<Int>? = null,
    val source: Source = Source.MANUAL,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
