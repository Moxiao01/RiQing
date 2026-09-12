package com.riqing.core.data.validate

import com.riqing.core.common.TimeUtils
import com.riqing.core.common.WeeksSpecParser
import com.riqing.core.model.RiQingError

/**
 * UI 表单与 Agent ToolValidator 共用的校验规则（对齐 D3）。
 */
object FieldValidator {
    fun requireTitle(value: String?, field: String = "title", max: Int = 80): String {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) throw RiQingError.Validation("标题不能为空", field)
        if (v.length > max) throw RiQingError.Validation("标题不能超过 $max 字", field)
        return v
    }

    fun requireCourseName(value: String?): String {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) throw RiQingError.Validation("课程名称不能为空", "name")
        if (v.length > 60) throw RiQingError.Validation("课程名称不能超过 60 字", "name")
        return v
    }

    fun optionalNotes(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.length > 2000) throw RiQingError.Validation("备注不能超过 2000 字", "notes")
        return value
    }

    fun optionalShort(value: String?, max: Int, field: String): String? {
        if (value.isNullOrBlank()) return null
        if (value.length > max) throw RiQingError.Validation("$field 不能超过 $max 字", field)
        return value.trim()
    }

    fun requireWeekday(weekday: Int): Int {
        if (weekday !in 1..7) throw RiQingError.Validation("星期必须为 1-7（1=周一）", "weekday")
        return weekday
    }

    /** WEEKLY 重复/课程多星期的星期几集合：校验后去重排序。 */
    fun requireWeekdays(weekdays: List<Int>?, field: String = "repeatWeekdays"): List<Int>? {
        if (weekdays == null) return null
        weekdays.forEach {
            if (it !in 1..7) throw RiQingError.Validation("星期必须为 1-7（1=周一）", field)
        }
        return weekdays.distinct().sorted()
    }

    fun requirePeriods(start: Int, end: Int): Pair<Int, Int> {
        if (start < 1 || start > 12) throw RiQingError.Validation("起始节次必须为 1-12", "periodStart")
        if (end < start || end > 12) throw RiQingError.Validation("结束节次必须 ≥ 起始且 ≤12", "periodEnd")
        return start to end
    }

    fun requireWeeksSpec(spec: String, maxWeek: Int = 52): String {
        val parsed = WeeksSpecParser.parse(spec.trim(), maxWeek)
        if (parsed.isFailure) {
            throw RiQingError.Validation(parsed.exceptionOrNull()?.message ?: "weeksSpec 非法", "weeksSpec")
        }
        return WeeksSpecParser.format(parsed.getOrThrow())
    }

    fun requireOffsets(offsets: List<Int>?, maxCount: Int = 3): List<Int> {
        if (offsets.isNullOrEmpty()) return emptyList()
        if (offsets.size > maxCount) throw RiQingError.Validation("最多 $maxCount 个提醒", "reminderOffsets")
        offsets.forEach {
            if (it !in 0..10080) {
                throw RiQingError.Validation("提醒偏移需在 0-10080 分钟", "reminderOffsets")
            }
        }
        return offsets
    }

    fun validateEventTime(startAt: Long, endAt: Long, isAllDay: Boolean) {
        if (!isAllDay && endAt <= startAt) {
            throw RiQingError.Validation("结束时间必须晚于开始时间", "endAt")
        }
        if (isAllDay && endAt < startAt) {
            throw RiQingError.Validation("结束时间不能早于开始", "endAt")
        }
    }

    fun validateRepeat(
        repeatFreq: String?,
        repeatUntil: Long?,
        repeatCount: Int?,
        startAt: Long,
    ) {
        if (repeatFreq.isNullOrBlank()) return
        // repeatUntil 允许为 ISO 日期（当天 00:00），与 startAt 按「日」比较，避免同日误报
        val untilOk = repeatUntil != null &&
            repeatUntil >= TimeUtils.startOfDay(TimeUtils.localDateOf(startAt))
        val countOk = repeatCount != null && repeatCount in 1..365
        if (!untilOk && !countOk) {
            throw RiQingError.Validation("重复规则必须提供结束日或次数", "repeatUntil")
        }
    }

    fun requireWeek1Monday(date: String): String {
        // ISO date string; Monday check done by caller via TimeUtils
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) {
            throw RiQingError.Validation("日期格式应为 YYYY-MM-DD", "week1Monday")
        }
        return date
    }
}
