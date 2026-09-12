package com.riqing.core.ai

import com.riqing.core.ai.tools.ToolExecutor
import com.riqing.core.ai.tools.ToolOutcome
import com.riqing.core.ai.tools.ToolSchemas
import com.riqing.core.datastore.AiSettings
import com.riqing.core.datastore.SettingsDataStore
import com.riqing.core.model.RiQingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class RiskLevel { LOW, MEDIUM, HIGH }

@Serializable
data class PendingToolCall(
    val tool: String,
    val arguments: String,
    val risk: RiskLevel,
    val summary: String,
    val toolCallId: String,
    val assistantMessage: ChatMessage,
)

@Serializable
data class ToolExecutionResult(
    val tool: String,
    val toolCallId: String? = null,
    val ok: Boolean,
    val dataJson: String,
    val risk: RiskLevel,
    val undoEligible: Boolean,
    val message: String? = null,
) {
    /** 作为 role=tool 消息并回会话历史（OpenAI 协议要求 tool_call_id）。 */
    fun toToolMessage(): ChatMessage =
        ChatMessage(role = "tool", content = dataJson, toolCallId = toolCallId)
}

data class AgentChatResult(
    val assistantText: String? = null,
    /** 本轮产生的、需要并入会话历史的消息（assistant tool_calls + 工具结果）。 */
    val messagesToAdd: List<ChatMessage> = emptyList(),
    val toolResults: List<ToolExecutionResult> = emptyList(),
    val pendingConfirm: PendingToolCall? = null,
)

/**
 * Agent 会话编排：低风险直接执行；中高风险返回 pendingConfirm 由 UI 确认。
 */
