package com.riqing.core.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * D4 工具 JSON Schema（OpenAI function calling）。
 */
object ToolSchemas {

    private fun obj(
        properties: JsonObject,
        required: List<String> = emptyList(),
        description: String? = null,
    ): JsonElement = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        if (required.isNotEmpty()) {
            put("required", buildJsonArray(required))
        }
        description?.let { put("description", it) }
    }

    private fun buildJsonArray(items: List<String>): JsonElement =
        kotlinx.serialization.json.JsonArray(items.map { JsonPrimitive(it) })

    private fun str(desc: String): JsonElement = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun num(desc: String): JsonElement = buildJsonObject {
        put("type", "number")
        put("description", desc)
    }

    private fun int(desc: String): JsonElement = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun bool(desc: String): JsonElement = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    private fun arrayOfInt(desc: String): JsonElement = buildJsonObject {
        put("type", "array")
        put("items", buildJsonObject { put("type", "integer") })
        put("description", desc)
    }

    fun all(): List<Pair<String, JsonElement>> = listOf(
        "list_events" to obj(
            buildJsonObject {
                put("start", str("ISO 本地日期时间，含，如 2026-08-12T00:00:00"))
                put("end", str("ISO 本地日期时间，不含"))
                put("keyword", str("可选，标题/备注关键词"))
            },
            listOf("start", "end"),
        ),
        "create_event" to obj(
            buildJsonObject {
                put("title", str("标题 1-80 字"))
                put("startAt", str("ISO 本地，如 2026-08-12T15:00:00"))
                put("endAt", str("可选 ISO 本地；缺省开始+60分钟"))
                put("isAllDay", bool("是否全天"))
                put("notes", str("备注"))
                put("reminderOffsets", arrayOfInt("提醒偏移分钟列表"))
                put("repeatFreq", str("DAILY|WEEKLY|WORKDAYS"))
                put("repeatUntil", str("重复结束 ISO 日期或时间"))
                put("repeatCount", int("重复次数 1-365"))
                put("repeatWeekdays", arrayOfInt("WEEKLY 生效星期几 1-7（1=周一）"))
            },
            listOf("title", "startAt"),
        ),
        "update_event" to obj(
            buildJsonObject {
                put("eventId", str("日程 id"))
                put("scope", str("THIS|SERIES"))
                put("occurrenceStartAt", str("scope=THIS 时必填，原实例开始 ISO"))
                put("title", str("新标题"))
                put("startAt", str("新开始 ISO"))
                put("endAt", str("新结束 ISO"))
                put("isAllDay", bool("是否全天"))
                put("notes", str("新备注"))
                put("reminderOffsets", arrayOfInt("提醒偏移分钟列表"))
                put("repeatFreq", str("DAILY|WEEKLY|WORKDAYS"))
                put("repeatUntil", str("重复结束 ISO 日期或时间"))
                put("repeatCount", int("重复次数 1-365"))
                put("repeatWeekdays", arrayOfInt("WEEKLY 生效星期几 1-7（1=周一），空数组清除"))
            },
            listOf("eventId"),
        ),
        "delete_event" to obj(
            buildJsonObject {
                put("eventId", str("日程 id"))
                put("scope", str("THIS|SERIES 默认 SERIES"))
                put("occurrenceStartAt", str("scope=THIS 时必填"))
            },
            listOf("eventId"),
        ),
        "list_todos" to obj(
            buildJsonObject {
                put("date", str("ISO 本地日期 YYYY-MM-DD"))
                put("includeDone", bool("是否含已完成"))
                put("keyword", str("关键词"))
            },
        ),
        "create_todo" to obj(
            buildJsonObject {
                put("title", str("标题"))
                put("dueAt", str("截止 ISO 本地，可选"))
                put("notes", str("备注"))
                put("reminderOffsets", arrayOfInt("相对截止提醒分钟"))
            },
            listOf("title"),
        ),
        "update_todo" to obj(
            buildJsonObject {
                put("todoId", str("待办 id"))
                put("title", str("标题"))
                put("dueAt", str("截止"))
                put("notes", str("备注"))
                put("isDone", bool("完成态"))
            },
            listOf("todoId"),
        ),
        "complete_todo" to obj(
            buildJsonObject { put("todoId", str("待办 id")) },
            listOf("todoId"),
        ),
        "delete_todo" to obj(
            buildJsonObject { put("todoId", str("待办 id")) },
            listOf("todoId"),
        ),
        "list_courses" to obj(
            buildJsonObject {
                put("teachingWeek", int("教学周"))
                put("date", str("ISO 日期"))
                put("semesterId", str("学期 id，可选"))
            },
        ),
        "create_course" to obj(
            buildJsonObject {
                put("name", str("课程名"))
                put("semesterId", str("学期 id，可选默认当前"))
                put("teacher", str("教师"))
                put("location", str("地点"))
                put("weekday", int("1-7，1=周一"))
                put("weekdays", arrayOfInt("一周多节时的星期数组，如 [1,3,5]；可选，默认用 weekday"))
                put("periodStart", int("开始节次"))
                put("periodEnd", int("结束节次"))
                put("weeksSpec", str("如 1-16 或 1,3,5-8"))
                put("isBiweekly", bool("是否单双周"))
                put("biweeklyOdd", bool("true=单周"))
                put("reminderOffsets", arrayOfInt("提醒分钟"))
                put("colorToken", str("c1-c8"))
            },
            listOf("name", "weekday", "periodStart", "periodEnd", "weeksSpec"),
        ),
        "update_course" to obj(
            buildJsonObject {
                put("courseId", str("课程 id"))
                put("name", str("课程名"))
                put("location", str("地点"))
                put("teacher", str("教师"))
                put("weekday", int("1-7"))
                put("weekdays", arrayOfInt("一周多节时的星期数组，如 [1,3,5]；可选"))
                put("periodStart", int("起"))
                put("periodEnd", int("止"))
                put("weeksSpec", str("周次"))
                put("isBiweekly", bool("是否单双周"))
                put("biweeklyOdd", bool("true=单周"))
                put("reminderOffsets", arrayOfInt("提醒分钟"))
                put("colorToken", str("c1-c8"))
            },
            listOf("courseId"),
        ),
        "delete_course" to obj(
            buildJsonObject { put("courseId", str("课程 id")) },
            listOf("courseId"),
        ),
        "get_semester" to obj(
            buildJsonObject { put("semesterId", str("可选，默认当前")) },
        ),
    )
}
