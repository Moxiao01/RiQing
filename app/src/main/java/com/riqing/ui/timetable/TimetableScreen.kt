package com.riqing.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.riqing.core.designsystem.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.riqing.core.common.TimeUtils
import com.riqing.core.designsystem.courseColor
import com.riqing.core.model.Course

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableRoute(
    onOpenCourse: (String) -> Unit,
    onAddCourse: () -> Unit,
    vm: TimetableViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // 底栏 restoreState 会保留 ViewModel；回到本页时重新拉取，避免加课后列表仍过期
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh()
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("课表") },
                navigationIcon = {
                    IconButton(onClick = { vm.shiftWeek(-1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周")
                    }
                },
                actions = {
                    if (!state.isCurrentWeek) {
                        TextButton(onClick = { vm.backToCurrentWeek() }) {
                            Text("本周")
                        }
                    }
                    IconButton(onClick = { vm.shiftWeek(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下一周")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCourse) {
                Icon(Icons.Filled.Add, contentDescription = "加课")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "第 ${state.teachingWeek} 教学周 · ${state.semester?.semester?.name ?: "未配置学期"}",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.semester == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚未配置学期与节次")
                    Spacer(Modifier.height(8.dp))
                    Text("到「我的 → 学期与节次」配置后即可显示课表")
                }
                return@Column
            }
            val week = state.teachingWeek
            val visibleCount = (1..7).sumOf { vm.coursesOn(it, week).size }
            if (visibleCount == 0) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.courses.isEmpty()) "本周还没有课程，点右下角 + 添加"
                        else "第 ${state.teachingWeek} 教学周没有课程（可左右切换周次）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (weekday in 1..7) {
                    val list = vm.coursesOn(weekday, week)
                    if (list.isEmpty()) continue
                    item(key = "h$weekday") {
                        Text(
                            "周${TimeUtils.weekdayChinese(weekday)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(list, key = { "${it.id}-w$weekday" }) { course ->
                        CourseCard(course, onClick = { onOpenCourse(course.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(courseColor(course.colorToken)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        course.effectiveWeekdays
                            .joinToString("/") { "周${TimeUtils.weekdayChinese(it)}" }
                            .takeIf { course.effectiveWeekdays.size > 1 },
                        "第${course.periodStart}-${course.periodEnd}节",
                        course.location,
                        course.teacher,
                        if (course.isBiweekly) (if (course.biweeklyOdd == true) "单周" else "双周") else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
