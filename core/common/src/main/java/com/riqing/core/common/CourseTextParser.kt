package com.riqing.core.common

/**
 * 离线（规则式）课表文本解析，无需联网或大模型。
 *
 * 支持一行一门课（一次课）的常见教务系统文本，字段位置不限：
 *   高等数学A(1) 星期一 1-2节 1-16周 教1-201 张伟
 *   数据结构实验 星期三 3-4节 2-15周(双周) 实验楼B402 赵敏
 *
 * - 星期：周一 / 星期一 / 礼拜一，一行可写多个（星期一、星期三）。
 * - 节次：1-2节 / 第1-2节 / 3节。
 * - 周次：1-16周 / 第1-16周 / 1,3,5周 / 1-8周,10-16周；可带（单周）（双周）。
 * - 剩余词按启发式识别：教室含「楼/馆/场/室」等或形如「数字-数字」，纯汉字 2-4 字的第二个词识别为教师。
 * - 周次与节次相同、其余字段一致的行会合并为一周多节课（weekdays 取并集）。
 * - 未标注周次时 weeksSpec 返回空串，由调用方按整学期补齐。
 */
object CourseTextParser {

    data class ParsedSession(
        val name: String,
        val teacher: String? = null,
        val location: String? = null,
        val weekdays: List<Int>,
        val periodStart: Int,
        val periodEnd: Int,
        val weeksSpec: String,
        val isBiweekly: Boolean = false,
        val biweeklyOdd: Boolean? = null,
    )

    data class ParseResult(
        val sessions: List<ParsedSession>,
        val failedLines: List<String>,
    )

    private val weekdayRegex = Regex("(?:星期|礼拜|周)\\s*([一二三四五六日天])")
    private val periodRangeRegex = Regex("第?\\s*(\\d{1,2})\\s*[-–—~]\\s*(\\d{1,2})\\s*节")
    private val periodSingleRegex = Regex("第?\\s*(\\d{1,2})\\s*节")
    private val weekRangeRegex = Regex("(\\d{1,2})\\s*[-–—~]\\s*(\\d{1,2})\\s*周")
    private val weekListRegex = Regex("(\\d{1,2}(?:\\s*[,，]\\s*\\d{1,2})+)\\s*周")
    private val weekSingleRegex = Regex("(\\d{1,2})\\s*周")
    private val biweeklyRegex = Regex("([单双])\\s*周")
    private val tokenSplitRegex = Regex("[\\s,、/|;；]+")
    private val cjkNameRegex = Regex("^[\\u4e00-\\u9fa5]{2,4}$")
    private val locationHintRegex = Regex("[楼馆场室苑厅廊]")
    private val locationRoomRegex = Regex("\\d+\\s*[-–—]\\s*\\d+")
    private val locationPlainRoomRegex = Regex("^[A-Za-z]?\\d{3,4}$")

    /** 一键导入页的示例文本：与解析规则保持同步，测试会校验它可被完整解析。 */
    val SAMPLE: String = listOf(
        "高等数学A(1) 星期一 1-2节 1-16周 教1-201 张伟",
        "大学英语(二) 星期一 3-4节 1-16周 教2-305 李娜",
        "数据结构 星期二 1-2节 1-15周 教2-401 王强",
        "数据结构实验 星期三 3-4节 2-15周(双周) 实验楼B402 赵敏",
        "马克思主义基本原理 星期三 5-6节 1-16周 教1-101 陈晨",
        "大学物理B(1) 星期四 1-2节 1-16周 教2-102 刘洋",
        "体育(篮球) 星期四 5-6节 3-16周(单周) 体育馆 孙洁",
        "程序设计基础(Java) 星期五 3-4节 1-16周 教3-201 周涛",
        "线性代数 星期五 5-6节 9-16周 教1-305 吴静",
    ).joinToString("\n")

    fun parse(text: String): ParseResult {
        val sessions = ArrayList<ParsedSession>()
        val failed = ArrayList<String>()
        text.lines().forEach { raw ->
            val line = normalize(raw).trim()
            if (line.isEmpty()) return@forEach
            val session = parseLine(line)
            if (session == null) failed.add(raw.trim()) else sessions.add(session)
        }
        return ParseResult(merge(sessions), failed)
    }

    /** 全角字母数字与标点转半角，去掉零宽字符；换行交给逐行处理。 */
    private fun normalize(s: String): String = buildString(s.length) {
        for (ch in s) {
            when {
                ch == '\u3000' || ch == '\u200b' || ch == '\ufeff' -> append(' ')
                ch >= '\uFF01' && ch <= '\uFF5E' -> append(ch - 0xFEE0)
                else -> append(ch)
            }
        }
    }

