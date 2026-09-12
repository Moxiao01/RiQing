package com.riqing.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

object TimeUtils {
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoDateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val displayDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val displayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日")
    private val displayWeekday: DateTimeFormatter =
        DateTimeFormatter.ofPattern("E")
    private val displayTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm")

    fun newId(): String = UUID.randomUUID().toString()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun localDateOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    fun localDateTimeOf(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDateTime()

    fun toEpoch(localDateTime: LocalDateTime): Long =
        localDateTime.atZone(zone()).toInstant().toEpochMilli()

    fun toEpoch(localDate: LocalDate): Long =
        localDate.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun parseIsoDateTime(raw: String): LocalDateTime =
        LocalDateTime.parse(raw.trim(), isoDateTime)

    fun parseIsoDate(raw: String): LocalDate =
        LocalDate.parse(raw.trim(), isoDate)

    fun formatIsoDate(date: LocalDate): String = date.format(isoDate)

    fun formatIsoDateTime(dateTime: LocalDateTime): String = dateTime.format(isoDateTime)

    /** 表单展示用：`yyyy-MM-dd HH:mm`（本地时区）。 */
    fun formatDateTimeText(epochMillis: Long): String =
        localDateTimeOf(epochMillis).format(displayDateTime)

    /** 表单展示用：`yyyy-MM-dd`（本地时区）。 */
    fun formatDateText(epochMillis: Long): String = formatIsoDate(localDateOf(epochMillis))

    /** 表单展示用：`HH:mm`（本地时区）。 */
    fun formatTimeText(epochMillis: Long): String =
        localDateTimeOf(epochMillis).format(displayTime)

    /**
     * 解析表单输入的时间：兼容 `yyyy-MM-dd`、`yyyy-MM-dd HH:mm`、ISO `yyyy-MM-ddTHH:mm[:ss]`
     * 与 epoch 毫秒。日期按本地时区解释；无法解析返回 null。
     */
    fun parseFlexibleDateTime(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { return it }
        runCatching { return toEpoch(LocalDateTime.parse(t)) }
        runCatching { return toEpoch(LocalDate.parse(t)) }
        val parts = t.split(' ', 'T')
        if (parts.size == 2) {
            val date = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return null
            val hm = parts[1].trim().split(':')
            val h = hm.getOrNull(0)?.toIntOrNull() ?: return null
            val m = hm.getOrNull(1)?.toIntOrNull() ?: 0
            if (h !in 0..23 || m !in 0..59) return null
            return toEpoch(date.atTime(h, m))
        }
        return null
    }

    fun formatAbs(epochMillis: Long): String =
        localDateTimeOf(epochMillis).format(displayDateTime)

    fun formatDayLabel(date: LocalDate): String = date.format(displayDate)

    fun formatWeekday(date: LocalDate): String = date.format(displayWeekday)

    fun startOfDay(date: LocalDate): Long = toEpoch(date)

    fun endOfDayExclusive(date: LocalDate): Long = toEpoch(date.plusDays(1))

    fun isSameDay(a: Long, b: Long): Boolean = localDateOf(a) == localDateOf(b)

    fun dayOfWeekMonday1(date: LocalDate): Int {
        // ISO: MONDAY=1 ... SUNDAY=7
        return date.dayOfWeek.value
    }

    fun mondayOfWeek(date: LocalDate): LocalDate {
        val dow = date.dayOfWeek
        val offset = when (dow) {
            DayOfWeek.MONDAY -> 0L
            else -> 1L - dow.value.toLong()
        }
        return date.plusDays(offset)
    }

    /**
     * 教学周：week1Monday 为第 1 周周一；返回 0 表示未开学。
     */
    fun teachingWeekIndex(week1Monday: LocalDate, date: LocalDate): Int {
        val thisMonday = mondayOfWeek(date)
        val days = ChronoUnit.DAYS.between(week1Monday, thisMonday)
        if (days < 0 || days % 7 != 0L) {
            // 非对齐（week1Monday 本身应是周一，此处兜底）
            val raw = ChronoUnit.DAYS.between(week1Monday, date)
            if (raw < 0) return 0
            return (raw / 7 + 1).toInt()
        }
        return (days / 7 + 1).toInt()
    }

    fun maxTeachingWeek(week1Monday: LocalDate, endDate: LocalDate): Int {
        if (endDate.isBefore(week1Monday)) return 0
        val days = ChronoUnit.DAYS.between(week1Monday, endDate)
        return (days / 7 + 1).toInt()
    }

    fun minutesOfDay(epochMillis: Long): Int {
        val t = localDateTimeOf(epochMillis)
        return t.hour * 60 + t.minute
    }

    fun withMinutesOnDay(date: LocalDate, minutes: Int): Long {
        val h = minutes / 60
        val m = minutes % 60
        return toEpoch(date.atTime(h, m))
    }

    fun weekdayChinese(weekday: Int): String = when (weekday) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> "?"
    }
}
