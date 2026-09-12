package com.riqing.core.model

/**
 * Agenda 合并视图中的一条时间项。
 */
sealed class AgendaItem {
    abstract val id: String
    abstract val title: String
    abstract val startAt: Long
    abstract val endAt: Long

    data class EventOccurrence(
        override val id: String,
        val eventId: String,
        override val title: String,
        override val startAt: Long,
        override val endAt: Long,
        val isAllDay: Boolean,
        val notes: String?,
        val isSeries: Boolean,
        val source: Source,
    ) : AgendaItem()

    data class CourseOccurrence(
        override val id: String,
        val courseId: String,
        val semesterId: String,
        override val title: String,
        override val startAt: Long,
        override val endAt: Long,
        val location: String?,
        val teacher: String?,
        val weekday: Int,
        val periodStart: Int,
        val periodEnd: Int,
        val teachingWeek: Int,
        val colorToken: String,
    ) : AgendaItem()
}

/**
 * 某一日的合并视图。
 */
data class DayAgenda(
    val date: String,
    val items: List<AgendaItem>,
    val todos: List<Todo>,
)
