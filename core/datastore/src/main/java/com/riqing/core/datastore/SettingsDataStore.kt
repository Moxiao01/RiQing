package com.riqing.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "riqing_settings")

data class AiSettings(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeoutSec: Int = 30,
    val temperature: Double? = 0.2,
    val enabled: Boolean = false,
)

/** 应用主题：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun ThemeMode.toLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

data class AppSettings(
    val ai: AiSettings = AiSettings(),
    val weekStartsOnMonday: Boolean = true,
    val exactAlarmRequested: Boolean = false,
    val agentPrivacyAck: Boolean = false,
    /** Agent 每次请求携带的最近对话轮数（1 轮 = 用户一条 + 助手回复及工具往返）。 */
    val agentContextTurns: Int = 5,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val endpoint = stringPreferencesKey("ai.endpointUrl")
        val apiKey = stringPreferencesKey("ai.apiKey")
        val model = stringPreferencesKey("ai.model")
        val timeout = intPreferencesKey("ai.timeoutSec")
        val temperature = doublePreferencesKey("ai.temperature")
        val aiEnabled = booleanPreferencesKey("ai.enabled")
        val weekStartsOn = stringPreferencesKey("ui.weekStartsOn")
        val exactAlarm = booleanPreferencesKey("notify.exactAlarmRequested")
        val privacyAck = booleanPreferencesKey("agent.privacyAck")
        val agentContextTurns = intPreferencesKey("agent.contextTurns")
        val themeMode = stringPreferencesKey("ui.themeMode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            AppSettings(
                ai = AiSettings(
                    endpointUrl = prefs[Keys.endpoint] ?: "",
                    apiKey = prefs[Keys.apiKey] ?: "",
                    model = prefs[Keys.model] ?: "",
                    timeoutSec = prefs[Keys.timeout] ?: 30,
                    temperature = prefs[Keys.temperature] ?: 0.2,
                    enabled = prefs[Keys.aiEnabled] ?: false,
                ),
                weekStartsOnMonday = (prefs[Keys.weekStartsOn] ?: "MONDAY") == "MONDAY",
                exactAlarmRequested = prefs[Keys.exactAlarm] ?: false,
                agentPrivacyAck = prefs[Keys.privacyAck] ?: false,
                agentContextTurns = (prefs[Keys.agentContextTurns] ?: 5).coerceIn(1, 20),
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.themeMode] ?: "SYSTEM")
                }.getOrDefault(ThemeMode.SYSTEM),
            )
        }

    suspend fun saveAi(ai: AiSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.endpoint] = ai.endpointUrl
            prefs[Keys.apiKey] = ai.apiKey
            prefs[Keys.model] = ai.model
            prefs[Keys.timeout] = ai.timeoutSec.coerceIn(10, 120)
            ai.temperature?.let { prefs[Keys.temperature] = it.coerceIn(0.0, 2.0) }
            prefs[Keys.aiEnabled] = ai.enabled
        }
    }

    suspend fun setAgentPrivacyAck(acked: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.privacyAck] = acked
        }
    }

    suspend fun setAgentContextTurns(turns: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.agentContextTurns] = turns.coerceIn(1, 20)
        }
    }

    suspend fun setExactAlarmRequested(requested: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.exactAlarm] = requested
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.themeMode] = mode.name
        }
    }
}
