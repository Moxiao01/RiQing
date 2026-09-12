package com.riqing.core.ai.tools

import com.riqing.core.common.TimeUtils
import com.riqing.core.common.WeeksSpecParser
import com.riqing.core.data.repo.AgendaRepository
import com.riqing.core.data.repo.CourseRepository
import com.riqing.core.data.repo.EventRepository
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.model.AgendaItem
import com.riqing.core.model.RepeatFreq
import com.riqing.core.model.RiQingError
import com.riqing.core.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed class ToolOutcome {
    data class Ok(val data: Map<String, Any?>) : ToolOutcome()
    data class Fail(val code: String, val message: String, val field: String? = null) : ToolOutcome()
}

/**
 * D4 工具执行器：校验后调用 Repository。风险确认由 UI 层负责，这里只执行。
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val events: EventRepository,
    private val todos: TodoRepository,
    private val courses: CourseRepository,
    private val semesters: SemesterRepository,
    private val agendaRepository: AgendaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(name: String, argsJson: String): ToolOutcome {
        val args = runCatching {
            if (argsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(argsJson).jsonObject
        }.getOrElse { return ToolOutcome.Fail("VALIDATION_ERROR", "参数不是合法 JSON") }

        return try {
            when (name) {
                "list_events" -> listEvents(args)
                "create_event" -> createEvent(args)
                "update_event" -> updateEvent(args)
                "delete_event" -> deleteEvent(args)
                "list_todos" -> listTodos(args)
                "create_todo" -> createTodo(args)
                "update_todo" -> updateTodo(args)
                "complete_todo" -> completeTodo(args)
                "delete_todo" -> deleteTodo(args)
                "list_courses" -> listCourses(args)
                "create_course" -> createCourse(args)
                "update_course" -> updateCourse(args)
                "delete_course" -> deleteCourse(args)
                "get_semester" -> getSemester(args)
                else -> ToolOutcome.Fail("VALIDATION_ERROR", "未知工具 $name")
            }
        } catch (e: RiQingError) {
            ToolOutcome.Fail(e.code, e.message ?: "error", e.field)
        } catch (e: java.time.format.DateTimeParseException) {
            ToolOutcome.Fail("VALIDATION_ERROR", "时间格式无法解析：${e.parsedString ?: e.message}", null)
        } catch (e: IllegalArgumentException) {
            ToolOutcome.Fail("VALIDATION_ERROR", e.message ?: "参数非法")
        } catch (e: Exception) {
            ToolOutcome.Fail("INTERNAL", e.message ?: "内部错误")
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNullSafe()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? {
        return runCatching { content }.getOrNull()
    }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.longOrNull?.toInt()
        ?: this[key]?.jsonPrimitive?.content?.toIntOrNull()

    private fun JsonObject.long(key: String): Long? {
        val p = this[key]?.jsonPrimitive ?: return null
        return p.longOrNull ?: p.content.toLongOrNull()
            ?: runCatching { parseIsoToEpoch(p.content) }.getOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
        ?: this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

    private fun JsonObject.intList(key: String): List<Int>? =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNull?.toInt() ?: it.jsonPrimitive.content.toIntOrNull() }

    private fun parseIsoToEpoch(raw: String): Long {
        val t = raw.trim()
        return if (t.length <= 10) {
            TimeUtils.toEpoch(LocalDate.parse(t))
        } else {
            TimeUtils.toEpoch(LocalDateTime.parse(t.replace(' ', 'T').let {
                if (it.length == 16) "$it:00" else it
            }))
        }
    }

    private fun parseIsoDateOnly(raw: String): LocalDate = LocalDate.parse(raw.trim())

    private suspend fun listEvents(args: JsonObject): ToolOutcome {
        val startIso = args.str("start") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 start", "start")
        val endIso = args.str("end") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 end", "end")
        val start = parseIsoToEpoch(startIso)
        val end = parseIsoToEpoch(endIso)
        val keyword = args.str("keyword")

        val startDay = TimeUtils.localDateOf(start)
        val endDay = TimeUtils.localDateOf(end).let { if (end == TimeUtils.startOfDay(it)) it.minusDays(1) else it }
        val exceptions = events.allExceptions()
        val result = mutableListOf<Map<String, Any?>>()
        var day = startDay
        while (!day.isAfter(endDay)) {
            val dayAgenda = agendaRepository.dayAgenda(day)
            dayAgenda.items.forEach { item ->
                if (item !is AgendaItem.EventOccurrence) return@forEach
                if (!keyword.isNullOrBlank() &&
                    !item.title.contains(keyword, true) &&
                    !(item.notes?.contains(keyword, true) == true)
                ) {
                    return@forEach
                }
                result += mapOf(
                    "kind" to "event",
                    "id" to item.eventId,
                    "occurrenceId" to item.id,
                    "title" to item.title,
                    "startAt" to TimeUtils.formatAbs(item.startAt),
                    "endAt" to TimeUtils.formatAbs(item.endAt),
                    "isAllDay" to item.isAllDay,
                    "notes" to item.notes,
                )
            }
            day = day.plusDays(1)
        }
        return ToolOutcome.Ok(mapOf("events" to result))
    }

    private suspend fun createEvent(args: JsonObject): ToolOutcome {
        val title = args.str("title") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 title", "title")
        val startAt = args.long("startAt")
            ?: args.str("startAt")?.let { parseIsoToEpoch(it) }
            ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 startAt", "startAt")
        val endAt = args.long("endAt")
            ?: args.str("endAt")?.let { parseIsoToEpoch(it) }
            ?: startAt + 60 * 60_000L
        val repeat = args.str("repeatFreq")?.let { RepeatFreq.valueOf(it.uppercase()) }
        val repeatUntil = args.str("repeatUntil")?.let { parseIsoToEpoch(it) }
        val event = events.create(
            title = title,
            startAt = startAt,
            endAt = endAt,
            isAllDay = args.bool("isAllDay") ?: false,
            notes = args.str("notes"),
            reminderOffsets = args["reminderOffsets"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList(),
            repeatFreq = repeat,
            repeatUntil = repeatUntil,
            repeatCount = args.int("repeatCount"),
            repeatWeekdays = args["repeatWeekdays"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() },
            source = Source.AGENT,
        )
        return ToolOutcome.Ok(
            mapOf(
                "event" to mapOf(
                    "id" to event.id,
                    "title" to event.title,
                    "startAt" to TimeUtils.formatAbs(event.startAt),
                    "endAt" to TimeUtils.formatAbs(event.endAt),
                ),
                "undoHint" to "可在结果卡撤销",
            ),
        )
    }

    private suspend fun updateEvent(args: JsonObject): ToolOutcome {
        val id = args.str("eventId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 eventId", "eventId")
        val scope = args.str("scope") ?: "SERIES"
        if (scope.equals("THIS", true)) {
            val occ = args.str("occurrenceStartAt")?.let { parseIsoToEpoch(it) }
                ?: return ToolOutcome.Fail("VALIDATION_ERROR", "scope=THIS 需要 occurrenceStartAt", "occurrenceStartAt")
            val exception = events.updateOccurrence(
                eventId = id,
                occurrenceStartAt = occ,
                title = args.str("title"),
                startAt = args.str("startAt")?.let { parseIsoToEpoch(it) },
                endAt = args.str("endAt")?.let { parseIsoToEpoch(it) },
            )
            return ToolOutcome.Ok(mapOf("scope" to "THIS", "exceptionId" to exception.id))
        }
        val updated = events.update(
            id = id,
            title = args.str("title"),
            startAt = args.str("startAt")?.let { parseIsoToEpoch(it) },
            endAt = args.str("endAt")?.let { parseIsoToEpoch(it) },
            isAllDay = args.bool("isAllDay"),
            notes = args.str("notes"),
            reminderOffsets = args["reminderOffsets"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() },
            repeatFreq = args.str("repeatFreq")?.let { RepeatFreq.valueOf(it.uppercase()) },
            repeatUntil = args.str("repeatUntil")?.let { parseIsoToEpoch(it) },
            repeatCount = args.int("repeatCount"),
            repeatWeekdays = args["repeatWeekdays"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList(),
        )
        return ToolOutcome.Ok(
            mapOf(
                "event" to mapOf(
                    "id" to updated.id,
                    "title" to updated.title,
                    "startAt" to TimeUtils.formatAbs(updated.startAt),
                    "endAt" to TimeUtils.formatAbs(updated.endAt),
                ),
            ),
        )
    }

    private suspend fun deleteEvent(args: JsonObject): ToolOutcome {
        val id = args.str("eventId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 eventId", "eventId")
        val scope = args.str("scope") ?: "SERIES"
        val occ = args.str("occurrenceStartAt")?.let { parseIsoToEpoch(it) }
        if (scope.equals("THIS", true)) {
            if (occ == null) return ToolOutcome.Fail("VALIDATION_ERROR", "scope=THIS 需要 occurrenceStartAt", "occurrenceStartAt")
            events.delete(id, occ)
            return ToolOutcome.Ok(mapOf("deleted" to "THIS", "eventId" to id))
        }
        events.delete(id, null)
        return ToolOutcome.Ok(mapOf("deleted" to "SERIES", "eventId" to id))
    }

    private suspend fun listTodos(args: JsonObject): ToolOutcome {
        val date = args.str("date")
        val includeDone = args.bool("includeDone") ?: false
        val keyword = args.str("keyword")
        val list = when {
            !keyword.isNullOrBlank() -> todos.search(keyword)
            date != null -> todos.listForDay(date)
            else -> todos.listActive()
        }.filter { includeDone || !it.isDone }
        return ToolOutcome.Ok(
            mapOf(
                "todos" to list.map {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title,
                        "dueAt" to it.dueAt?.let { d -> TimeUtils.formatAbs(d) },
                        "isDone" to it.isDone,
                        "notes" to it.notes,
                    )
                },
            ),
        )
    }

    private suspend fun createTodo(args: JsonObject): ToolOutcome {
        val title = args.str("title") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 title", "title")
        val todo = todos.create(
            title = title,
            dueAt = args.str("dueAt")?.let { parseIsoToEpoch(it) },
            notes = args.str("notes"),
            reminderOffsets = args["reminderOffsets"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList(),
            source = Source.AGENT,
        )
        return ToolOutcome.Ok(
            mapOf(
                "todo" to mapOf(
                    "id" to todo.id,
                    "title" to todo.title,
                    "dueAt" to todo.dueAt?.let { TimeUtils.formatAbs(it) },
                ),
            ),
        )
    }

    private suspend fun updateTodo(args: JsonObject): ToolOutcome {
        val id = args.str("todoId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 todoId", "todoId")
        val todo = todos.update(
            id = id,
            title = args.str("title"),
            dueAt = args.str("dueAt")?.let { parseIsoToEpoch(it) },
            notes = args.str("notes"),
            isDone = args.bool("isDone"),
        )
        return ToolOutcome.Ok(mapOf("todo" to mapOf("id" to todo.id, "title" to todo.title, "isDone" to todo.isDone)))
    }

    private suspend fun completeTodo(args: JsonObject): ToolOutcome {
        val id = args.str("todoId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 todoId", "todoId")
        val todo = todos.complete(id)
        return ToolOutcome.Ok(mapOf("todo" to mapOf("id" to todo.id, "title" to todo.title, "isDone" to true)))
    }

    private suspend fun deleteTodo(args: JsonObject): ToolOutcome {
        val id = args.str("todoId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 todoId", "todoId")
        todos.delete(id)
        return ToolOutcome.Ok(mapOf("deleted" to id))
    }

    private suspend fun listCourses(args: JsonObject): ToolOutcome {
        val semId = args.str("semesterId")
        val sem = if (semId != null) semesters.getOrThrow(semId) else semesters.getCurrent()
            ?: return ToolOutcome.Ok(mapOf("courses" to emptyList<Any>(), "hint" to "尚未配置学期"))
        val list = courses.listBySemester(sem.id)
        val week = args.int("teachingWeek")
        val date = args.str("date")?.let { parseIsoDateOnly(it) }
        return ToolOutcome.Ok(
            mapOf(
                "semester" to mapOf("id" to sem.id, "name" to sem.name),
                "courses" to list.map { c ->
                    mapOf(
                        "id" to c.id,
                        "name" to c.name,
                        "weekday" to c.weekday,
                        "weekdays" to c.effectiveWeekdays,
                        "periods" to "${c.periodStart}-${c.periodEnd}",
                        "weeksSpec" to c.weeksSpec,
                        "location" to c.location,
                        "teachingWeeks" to WeeksSpecParser.parse(c.weeksSpec).getOrDefault(emptySet()).sorted(),
                        "requestedWeek" to week,
                        "requestedDate" to date?.toString(),
                    )
                },
            ),
        )
    }

    private suspend fun createCourse(args: JsonObject): ToolOutcome {
        val course = courses.create(
            name = args.str("name") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 name", "name"),
            semesterId = args.str("semesterId"),
            teacher = args.str("teacher"),
            location = args.str("location"),
            weekday = args.int("weekday")
                ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 weekday", "weekday"),
            weekdays = args.intList("weekdays"),
            periodStart = args.int("periodStart")
                ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 periodStart", "periodStart"),
            periodEnd = args.int("periodEnd")
                ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 periodEnd", "periodEnd"),
            weeksSpec = args.str("weeksSpec")
                ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 weeksSpec", "weeksSpec"),
            isBiweekly = args.bool("isBiweekly") ?: false,
            biweeklyOdd = args.bool("biweeklyOdd"),
            reminderOffsets = args["reminderOffsets"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: listOf(10),
            colorToken = args.str("colorToken") ?: "c1",
            source = Source.AGENT,
        )
        return ToolOutcome.Ok(
            mapOf(
                "course" to mapOf(
                    "id" to course.id,
                    "name" to course.name,
                    "weekday" to course.weekday,
                    "weekdays" to course.effectiveWeekdays,
                    "periods" to "${course.periodStart}-${course.periodEnd}",
                    "weeksSpec" to course.weeksSpec,
                    "location" to course.location,
                ),
            ),
        )
    }

    private suspend fun updateCourse(args: JsonObject): ToolOutcome {
        val id = args.str("courseId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 courseId", "courseId")
        val course = courses.update(
            id = id,
            name = args.str("name"),
            teacher = args.str("teacher"),
            location = args.str("location"),
            weekday = args.int("weekday"),
            weekdays = args.intList("weekdays"),
            periodStart = args.int("periodStart"),
            periodEnd = args.int("periodEnd"),
            weeksSpec = args.str("weeksSpec"),
            reminderOffsets = args["reminderOffsets"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() },
            colorToken = args.str("colorToken"),
        )
        return ToolOutcome.Ok(
            mapOf(
                "course" to mapOf(
                    "id" to course.id,
                    "name" to course.name,
                    "weeksSpec" to course.weeksSpec,
                    "location" to course.location,
                ),
                "warning" to "将影响第 ${course.weeksSpec} 周投影",
            ),
        )
    }

    private suspend fun deleteCourse(args: JsonObject): ToolOutcome {
        val id = args.str("courseId") ?: return ToolOutcome.Fail("VALIDATION_ERROR", "缺少 courseId", "courseId")
        courses.delete(id)
        return ToolOutcome.Ok(mapOf("deleted" to id, "note" to "之后不再出现在课表"))
    }

    private suspend fun getSemester(args: JsonObject): ToolOutcome {
        val with = semesters.getWithSlots(args.str("semesterId"))
            ?: return ToolOutcome.Ok(mapOf("semester" to null, "hint" to "尚未配置学期"))
        return ToolOutcome.Ok(
            mapOf(
                "semester" to mapOf(
                    "id" to with.semester.id,
                    "name" to with.semester.name,
                    "week1Monday" to with.semester.week1Monday,
                    "endDate" to with.semester.endDate,
                    "isCurrent" to with.semester.isCurrent,
                    "maxTeachingWeek" to with.maxTeachingWeek,
                    "periods" to with.slots.map {
                        mapOf("index" to it.periodIndex, "startMinutes" to it.startMinutes, "endMinutes" to it.endMinutes)
                    },
                ),
            ),
        )
    }
}
