package com.riqing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeRoute(
    onAi: () -> Unit,
    onSemester: () -> Unit,
    onImport: () -> Unit,
    onNotify: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("我的") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("AI 配置") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAi),
            )
            ListItem(
                headlineContent = { Text("学期与节次") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSemester),
            )
            ListItem(
                headlineContent = { Text("一键导入课程") },
                supportingContent = { Text("粘贴文字或截图识别 · 离线解析") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onImport),
            )
            ListItem(
                headlineContent = { Text("提醒与通知引导") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNotify),
            )
            ListItem(
                headlineContent = { Text("关于与隐私") },
                supportingContent = { Text("本地优先 · 数据仅存本机 · Agent 需自备 API") },
            )
        }
    }
}
