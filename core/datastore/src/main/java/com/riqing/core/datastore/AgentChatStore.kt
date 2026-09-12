package com.riqing.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Agent 对话持久化：与设置分开的独立 DataStore 文件，避免聊天记录体积
 * 影响全局设置读取。内容为调用方序列化好的 JSON 字符串，本类不感知消息结构。
 */
private val Context.agentChatDataStore by preferencesDataStore(name = "agent_chat")

class AgentChatStore(private val context: Context) {

    private object Keys {
        val history = stringPreferencesKey("history")
        val items = stringPreferencesKey("items")
    }

    data class SavedChat(val historyJson: String?, val itemsJson: String?)

    suspend fun load(): SavedChat {
        val prefs = try {
            context.agentChatDataStore.data.first()
        } catch (_: IOException) {
            return SavedChat(null, null)
        }
        return SavedChat(prefs[Keys.history], prefs[Keys.items])
    }

    suspend fun save(historyJson: String, itemsJson: String) {
        try {
            context.agentChatDataStore.edit { prefs ->
                prefs[Keys.history] = historyJson
                prefs[Keys.items] = itemsJson
            }
        } catch (_: IOException) {
            // 持久化失败不影响当次对话，仅下次启动无法恢复
        }
    }
}
