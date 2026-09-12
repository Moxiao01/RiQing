package com.riqing.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeksSpecParserTest {
    @Test
    fun parseRange() {
        val set = WeeksSpecParser.parse("1-5").getOrThrow()
        assertEquals(setOf(1, 2, 3, 4, 5), set)
    }

    @Test
    fun parseMixed() {
        val set = WeeksSpecParser.parse("1,3,5-8").getOrThrow()
        assertEquals(setOf(1, 3, 5, 6, 7, 8), set)
    }

    @Test
    fun contains() {
        assertTrue(WeeksSpecParser.contains("1-16", 10))
        assertFalse(WeeksSpecParser.contains("1-16", 17))
        assertTrue(WeeksSpecParser.contains("1,3,5", 3))
    }

    @Test
    fun format() {
        assertEquals("1-3,5", WeeksSpecParser.format(listOf(1, 2, 3, 5)))
    }

    @Test
    fun invalid() {
        assertTrue(WeeksSpecParser.parse("").isFailure)
        assertTrue(WeeksSpecParser.parse("0-3").isFailure)
        assertTrue(WeeksSpecParser.parse("abc").isFailure)
    }
}

class TimeUtilsTeachingWeekTest {
    @Test
    fun week1IsOne() {
        val week1 = LocalDate.of(2026, 3, 2) // Monday
        assertEquals(1, TimeUtils.teachingWeekIndex(week1, week1))
        assertEquals(1, TimeUtils.teachingWeekIndex(week1, week1.plusDays(6)))
        assertEquals(2, TimeUtils.teachingWeekIndex(week1, week1.plusDays(7)))
        assertEquals(0, TimeUtils.teachingWeekIndex(week1, week1.minusDays(1)))
    }

    @Test
    fun maxWeek() {
        val week1 = LocalDate.of(2026, 3, 2)
        assertEquals(16, TimeUtils.maxTeachingWeek(week1, week1.plusDays(16 * 7 - 1)))
    }
}
