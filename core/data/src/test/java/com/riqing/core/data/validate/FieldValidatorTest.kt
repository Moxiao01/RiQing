package com.riqing.core.data.validate

import com.riqing.core.common.TimeUtils
import com.riqing.core.model.RiQingError
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FieldValidatorTest {

    private fun epoch(date: LocalDateTime): Long = TimeUtils.toEpoch(date)

    @Test
    fun repeatUntilSameDayAsStartIsValid() {
        // 8月12日 08:00 开始，repeatUntil=8月12日（00:00）→ 同日应视为有效（D4 repeatUntil 可为 ISO 日期）
        val start = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        val until = epoch(LocalDateTime.of(2026, 8, 12, 0, 0))
        FieldValidator.validateRepeat("DAILY", until, null, start)
    }

    @Test
    fun repeatUntilBeforeStartDayFails() {
        val start = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        val until = epoch(LocalDateTime.of(2026, 8, 11, 0, 0))
        assertThrows(RiQingError.Validation::class.java) {
            FieldValidator.validateRepeat("DAILY", until, null, start)
        }
    }

    @Test
    fun repeatWithoutUntilOrCountFails() {
        val start = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        assertThrows(RiQingError.Validation::class.java) {
            FieldValidator.validateRepeat("WEEKLY", null, null, start)
        }
    }

    @Test
    fun repeatCountAloneIsValid() {
        val start = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        FieldValidator.validateRepeat("WORKDAYS", null, 10, start)
    }

    @Test
    fun eventEndMustBeAfterStart() {
        val start = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        val end = epoch(LocalDateTime.of(2026, 8, 12, 8, 0))
        assertThrows(RiQingError.Validation::class.java) {
            FieldValidator.validateEventTime(start, end, isAllDay = false)
        }
    }

    @Test
    fun weeksSpecRange() {
        // 学期最大周 16，超出应失败
        assertThrows(RiQingError.Validation::class.java) {
            FieldValidator.requireWeeksSpec("1-17", maxWeek = 16)
        }
        val normalized = FieldValidator.requireWeeksSpec("1,2,3,6", maxWeek = 16)
        assert(normalized == "1-3,6")
    }
}
