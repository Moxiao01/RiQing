package com.riqing.core.common

/**
 * 解析 weeksSpec：`1-16`、`1,3,5-8`。
 * 不支持 `2-16/2`（单双周用 isBiweekly）。
 */
object WeeksSpecParser {
    fun parse(spec: String, maxWeek: Int = 52): Result<Set<Int>> = runCatching {
        require(spec.isNotBlank()) { "weeksSpec 不能为空" }
        val weeks = linkedSetOf<Int>()
        val parts = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "weeksSpec 非法" }
        for (part in parts) {
            if ('-' in part) {
                val (a, b) = part.split('-', limit = 2).map { it.trim() }
                val start = a.toIntOrNull() ?: error("weeksSpec 非法: $part")
                val end = b.toIntOrNull() ?: error("weeksSpec 非法: $part")
                require(start >= 1 && end >= start) { "weeksSpec 区间非法: $part" }
                for (w in start..end) {
                    require(w in 1..maxWeek) { "周次超出范围: $w" }
                    weeks.add(w)
                }
            } else {
                val w = part.toIntOrNull() ?: error("weeksSpec 非法: $part")
                require(w in 1..maxWeek) { "周次超出范围: $w" }
                weeks.add(w)
            }
        }
        weeks
    }

    fun format(weeks: Collection<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.toSortedSet().toList()
        val sb = StringBuilder()
        var i = 0
        while (i < sorted.size) {
            val start = sorted[i]
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            val end = sorted[j]
            if (sb.isNotEmpty()) sb.append(',')
            if (i == j) sb.append(start) else sb.append("$start-$end")
            i = j + 1
        }
        return sb.toString()
    }

    fun contains(spec: String, week: Int): Boolean =
        parse(spec).getOrNull()?.contains(week) == true
}
