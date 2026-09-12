package com.riqing.core.data.agenda

import com.riqing.core.common.TimeUtils
import com.riqing.core.common.WeeksSpecParser
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.Course
import com.riqing.core.model.DayAgenda
import com.riqing.core.model.Event
import com.riqing.core.model.EventException
import com.riqing.core.model.PeriodSlot
import com.riqing.core.model.RepeatFreq
import com.riqing.core.model.Semester
import com.riqing.core.model.Todo
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Agenda 投影：Events + CourseOccurrences + Todos。
 */
object AgendaProjector {

    fun expandEventOnDay(
        event: Event,
        day: LocalDate,
        exceptions: List<EventException>,
    ): AgendaItem.EventOccurrence? {
        val dayStart = TimeUtils.startOfDay(day)
        val dayEnd = TimeUtils.endOfDayExclusive(day)

        if (event.repeatFreq == null) {
            if (event.startAt >= dayEnd || event.startAt < dayStart) return null
            return event.toOccurrence(event.id)
        }

        val occurrenceStart = occurrenceStartOnDay(event, day) ?: return null
        val exception = exceptions.find {
            it.eventId == event.id && it.originalStartAt == occurrenceStart && !it.isDeleted
        }
        if (exception?.isDeletedInstance == true) return null

        val duration = event.endAt - event.startAt
        val start = exception?.overrideStartAt ?: occurrenceStart
        val end = exception?.overrideEndAt ?: (start + duration)
        val title = exception?.overrideTitle ?: event.title
        return AgendaItem.EventOccurrence(
            id = "${event.id}@${occurrenceStart}",
            eventId = event.id,
            title = title,
            startAt = start,
            endAt = end,
            isAllDay = event.isAllDay,
            notes = event.notes,
            isSeries = true,
            source = event.source,
        )
    }

    private fun Event.toOccurrence(id: String): AgendaItem.EventOccurrence =
        AgendaItem.EventOccurrence(
            id = id,
            eventId = id,
            title = title,
            startAt = startAt,
            endAt = endAt,
            isAllDay = isAllDay,
            notes = notes,
            isSeries = false,
            source = source,
        )

    /**
     * 返回该日在重复系列上的「原始」 occurrence start，若该日无实例则 null。
     */
    fun occurrenceStartOnDay(event: Event, day: LocalDate): Long? {
        val freq = event.repeatFreq ?: return null
        val startDate = TimeUtils.localDateOf(event.startAt)
        if (day.isBefore(startDate)) return null

        // repeatCount：以 startDate 为第 1 次
        val count = event.repeatCount
        if (count != null) {
            val index = occurrenceIndex(event, day) ?: return null
            if (index < 0 || index >= count) return null
        }

        event.repeatUntil?.let { until ->
            if (day.isAfter(TimeUtils.localDateOf(until))) return null
        }

        val dayMinutes = TimeUtils.minutesOfDay(event.startAt)
        val matches = when (freq) {
            RepeatFreq.DAILY -> true
            RepeatFreq.WEEKLY -> {
                val weekdays = event.repeatWeekdays
                if (weekdays.isNullOrEmpty()) {
                    day.dayOfWeek == startDate.dayOfWeek
                } else {
                    day.dayOfWeek.value in weekdays
                }
            }
            RepeatFreq.WORKDAYS -> day.dayOfWeek.value in 1..5
        }
        if (!matches) return null
        return TimeUtils.withMinutesOnDay(day, dayMinutes)
    }

    private fun occurrenceIndex(event: Event, day: LocalDate): Int? {
        val startDate = TimeUtils.localDateOf(event.startAt)
        if (day.isBefore(startDate)) return null
        return when (event.repeatFreq) {
            RepeatFreq.DAILY -> ChronoUnit.DAYS.between(startDate, day).toInt()
            RepeatFreq.WEEKLY -> {
                val weekdays = event.repeatWeekdays
                if (weekdays.isNullOrEmpty()) {
                    (ChronoUnit.DAYS.between(startDate, day) / 7).toInt()
                } else {
                    // 自选星期几时按「已发生次数」计数：从 startDate 起统计匹配日
                    var idx = 0
                    var cur = startDate
                    while (cur.isBefore(day)) {
                        if (cur.dayOfWeek.value in weekdays) idx++
                        cur = cur.plusDays(1)
                    }
                    idx
                }
            }
            RepeatFreq.WORKDAYS -> {
                var idx = 0
                var cur = startDate
                while (cur.isBefore(day)) {
                    if (cur.dayOfWeek.value in 1..5) idx++
                    cur = cur.plusDays(1)
                }
                if (day.dayOfWeek.value !in 1..5) null else idx
            }
            null -> null
        }
    }

    fun courseOccurrenceOnDay(
        course: Course,
        day: LocalDate,
        semester: Semester,
        slots: List<PeriodSlot>,
    ): AgendaItem.CourseOccurrence? {
        val weekIndex = TimeUtils.teachingWeekIndex(
            TimeUtils.parseIsoDate(semester.week1Monday),
            day,
        )
        if (weekIndex < 1) return null
        if (day.isAfter(TimeUtils.parseIsoDate(semester.endDate))) return null
        val dayOfWeek = TimeUtils.dayOfWeekMonday1(day)
        if (dayOfWeek !in course.effectiveWeekdays) return null
        if (!WeeksSpecParser.contains(course.weeksSpec, weekIndex)) return null
        if (course.isBiweekly) {
            val odd = weekIndex % 2 == 1
            val wantOdd = course.biweeklyOdd ?: true
            if (odd != wantOdd) return null
        }

        val startSlot = slots.find { it.periodIndex == course.periodStart } ?: return null
        val endSlot = slots.find { it.periodIndex == course.periodEnd } ?: startSlot
        val startAt = TimeUtils.withMinutesOnDay(day, startSlot.startMinutes)
        val endAt = TimeUtils.withMinutesOnDay(day, endSlot.endMinutes)

        return AgendaItem.CourseOccurrence(
            id = "${course.id}@${TimeUtils.formatIsoDate(day)}",
            courseId = course.id,
            semesterId = course.semesterId,
            title = course.name,
            startAt = startAt,
            endAt = endAt,
            location = course.location,
            teacher = course.teacher,
            weekday = dayOfWeek,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            teachingWeek = weekIndex,
            colorToken = course.colorToken,
        )
    }

    fun buildDay(
        day: LocalDate,
        events: List<Event>,
        exceptions: List<EventException>,
        courses: List<Course>,
        semester: Semester?,
        slots: List<PeriodSlot>,
        todos: List<Todo>,
    ): DayAgenda {
        val items = buildList {
            events.forEach { e ->
                expandEventOnDay(e, day, exceptions)?.let { add(it) }
            }
            if (semester != null) {
                courses.forEach { c ->
                    courseOccurrenceOnDay(c, day, semester, slots)?.let { add(it) }
                }
            }
        }.sortedWith(compareBy({ it.startAt }, { it.title }))

        val dayStart = TimeUtils.startOfDay(day)
        val dayEnd = TimeUtils.endOfDayExclusive(day)
        // D2 §5.3：agenda(D) 的待办 = dueAt 落在 D + 无截止（收件箱）；已逾期由今日视图单独分组
        // 已完成待办保留在列表中原地置灰（isDone 由 UI 层区分展示）
        val dayTodos = todos.filter { t ->
            val due = t.dueAt
            due == null || (due >= dayStart && due < dayEnd)
        }

        return DayAgenda(
            date = TimeUtils.formatIsoDate(day),
            items = items,
            todos = dayTodos,
        )
    }
}
