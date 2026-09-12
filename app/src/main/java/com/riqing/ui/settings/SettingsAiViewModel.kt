package com.riqing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.ai.OpenAiClient
import com.riqing.core.datastore.AiSettings
import com.riqing.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeoutSec: String = "30",
    val temperature: String = "0.2",
    val contextTurns: Int = 5,
    val testing: Boolean = false,
    val testResult: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsAiViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val client: OpenAiClient,
) : ViewModel() {
    private val _ui = MutableStateFlow(AiSettingsUiState())
    val ui: StateFlow<AiSettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsDataStore.settings.first()
            val ai = s.ai
            _ui.update {
                it.copy(
                    endpoint = ai.endpointUrl,
                    apiKey = ai.apiKey,
                    model = ai.model,
                    timeoutSec = ai.timeoutSec.toString(),
                    temperature = ai.temperature?.toString() ?: "0.2",
                    contextTurns = s.agentContextTurns,
                )
            }
        }
    }

    fun setEndpoint(v: String) = _ui.update { it.copy(endpoint = v) }
    fun setKey(v: String) = _ui.update { it.copy(apiKey = v) }
    fun setModel(v: String) = _ui.update { it.copy(model = v) }
    fun setTimeout(v: String) = _ui.update { it.copy(timeoutSec = v) }
    fun setTemperature(v: String) = _ui.update { it.copy(temperature = v) }

    /** 上下文轮数即时生效，无需按「保存配置」。 */
    fun setContextTurns(turns: Int) {
        _ui.update { it.copy(contextTurns = turns) }
        viewModelScope.launch {
            settingsDataStore.setAgentContextTurns(turns)
        }
    }

    private fun current(): AiSettings = AiSettings(
        endpointUrl = _ui.value.endpoint.trim(),
        apiKey = _ui.value.apiKey.trim(),
        model = _ui.value.model.trim(),
        timeoutSec = _ui.value.timeoutSec.toIntOrNull() ?: 30,
        temperature = _ui.value.temperature.toDoubleOrNull(),
        enabled = _ui.value.endpoint.isNotBlank() &&
            _ui.value.apiKey.isNotBlank() &&
            _ui.value.model.isNotBlank(),
    )

    fun save() {
        viewModelScope.launch {
            settingsDataStore.saveAi(current())
            _ui.update { it.copy(saved = true) }
        }
    }

    fun test() {
        viewModelScope.launch {
            _ui.update { it.copy(testing = true, testResult = null) }
            val result = client.connectionTest(current())
            _ui.update {
                it.copy(
                    testing = false,
                    testResult = result.fold(
                        onSuccess = { ms -> "成功 · ${ms}ms" },
                        onFailure = { e -> "失败：${e.message}" },
                    ),
                )
            }
        }
    }
}
