package com.riqing.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRoute(vm: AgentViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // 配置页保存后返回时，重读 AI 配置状态
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshConfigured()
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("Agent") },
                actions = {
                    // 对话已持久化，提供显式清空入口
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = vm::clearChat, enabled = !state.sending) {
                            Text("清空")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.privacyAck) {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("隐私说明", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("与 Agent 对话时，必要文本与工具结果会发送到你自配置的模型服务。本地日程数据仍仅存本机。")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.ackPrivacy() }) { Text("我已知晓") }
                    }
                }
            }
            if (!state.configured) {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有配置 AI", style = MaterialTheme.typography.titleMedium)
                        Text("需要自备 OpenAI 兼容模型 API 才能使用对话")
                        Spacer(Modifier.height(8.dp))
                        Text("示例：「明天下午三点图书馆还书」")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        Text("可以帮你增删改查日程、待办、课程。")
                        Text("例如：「今晚八点提醒我取快递」")
                    }
                }
                itemsIndexed(state.items) { index, item ->
                    when (item) {
                        is ChatItem.User -> Card {
                            Text("我：${item.text}", Modifier.padding(12.dp))
                        }
                        is ChatItem.Assistant -> Card {
                            Text(item.text, Modifier.padding(12.dp))
                        }
                        is ChatItem.Thinking -> Text(item.text, style = MaterialTheme.typography.bodySmall)
                        is ChatItem.Error -> Card {
                            Text(item.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                        }
                        is ChatItem.Confirm -> ConfirmCard(item) { ok -> vm.confirm(index, ok) }
                        is ChatItem.Result -> Card {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.display, style = MaterialTheme.typography.titleSmall)
                                Text(item.result.tool, style = MaterialTheme.typography.bodySmall)
                                if (item.result.ok && item.result.undoEligible) {
                                    TextButton(onClick = vm::undoLatest) { Text("撤销") }
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::setInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入指令…") },
                    singleLine = true,
                )
                Button(onClick = { vm.send() }, enabled = !state.sending) {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
private fun ConfirmCard(item: ChatItem.Confirm, onResult: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(12.dp)) {
            val risk = when (item.pending.risk) {
                com.riqing.core.ai.RiskLevel.HIGH -> "高风险"
                com.riqing.core.ai.RiskLevel.MEDIUM -> "需确认"
                com.riqing.core.ai.RiskLevel.LOW -> "低风险"
            }
            Text("将执行（$risk）", style = MaterialTheme.typography.titleSmall)
            Text(item.pending.summary)
            Text(item.pending.arguments, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onResult(false) }) { Text("取消") }
                Button(onClick = { onResult(true) }) { Text("确认") }
            }
        }
    }
}
