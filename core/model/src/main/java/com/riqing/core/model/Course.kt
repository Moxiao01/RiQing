package com.riqing.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val semesterId: String,
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val weekday: Int,
    /** 一周多节的课的全部星期（升序去重）；空 = 旧数据，仅 weekday。 */
    val weekdays: List<Int> = emptyList(),
    val periodStart: Int,
    val periodEnd: Int,
    val weeksSpec: String,
    val isBiweekly: Boolean = false,
    val biweeklyOdd: Boolean? = null,
    val reminderOffsets: List<Int> = listOf(10),
    val colorToken: String = "c1",
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source = Source.MANUAL,
) {
    /** 实际上课的全部星期（weekdays 为空 = 旧数据，回退到单 weekday）。 */
    val effectiveWeekdays: List<Int>
        get() = weekdays.ifEmpty { listOf(weekday) }
}