    private fun parseLine(line: String): ParsedSession? {
        val weekdays = weekdayRegex.findAll(line)
            .mapNotNull { weekdayOf(it.groupValues[1]) }
            .toList().sorted().distinct()
        val periods = findPeriods(line)
        if (weekdays.isEmpty() || periods == null) return null

        val biweekly = biweeklyRegex.find(line)
        val weeks = collectWeeks(line)

        val removals = buildList {
            addAll(weekdayRegex.findAll(line).map { it.range })
            addAll(weeks.ranges)
            biweekly?.let { add(it.range) }
            periods.range?.let { add(it) }
        }
        val (name, teacher, location) = classify(line, removals)
        name ?: return null

        return ParsedSession(
            name = name,
            teacher = teacher,
            location = location,
            weekdays = weekdays,
            periodStart = periods.start,
            periodEnd = periods.end,
            weeksSpec = weeks.spec,
            isBiweekly = biweekly != null,
            biweeklyOdd = biweekly?.let { it.groupValues[1] == "单" },
        )
    }

    private fun weekdayOf(ch: String): Int? = when (ch) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        else -> 7
    }

    private fun findPeriods(line: String): PeriodsInfo? {
        periodRangeRegex.find(line)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..12 && b in 1..12 && a <= b) {
                return PeriodsInfo(a, b, m.range)
            }
        }
        periodSingleRegex.find(line)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            if (a != null && a in 1..12) return PeriodsInfo(a, a, m.range)
        }
        return null
    }

    private data class PeriodsInfo(val start: Int, val end: Int, val range: IntRange?)

    private data class WeeksInfo(val spec: String, val ranges: List<IntRange>)

    private fun collectWeeks(line: String): WeeksInfo {
        val weeks = sortedSetOf<Int>()
        val ranges = ArrayList<IntRange>()

        // 「周」前的数字串必须完整（前面不能再是数字）才算周次，
        // 否则「教3-201 周涛」会把「01 周」误判为第 1 周
        fun precededByDigit(m: MatchResult) = m.range.first > 0 && line[m.range.first - 1].isDigit()

        weekRangeRegex.findAll(line).forEach { m ->
            if (precededByDigit(m)) return@forEach
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a in 1..52 && b in 1..52 && a <= b) {
                weeks.addAll(a..b)
                ranges.add(m.range)
            }
        }
        weekListRegex.findAll(line).forEach { m ->
            if (precededByDigit(m) || ranges.any { overlaps(it, m.range) }) return@forEach
            val nums = m.groupValues[1].split(',', ',')
                .mapNotNull { it.trim().toIntOrNull() }
            if (nums.isNotEmpty() && nums.all { it in 1..52 }) {
                weeks.addAll(nums)
                ranges.add(m.range)
            }
        }
        weekSingleRegex.findAll(line).forEach { m ->
            if (precededByDigit(m) || ranges.any { overlaps(it, m.range) }) return@forEach
            val w = m.groupValues[1].toIntOrNull()
            if (w != null && w in 1..52) {
                weeks.add(w)
                ranges.add(m.range)
            }
        }
        return WeeksInfo(if (weeks.isEmpty()) "" else WeeksSpecParser.format(weeks), ranges)
    }

    /** 把识别出的星期/周次/节次/单双周片段从行中剪掉，剩余词再做课程名/教师/教室分类。 */
    private fun classify(
        line: String,
        removals: List<IntRange>,
    ): Triple<String?, String?, String?> {
        // 剪掉已识别片段后可能残留「(单周)」这类空括号，先清掉；课程名自身的括号要保留
        val rest = cutOut(line, removals.sortedBy { it.first })
            .replace("()", "").replace("[]", "").replace("{}", "")
        val tokens = rest.split(tokenSplitRegex).filter { it.isNotBlank() }

        var location: String? = null
        var name: String? = null
        var teacher: String? = null
        for (t in tokens) {
            if (location == null && isLocation(t)) {
                location = t
                continue
            }
            if (name == null) {
                name = t
            } else if (teacher == null && cjkNameRegex.matches(t)) {
                teacher = t
            }
        }
        return Triple(name, teacher, location)
    }

    private fun isLocation(token: String): Boolean =
        locationHintRegex.containsMatchIn(token) ||
            (token.any { it.isDigit() } && locationRoomRegex.containsMatchIn(token)) ||
            locationPlainRoomRegex.matches(token)

    private fun cutOut(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val sb = StringBuilder()
        var i = 0
        for (r in ranges) {
            if (r.first < i || r.isEmpty()) continue
            sb.append(text, i, r.first)
            i = r.last + 1
        }
        sb.append(text, i, text.length)
        return sb.toString()
    }

    private fun overlaps(a: IntRange, b: IntRange): Boolean =
        a.first <= b.last && b.first <= a.last

    /** 同名同教师同教室同节次同周次的行合并为一门课（weekdays 取并集），保持首次出现顺序。 */
    private fun merge(sessions: List<ParsedSession>): List<ParsedSession> {
        val out = LinkedHashMap<String, ParsedSession>()
        for (s in sessions) {
            val key = "${s.name}|${s.teacher}|${s.location}|${s.periodStart}|${s.periodEnd}|" +
                "${s.weeksSpec}|${s.isBiweekly}|${s.biweeklyOdd}"
            val prev = out[key]
            out[key] = if (prev == null) {
                s
            } else {
                prev.copy(weekdays = (prev.weekdays + s.weekdays).distinct().sorted())
            }
        }
        return out.values.toList()
    }
}
