package com.riqing.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 一键导入课程：粘贴课表文字或选择截图（端侧离线 OCR），规则解析出课程后
 * 勾选预览并批量导入当前学期；全程不需要联网与大模型。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseImportRoute(
    onBack: () -> Unit,
    onOpenSemester: () -> Unit,
    vm: CourseImportViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(vm::onImagePicked) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("一键导入课程") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imeNestedScroll()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SemesterBanner(state, onOpenSemester)

            Text(
                "第 1 步 · 填入课表文字",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::loadSample) { Text("填入示例") }
                OutlinedButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.ocrRunning,
                ) { Text("选截图识别") }
            }
            if (state.ocrRunning) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在识别截图（端侧离线 OCR）…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.ocrError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.importText,
                onValueChange = vm::setText,
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                placeholder = {
                    Text(
                        "每行一门课，例如：\n高等数学 星期一 1-2节 1-16周 教1-201 张伟",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "支持：周一/星期一 · 1-2节/第1-2节 · 1-16周 · 单周/双周；教室、教师自动识别。" +
                    "截图识别后请检查补全缺失的星期与节次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::parse,
                enabled = state.importText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("解析预览") }

            if (state.failedLines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FailedLinesCard(state.failedLines)
            }

            if (state.preview.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "第 2 步 · 勾选要导入的课程",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                state.preview.forEachIndexed { index, item ->
                    PreviewCard(item = item, onToggle = { vm.toggle(index) })
                    Spacer(Modifier.height(8.dp))
                }
                val selectable = state.preview.count { it.error == null }
                val picked = state.preview.count { it.selected && it.error == null }
                Text(
                    "已选 $picked / $selectable 门（标红的暂不能导入）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::importSelected,
                    enabled = !state.importing && picked > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.importing) "导入中…" else "导入所选（$picked 门）") }
            }

            state.result?.let { result ->
                Spacer(Modifier.height(12.dp))
                ResultCard(result, onDone = onBack)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SemesterBanner(state: CourseImportUiState, onOpenSemester: () -> Unit) {
    when {
        state.loading -> {}
        state.maxWeek <= 0 -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "还没有可用的学期与节次",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "请先到「学期与节次」配置当前学期，课程会导入到当前学期。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSemester) { Text("去配置学期") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        else -> {
            Text(
                "将导入到当前学期：${state.semesterName}（共 ${state.maxWeek} 个教学周）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FailedLinesCard(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "有 ${lines.size} 行没有识别出星期或节次：",
                style = MaterialTheme.typography.titleSmall,
            )
            lines.take(3).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lines.size > 3) {
                Text(
                    "…等 ${lines.size} 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "请为这些行补上「星期X X-X节」，再次解析。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreviewCard(item: ImportPreviewItem, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.selected && item.error == null,
                onCheckedChange = { onToggle() },
                enabled = item.error == null,
            )
            Column(Modifier.weight(1f).padding(end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("星期")
                        append(weekdayLabel(item.weekdays))
                        append(" · 第${item.periodStart}-${item.periodEnd}节 · ")
                        append(item.weeksSpec.ifBlank { "整学期" })
                        if (item.isBiweekly) {
                            append(if (item.biweeklyOdd == true) "（单周）" else "（双周）")
                        }
                        item.location?.let { append(" · $it") }
                        item.teacher?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.error?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ImportResult, onDone: () -> Unit) {
    val ok = result.failed == 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (ok) "已导入 ${result.success} 门课程 ✓" else "导入完成：成功 ${result.success} 门，失败 ${result.failed} 门",
                style = MaterialTheme.typography.titleSmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            if (result.messages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                result.messages.take(3).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone) { Text("完成") }
        }
    }
}

private fun weekdayLabel(days: List<Int>): String {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return days.joinToString("、") { names.getOrNull(it - 1) ?: "?" }
}
