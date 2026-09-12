package com.riqing.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riqing.core.data.repo.SemesterWithSlots

/**
 * 学期卡片列表页：每个已保存学期一张卡片（摘要 + 设为当前学期），
 * 点卡片进入编辑页，底部新建学期；所有课表都随卡片页上的当前学期切换。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SemesterListRoute(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    vm: SemesterListViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("学期与节次") },
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
            when {
                state.loading -> {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.items.isEmpty() -> {
                    Text(
                        "还没有学期，点按下方「新建学期」开始配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
                else -> {
                    state.items.forEach { item ->
                        SemesterCard(
                            withSlots = item,
                            onClick = { onEdit(item.semester.id) },
                            onSetCurrent = { vm.setCurrent(item.semester.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            AddSemesterCard(onClick = { onEdit(null) })
        }
    }
}

@Composable
private fun SemesterCard(
    withSlots: SemesterWithSlots,
    onClick: () -> Unit,
    onSetCurrent: () -> Unit,
) {
    val sem = withSlots.semester
    val isCurrent = sem.isCurrent
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sem.name.ifEmpty { "未命名学期" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            "当前学期",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${sem.week1Monday} ~ ${sem.endDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "共 ${withSlots.slots.size} 节" + if (withSlots.maxTeachingWeek > 0) " · ${withSlots.maxTeachingWeek} 个教学周" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设为当前学期", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = isCurrent,
                    onCheckedChange = { checked -> if (checked) onSetCurrent() },
                )
            }
        }
    }
}

@Composable
private fun AddSemesterCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("新建学期", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
    }
}
