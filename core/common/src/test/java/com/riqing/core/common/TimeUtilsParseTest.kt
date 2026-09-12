package com.riqing.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimeUtilsParseTest {

    @Test
    fun parseIsoDateOnly() {
        val epoch = TimeUtils.parseFlexibleDateTime("2026-08-12")
        assertNotNull(epoch)
        assertEquals(LocalDate.of(2026, 8, 12), TimeUtils.localDateOf(epoch!!))
    }

    @Test
    fun parseDateAndTimeWithSpace() {
        val epoch = TimeUtils.parseFlexibleDateTime("2026-08-12 15:30")
        assertNotNull(epoch)
        assertEquals(LocalDateTime.of(2026, 8, 12, 15, 30), TimeUtils.localDateTimeOf(epoch!!))
    }

    @Test
    fun parseIsoLocalDateTime() {
        val epoch = TimeUtils.parseFlexibleDateTime("2026-08-12T15:30:00")
        assertNotNull(epoch)
        assertEquals(LocalDateTime.of(2026, 8, 12, 15, 30), TimeUtils.localDateTimeOf(epoch!!))
    }

    @Test
    fun parseEpochPassthrough() {
        assertEquals(123456789L, TimeUtils.parseFlexibleDateTime("123456789"))
    }

    @Test
    fun rejectGarbage() {
        assertNull(TimeUtils.parseFlexibleDateTime(""))
        assertNull(TimeUtils.parseFlexibleDateTime("abc"))
        assertNull(TimeUtils.parseFlexibleDateTime("2026-13-40"))
        assertNull(TimeUtils.parseFlexibleDateTime("2026-08-12 25:00"))
    }
}
