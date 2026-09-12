package com.riqing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riqing.core.datastore.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemeRoute(
    onDone: () -> Unit,
    vm: SettingsThemeViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("外观") },
                navigationIcon = { TextButton(onClick = onDone) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "主题",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    ThemeOption(
                        title = "跟随系统",
                        subtitle = "随系统深浅色设置自动切换",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    ThemeOption(
                        title = "浅色",
                        subtitle = "固定使用白色主题",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                    )
                    ThemeOption(
                        title = "深色",
                        subtitle = "固定使用黑色主题",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { vm.setThemeMode(ThemeMode.DARK) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "切换立即生效并保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