@Singleton
class AgentService @Inject constructor(
    private val client: OpenAiClient,
    private val toolExecutor: ToolExecutor,
    private val settingsDataStore: SettingsDataStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val systemPrompt = buildString {
        appendLine("你是「日清」App 内的本地日程助手，帮助用户增删改查日程(Event)、待办(Todo)、课程(Course)。")
        appendLine("硬约束：")
        appendLine("1. 只使用提供的工具读写数据，不要编造 id。")
        appendLine("2. 时间参数必须输出本地 ISO-8601 不带 offset，如 2026-08-12T15:00:00。")
        appendLine("3. 模糊时间（明天下午、过几天）能合理解析就解析；不确定必须向用户反问，禁止猜测写入。")
        appendLine("4. 「明天下午」创建时可建议 15:00，但结果中应写绝对时间。")
        appendLine("5. 删除是高风险，确认由系统完成；你只需生成工具调用。")
        appendLine("6. 课程(create_course)与日程(create_event)分流：课名+节次+周次→课程；单次安排→日程。")
        appendLine("7. 重复必须有 until 或 count。")
        appendLine("8. 回复用户使用简体中文，简洁。")
        appendLine("9. 用户消息可能以 [yyyy-MM-dd HH:mm 周X] 前缀标注发送时间；「今晚/明天/下周」等相对时间以最近前缀或下方当前时间为基准解析，不要反问日期。")
    }

    fun riskOf(tool: String): RiskLevel = when (tool) {
        "list_events", "list_todos", "list_courses", "get_semester",
        "create_event", "create_todo", "complete_todo",
        -> RiskLevel.LOW
        "update_event", "update_todo", "create_course", "update_course",
        -> RiskLevel.MEDIUM
        "delete_event", "delete_todo", "delete_course",
        -> RiskLevel.HIGH
        else -> RiskLevel.MEDIUM
    }

    suspend fun currentAi(): AiSettings = settingsDataStore.settings.first().ai

    suspend fun isConfigured(): Boolean {
        val ai = currentAi()
        return ai.enabled && ai.apiKey.isNotBlank() && ai.endpointUrl.isNotBlank() && ai.model.isNotBlank()
    }

    /**
     * 一轮对话 = 用户输入 + 若干「模型 → 工具结果回传」往返（D4 §6）。
     * 低风险工具直接执行；首个中/高风险工具转为 pendingConfirm，用户确认后由
     * [continueChat] 续跑同一轮。同一响应里的多个工具调用会依次处理（批量场景）。
     */
    suspend fun chat(
        history: List<ChatMessage>,
        userText: String,
    ): AgentChatResult = runTurn(history + ChatMessage(role = "user", content = userText))

    /** 用户确认（或执行完）中/高风险工具后，带着工具结果续跑当前轮。 */
    suspend fun continueChat(history: List<ChatMessage>): AgentChatResult = runTurn(history)

    private suspend fun runTurn(inputMessages: List<ChatMessage>): AgentChatResult =
        withContext(Dispatchers.IO) {
            val ai = currentAi()
            if (!ai.enabled || ai.apiKey.isBlank() || ai.endpointUrl.isBlank() || ai.model.isBlank()) {
                throw RiQingError.Unauthorized("尚未配置 AI，请到 设置 → AI 配置")
            }

            val working = inputMessages.takeLast(TURN_MESSAGE_LIMIT).toMutableList()
            repeat(MAX_TOOL_ROUNDS) {
                val response = client.chat(ai, request(ai, working))
                val msg = response.choices.firstOrNull()?.message
                    ?: return@withContext AgentChatResult(assistantText = "模型没有返回内容")

                val toolCalls = msg.toolCalls.orEmpty()
                if (toolCalls.isEmpty()) {
                    return@withContext AgentChatResult(assistantText = msg.content ?: "（空回复）")
                }

                val executed = mutableListOf<ToolExecutionResult>()
                var pending: PendingToolCall? = null
                for (call in toolCalls) {
                    if (riskOf(call.function.name) != RiskLevel.LOW) {
                        pending = PendingToolCall(
                            tool = call.function.name,
                            arguments = call.function.arguments,
                            risk = riskOf(call.function.name),
                            summary = summarizeCall(call.function.name, call.function.arguments),
                            toolCallId = call.id,
                            assistantMessage = msg,
                        )
                        break
                    }
                    val outcome = toolExecutor.execute(call.function.name, call.function.arguments)
                    executed += toResult(call.function.name, call.id, outcome)
                }

                working += msg
                executed.forEach { r ->
                    working += ChatMessage(role = "tool", content = r.dataJson, toolCallId = r.toolCallId)
                }

                if (pending != null) {
                    val pc = pending
                    // 协议要求每个 tool_call 恰好一条应答：排在待确认调用之后的调用
                    // 先回 CANCELLED 占位，模型可在下一轮重新发起
                    val deferred = toolCalls.dropWhile { it.id != pc.toolCallId }.drop(1)
                        .map { call ->
                            ChatMessage(
                                role = "tool",
                                content = """{"code":"CANCELLED","message":"请在前一个操作确认后重新发起该调用"}""",
                                toolCallId = call.id,
                            )
                        }
                    return@withContext AgentChatResult(
                        assistantText = msg.content,
                        messagesToAdd = listOf(msg) + executed.map { it.toToolMessage() } + deferred,
                        toolResults = executed,
                        pendingConfirm = pending,
                    )
                }
            }
            AgentChatResult(assistantText = "（多步操作较多，已暂停。请继续或换一种说法。）")
        }

    private fun request(ai: AiSettings, messages: List<ChatMessage>): ChatCompletionRequest {
        val tools = ToolSchemas.all().map { (name, schema) ->
            ToolSpec(
                function = FunctionSpec(
                    name = name,
                    description = toolDescriptions[name] ?: name,
                    parameters = schema,
                ),
            )
        }
        // 每次请求注入实时本地时间，模型才能解析「今晚/明天」而不反问日期
        val nowLine = "当前本地时间：" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINA))
        return ChatCompletionRequest(
            model = ai.model,
            messages = listOf(ChatMessage(role = "system", content = "$systemPrompt$nowLine")) + messages,
            tools = tools,
            toolChoice = "auto",
            temperature = ai.temperature ?: 0.2,
        )
    }

    suspend fun executeConfirmed(pending: PendingToolCall): ToolExecutionResult =
        toResult(pending.tool, pending.toolCallId, toolExecutor.execute(pending.tool, pending.arguments))

    suspend fun executeToolRaw(tool: String, args: String): ToolExecutionResult =
        toResult(tool, null, toolExecutor.execute(tool, args))

    private fun toResult(tool: String, toolCallId: String?, outcome: ToolOutcome): ToolExecutionResult =
        when (outcome) {
            is ToolOutcome.Ok -> {
                val dataJson = json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        outcome.data.forEach { (k, v) -> put(k, toJsonElement(v)) }
                    },
                )
                ToolExecutionResult(
                    tool = tool,
                    toolCallId = toolCallId,
                    ok = true,
                    dataJson = dataJson,
                    risk = riskOf(tool),
                    undoEligible = tool !in setOf(
                        "list_events", "list_todos", "list_courses", "get_semester",
                    ),
                    message = null,
                )
            }
            is ToolOutcome.Fail -> ToolExecutionResult(
                tool = tool,
                toolCallId = toolCallId,
                ok = false,
                dataJson = """{"code":"${outcome.code}","message":${JsonPrimitive(outcome.message)}}""",
                risk = riskOf(tool),
                undoEligible = false,
                message = outcome.message,
            )
        }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> put(k.toString(), toJsonElement(v)) }
        }
        is List<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    fun summarizeCall(tool: String, args: String): String {
        return try {
            val element = json.parseToJsonElement(args)
            if (element !is JsonObject) return tool
            val title = element["title"]?.asString()
                ?: element["name"]?.asString()
            val start = element["startAt"]?.asString()
                ?: element["dueAt"]?.asString()
            buildString {
                append(tool)
                if (!title.isNullOrBlank()) append(" · $title")
                if (!start.isNullOrBlank()) append(" · $start")
            }
        } catch (_: Exception) {
            tool
        }
    }

    private fun JsonElement.asString(): String? =
        (this as? JsonPrimitive)?.let { runCatching { it.content }.getOrNull() }

    companion object {
        private const val MAX_TOOL_ROUNDS = 4
        private const val TURN_MESSAGE_LIMIT = 40

        private val toolDescriptions = mapOf(
            "list_events" to "按时间范围列出日程（含重复展开）",
            "create_event" to "创建日程",
            "update_event" to "修改日程（可仅本次）",
            "delete_event" to "删除日程（可仅本次）",
            "list_todos" to "列出待办",
            "create_todo" to "创建待办",
            "update_todo" to "修改待办",
            "complete_todo" to "标记待办完成",
            "delete_todo" to "删除待办",
            "list_courses" to "按周/日期列出课程定义",
            "create_course" to "创建课程定义",
            "update_course" to "修改课程定义",
            "delete_course" to "删除课程定义",
            "get_semester" to "获取当前或指定学期与节次",
        )
    }
}
