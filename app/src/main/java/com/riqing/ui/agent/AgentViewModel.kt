package com.riqing.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.ai.AgentChatResult
import com.riqing.core.ai.AgentService
import com.riqing.core.ai.ChatMessage
import com.riqing.core.ai.PendingToolCall
import com.riqing.core.ai.ToolExecutionResult
import com.riqing.core.data.repo.UndoService
import com.riqing.core.datastore.AgentChatStore
import com.riqing.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@Serializable
sealed class ChatItem {
    @Serializable
    @SerialName("user")
    data class User(val text: String) : ChatItem()

    @Serializable
    @SerialName("assistant")
    data class Assistant(val text: String) : ChatItem()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val text: String = "思考中…") : ChatItem()

    @Serializable
    @SerialName("confirm")
    data class Confirm(
        val pending: PendingToolCall,
        val batch: Boolean = false,
    ) : ChatItem()

    @Serializable
    @SerialName("result")
    data class Result(
        val result: ToolExecutionResult,
        val display: String,
    ) : ChatItem()

    @Serializable
    @SerialName("error")
    data class Error(val text: String) : ChatItem()
}

data class AgentUiState(
    val configured: Boolean = false,
    val items: List<ChatItem> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    // 默认 false：DataStore 读取失败时宁可多展示一次出网说明（PV-02）
    val privacyAck: Boolean = false,
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentService: AgentService,
    private val settingsDataStore: SettingsDataStore,
    private val undoService: UndoService,
    private val agentChatStore: AgentChatStore,
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _ui = MutableStateFlow(AgentUiState())
    val ui: StateFlow<AgentUiState> = _ui.asStateFlow()

    private val history = mutableListOf<ChatMessage>()

    init {
        viewModelScope.launch {
            val configured = runCatching { agentService.isConfigured() }.getOrDefault(false)
            // 不能在 collect 里抛 CancellationException 退出——那会取消整个协程
            val ack = runCatching {
                withTimeoutOrNull(1500) {
                    settingsDataStore.settings.first().agentPrivacyAck
                }
            }.getOrNull()
            restoreChat()
            // 读取失败/超时（ack == null）保持默认 false，展示隐私说明
            _ui.update { it.copy(configured = configured, privacyAck = ack ?: false) }
        }
    }

    /** 启动时恢复上次对话（界面气泡 + 模型上下文），损坏则丢弃。 */
    private suspend fun restoreChat() {
        try {
            val saved = agentChatStore.load()
            val items = saved.itemsJson?.let { json.decodeFromString<List<ChatItem>>(it) } ?: return
            if (items.isEmpty()) return
            val msgs = saved.historyJson?.let { json.decodeFromString<List<ChatMessage>>(it) } ?: emptyList()
            history += msgs
            _ui.update { it.copy(items = items) }
        } catch (_: Exception) {
            history.clear()
        }
    }

    fun refreshConfigured() {
        viewModelScope.launch {
            _ui.update { it.copy(configured = agentService.isConfigured()) }
        }
    }

    fun setInput(v: String) = _ui.update { it.copy(input = v) }

    fun ackPrivacy() {
        viewModelScope.launch {
            settingsDataStore.setAgentPrivacyAck(true)
            _ui.update { it.copy(privacyAck = true) }
        }
    }

    /** 清空对话：界面气泡与模型上下文一并丢弃并清持久化。 */
    fun clearChat() {
        history.clear()
        _ui.update { it.copy(items = emptyList()) }
        persist()
    }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.sending) return
        viewModelScope.launch {
            // 不信任缓存的 configured：设置页可能刚保存过
            if (!agentService.isConfigured()) {
                _ui.update {
                    it.copy(
                        configured = false,
                        sending = false,
                        items = it.items + ChatItem.Error("尚未配置 AI，请到 设置 → AI 配置"),
                    )
                }
                return@launch
            }
            _ui.update { it.copy(configured = true) }
            doSend(text)
        }
    }

    private fun doSend(text: String) {
        _ui.update {
            it.copy(
                input = "",
                sending = true,
                items = it.items + ChatItem.User(text) + ChatItem.Thinking(),
            )
        }
        viewModelScope.launch {
            // 指令前缀发送时间戳，模型才能解析「今晚8点」这类相对时间而不反问日期
            val stamped = "[${timestamp()}] $text"
            val turns = runCatching { settingsDataStore.settings.first().agentContextTurns }
                .getOrDefault(DEFAULT_CONTEXT_TURNS)
            val context = contextSlice(history.toList(), turns)
            runCatching { agentService.chat(context, stamped) }
                .onSuccess { result ->
                    history += ChatMessage(role = "user", content = stamped)
                    absorb(result)
                }
                .onFailure { fail(it) }
        }
    }

    /**
     * 取最近 turns 轮作为发给模型的上下文（轮以 user 消息开头）。
     * 按轮对齐保证不会把 tool 消息与其 assistant tool_calls 截断；
     * 历史不足 turns 轮时全量返回。
     */
    private fun contextSlice(history: List<ChatMessage>, turns: Int): List<ChatMessage> {
        if (turns <= 0 || history.isEmpty()) return emptyList()
        var seen = 0
        for (i in history.indices.reversed()) {
            if (history[i].role == "user") {
                seen++
                if (seen == turns) return history.subList(i, history.size)
            }
        }
        return history
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE", Locale.CHINA))

    /** 对话落盘：界面气泡与模型上下文分别序列化；从 user 消息起保留，封顶防膨胀。 */
    private fun persist() {
        val itemsJson = runCatching {
            json.encodeToString<List<ChatItem>>(_ui.value.items.takeLast(MAX_PERSIST_ITEMS))
        }.getOrNull() ?: return
        val historyJson = runCatching {
            json.encodeToString<List<ChatMessage>>(
                history.toList().takeLast(MAX_PERSIST_MESSAGES).dropWhile { it.role != "user" },
            )
        }.getOrNull() ?: return
        viewModelScope.launch {
            runCatching { agentChatStore.save(historyJson, itemsJson) }
        }
    }

    /** 并入一轮结果：历史消息、结果卡、确认卡、助手文案。 */
    private fun absorb(result: AgentChatResult) {
        history += result.messagesToAdd
        val newItems = mutableListOf<ChatItem>()
        result.assistantText?.takeIf { it.isNotBlank() }?.let { newItems += ChatItem.Assistant(it) }
        result.toolResults.forEach { tr -> newItems += ChatItem.Result(tr, formatResult(tr)) }
        result.pendingConfirm?.let { newItems += ChatItem.Confirm(it) }
        _ui.update { s ->
            s.copy(
                sending = false,
                items = s.items.dropLastWhile { it is ChatItem.Thinking } + newItems,
            )
        }
        persist()
    }

    private fun fail(e: Throwable) {
        _ui.update { s ->
            s.copy(
                sending = false,
                items = s.items.dropLastWhile { it is ChatItem.Thinking } +
                    ChatItem.Error(e.message ?: "请求失败"),
            )
        }
        persist()
    }

    fun confirm(index: Int, accept: Boolean) {
        val item = _ui.value.items.getOrNull(index) as? ChatItem.Confirm ?: return
        if (!accept) {
            viewModelScope.launch {
                // 取消也要按协议回 tool 消息，模型才能正常收尾（D4：CANCELLED，不写库）
                history += ChatMessage(
                    role = "tool",
                    content = """{"code":"CANCELLED","message":"用户已取消该操作"}""",
                    toolCallId = item.pending.toolCallId,
                )
                _ui.update { s ->
                    s.copy(
                        sending = true,
                        items = s.items.mapIndexed { i, c ->
                            if (i == index) ChatItem.Assistant("已取消操作") else c
                        },
                    )
                }
                persist()
                runCatching { agentService.continueChat(history.toList()) }
                    .onSuccess { absorb(it) }
                    .onFailure { fail(it) }
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(sending = true) }
            runCatching { agentService.executeConfirmed(item.pending) }
                .onSuccess { result ->
                    history += result.toToolMessage()
                    _ui.update { s ->
                        s.copy(
                            items = s.items.mapIndexed { i, c ->
                                if (i == index) ChatItem.Result(result, formatResult(result)) else c
                            },
                        )
                    }
                    persist()
                    // 工具结果回传模型，得到收尾文案（D4 §6）
                    runCatching { agentService.continueChat(history.toList()) }
                        .onSuccess { absorb(it) }
                        .onFailure { fail(it) }
                }
                .onFailure { e ->
                    _ui.update { s ->
                        s.copy(
                            sending = false,
                            items = s.items.mapIndexed { i, c ->
                                if (i == index) ChatItem.Error(e.message ?: "执行失败") else c
                            },
                        )
                    }
                    persist()
                }
        }
    }

    /** 结果卡「撤销」：按最近快照回滚，不经过模型（D4 §4）。 */
    fun undoLatest() {
        viewModelScope.launch {
            runCatching { undoService.latest() }
                .onSuccess { msg ->
                    _ui.update { s -> s.copy(items = s.items + ChatItem.Assistant(msg ?: "没有可撤销操作")) }
                    persist()
                }
                .onFailure { e ->
                    _ui.update { s -> s.copy(items = s.items + ChatItem.Error(e.message ?: "撤销失败")) }
                    persist()
                }
        }
    }

    private fun formatResult(r: ToolExecutionResult): String {
        if (!r.ok) return "失败：${r.message ?: r.dataJson}"
        return when (r.tool) {
            "create_event" -> "已创建日程（可撤销）"
            "create_todo" -> "已创建待办（可撤销）"
            "complete_todo" -> "已标记完成"
            "update_event", "update_todo" -> "已更新"
            "delete_event", "delete_todo", "delete_course" -> "已删除（可撤销）"
            "create_course" -> "已创建课程"
            else -> "已执行 ${r.tool}"
        }
    }

    companion object {
        private const val DEFAULT_CONTEXT_TURNS = 5
        private const val MAX_PERSIST_ITEMS = 200
        private const val MAX_PERSIST_MESSAGES = 200
    }
}
