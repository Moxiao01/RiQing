package com.riqing.core.data.agenda

import com.riqing.core.model.AgendaItem
import com.riqing.core.model.Course
import com.riqing.core.model.Event
import com.riqing.core.model.PeriodSlot
import com.riqing.core.model.RepeatFreq
import com.riqing.core.model.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class AgendaProjectorTest {

    private val week1 = LocalDate.of(2026, 3, 2) // Monday

    private fun semester() = Semester(
        id = "s1",
        name = "2026 春",
        week1Monday = "2026-03-02",
        endDate = "2026-06-30",
        isCurrent = true,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun slots() = listOf(
        PeriodSlot("p1", "s1", 1, 8 * 60, 9 * 60 + 40, createdAt = 0, updatedAt = 0),
        PeriodSlot("p2", "s1", 2, 10 * 60, 11 * 60 + 40, createdAt = 0, updatedAt = 0),
    )

    @Test
    fun nonRecurringEventOnDay() {
        val start = com.riqing.core.common.TimeUtils.toEpoch(week1.atTime(15, 0))
        val event = Event(
            id = "e1",
            title = "开会",
            startAt = start,
            endAt = start + 3600_000,
            createdAt = 0,
            updatedAt = 0,
        )
        val occ = AgendaProjector.expandEventOnDay(event, week1, emptyList())
        assertNotNull(occ)
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(1), emptyList()))
    }

    @Test
    fun weeklyRepeatsSameWeekday() {
        val start = com.riqing.core.common.TimeUtils.toEpoch(week1.atTime(10, 0))
        val event = Event(
            id = "e2",
            title = "每周",
            startAt = start,
            endAt = start + 3600_000,
            repeatFreq = RepeatFreq.WEEKLY,
            repeatCount = 4,
            createdAt = 0,
            updatedAt = 0,
        )
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1, emptyList()))
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(7), emptyList()))
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(1), emptyList()))
    }

    @Test
    fun weeklyEmptyWeekdaysFallsBackToStartWeekday() {
        val start = com.riqing.core.common.TimeUtils.toEpoch(week1.atTime(10, 0))
        val event = Event(
            id = "e2b",
            title = "每周（未自选星期几）",
            startAt = start,
            endAt = start + 3600_000,
            repeatFreq = RepeatFreq.WEEKLY,
            repeatWeekdays = emptyList(),
            createdAt = 0,
            updatedAt = 0,
        )
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1, emptyList()))
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(2), emptyList())) // 周三
    }

    @Test
    fun weeklyRepeatsOnSelectedWeekdays() {
        // 2026-03-02 是周一
        val start = com.riqing.core.common.TimeUtils.toEpoch(week1.atTime(10, 0))
        val event = Event(
            id = "e4",
            title = "每周一三",
            startAt = start,
            endAt = start + 3600_000,
            repeatFreq = RepeatFreq.WEEKLY,
            repeatWeekdays = listOf(1, 3), // 周一、周三
            repeatCount = 3,
            createdAt = 0,
            updatedAt = 0,
        )
        // 第1次：开始日周一；第2次：周三；第3次：下周一；第4次：下周三（超出次数，不展开）
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1, emptyList()))
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(2), emptyList()))
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(1), emptyList()))
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(7), emptyList()))
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(9), emptyList()))
    }

    @Test
    fun workdaysSkipsWeekend() {
        // 2026-03-07 is Saturday
        val start = com.riqing.core.common.TimeUtils.toEpoch(week1.atTime(9, 0))
        val event = Event(
            id = "e3",
            title = "工作日",
            startAt = start,
            endAt = start + 3600_000,
            repeatFreq = RepeatFreq.WORKDAYS,
            repeatUntil = com.riqing.core.common.TimeUtils.toEpoch(week1.plusDays(14).atTime(23, 59)),
            createdAt = 0,
            updatedAt = 0,
        )
        assertNotNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(4), emptyList())) // Fri
        assertNull(AgendaProjector.expandEventOnDay(event, week1.plusDays(5), emptyList())) // Sat
    }

    @Test
    fun courseProjectsByWeekAndWeekday() {
        val course = Course(
            id = "c1",
            semesterId = "s1",
            name = "高数",
            weekday = 3, // Wednesday
            periodStart = 1,
            periodEnd = 1,
            weeksSpec = "1-16",
            createdAt = 0,
            updatedAt = 0,
        )
        val wed = week1.plusDays(2)
        val occ = AgendaProjector.courseOccurrenceOnDay(course, wed, semester(), slots())
        assertNotNull(occ)
        assertEquals(1, occ!!.teachingWeek)
        assertNull(
            AgendaProjector.courseOccurrenceOnDay(course, week1, semester(), slots()),
        ) // Monday
    }

    @Test
    fun courseWithMultipleWeekdaysProjectsOnEachDay() {
        // 一周两节的课：周一、周四（weekdays 已知，weekday 仅作主键日）
        val course = Course(
            id = "c1b",
            semesterId = "s1",
            name = "英语",
            weekday = 1,
            weekdays = listOf(1, 4),
            periodStart = 1,
            periodEnd = 2,
            weeksSpec = "1-16",
            createdAt = 0,
            updatedAt = 0,
        )
        val mon = AgendaProjector.courseOccurrenceOnDay(course, week1, semester(), slots())
        val thu = AgendaProjector.courseOccurrenceOnDay(course, week1.plusDays(3), semester(), slots())
        assertNotNull(mon)
        assertNotNull(thu)
        assertEquals(1, mon!!.weekday)
        assertEquals(4, thu!!.weekday)
        assertNull(AgendaProjector.courseOccurrenceOnDay(course, week1.plusDays(1), semester(), slots()))
    }

    @Test
    fun biweeklyOddOnly() {
        val course = Course(
            id = "c2",
            semesterId = "s1",
            name = "单周课",
            weekday = 1,
            periodStart = 1,
            periodEnd = 1,
            weeksSpec = "1-16",
            isBiweekly = true,
            biweeklyOdd = true,
            createdAt = 0,
            updatedAt = 0,
        )
        assertNotNull(AgendaProjector.courseOccurrenceOnDay(course, week1, semester(), slots()))
        assertNull(AgendaProjector.courseOccurrenceOnDay(course, week1.plusWeeks(1), semester(), slots()))
    }
}
