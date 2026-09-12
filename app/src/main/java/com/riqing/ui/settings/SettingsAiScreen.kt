package com.riqing.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAiRoute(
    onDone: () -> Unit,
    vm: SettingsAiViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 配置") },
                navigationIcon = { TextButton(onClick = onDone) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "本 App 没有内置模型，需自备 OpenAI 兼容接口。Endpoint / Key / 模型三项都填才能启用 Agent。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                state.endpoint,
                vm::setEndpoint,
                label = { Text("端点 URL") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.apiKey,
                vm::setKey,
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.model,
                vm::setModel,
                label = { Text("模型") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.timeoutSec,
                vm::setTimeout,
                label = { Text("超时（秒 10-120）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                state.temperature,
                vm::setTemperature,
                label = { Text("Temperature（0-2）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Text("Agent 上下文轮数", style = MaterialTheme.typography.titleSmall)
            Text(
                "每次发送携带的最近对话轮数（1 轮 = 你一条 + 助手回复），改动即时生效",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { n ->
                    FilterChip(
                        selected = state.contextTurns == n,
                        onClick = { vm.setContextTurns(n) },
                        label = { Text("$n 轮") },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { vm.test() }, enabled = !state.testing, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.testing) "测试中…" else "连接测试")
            }
            state.testResult?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Key 仅存本机。协议：OpenAI 兼容 chat/completions。" +
                    "模型需支持 function calling / tools，否则 Agent 工具调用会失败。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
