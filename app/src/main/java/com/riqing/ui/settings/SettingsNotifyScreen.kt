package com.riqing.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.riqing.core.notify.ReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNotifyRoute(
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scheduler = remember { ReminderScheduler(context, com.riqing.core.notify.NotificationChannelManager(context)) }
    var notifyGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifyGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提醒与通知引导") },
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("系统权限", style = MaterialTheme.typography.titleMedium)
                    ListItem(
                        headlineContent = { Text("通知权限") },
                        supportingContent = { Text(if (notifyGranted) "已允许" else "未允许，到点不会提醒") },
                        trailingContent = {
                            if (!notifyGranted) {
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }) { Text("去开启") }
                            }
                        },
                    )
                    ListItem(
                        headlineContent = { Text("精确闹钟") },
                        supportingContent = {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val am = context.getSystemService(android.app.AlarmManager::class.java)
                                    if (am.canScheduleExactAlarms()) "可用" else "不可用，可能延迟"
                                } else "系统版本无需申请",
                            )
                        },
                        trailingContent = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                                    )
                                }) { Text("去设置") }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("厂商后台引导", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "小米/华为/OPPO/vivo/荣耀等需允许自启动、关闭电池优化、锁定后台，" +
                            "否则杀进程后可能漏提醒。请到系统设置中为「日清」开启。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("1. 允许自启动", style = MaterialTheme.typography.bodySmall)
                    Text("2. 省电策略设为无限制 / 允许后台运行", style = MaterialTheme.typography.bodySmall)
                    Text("3. 最近任务锁定应用（可选）", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { scheduler.sendTestNow("日清测试通知", "如果你能看到，说明通知渠道正常") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("发送测试通知（现在）") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val trigger = System.currentTimeMillis() + 10_000
                    scheduler.schedule(
                        ReminderSpecSafe(
                            title = "日清测试提醒",
                            text = "10 秒调度路径验证",
                            triggerAtMillis = trigger,
                        ).toSpec(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("调度 10 秒后的测试提醒") }
        }
    }
}

private data class ReminderSpecSafe(
    val title: String,
    val text: String,
    val triggerAtMillis: Long,
)

private fun ReminderSpecSafe.toSpec() = com.riqing.core.notify.ReminderSpec(
    notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    channelId = com.riqing.core.notify.NotificationChannels.EVENT_REMINDER,
    title = title,
    text = text,
    triggerAtMillis = triggerAtMillis,
    contentIntentAction = "com.riqing.action.OPEN_HOME",
    extraEntityId = "test",
    extraEntityType = "test",
)
