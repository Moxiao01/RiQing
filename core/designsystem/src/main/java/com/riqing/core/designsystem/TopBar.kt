package com.riqing.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 顶栏向上探入状态栏的深度：标题紧贴系统时钟，消除时钟与标题之间的整段留白。 */
private val StatusBarOverlap = 12.dp

/**
 * 紧凑顶栏：48dp 高（系统 TopAppBar 固定 64dp，顶部留白过大），
 * 且整体上探 StatusBarOverlap 进入状态栏区域。内部复用 TopAppBar 保证样式一致，
 * 参数与系统 TopAppBar 对齐，可直接替换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val topPadding = with(density) {
        (WindowInsets.statusBars.getTop(density) - StatusBarOverlap.toPx())
            .coerceAtLeast(0f)
            .toDp()
    }
    Box(modifier.fillMaxWidth().padding(top = topPadding)) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            windowInsets = WindowInsets(0.dp),
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}
