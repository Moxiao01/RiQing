package com.riqing.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 三个小组件的公共基类：onUpdate 时加载一次快照，然后各自渲染。
 * Hilt 会注入基类中的 loader 字段。
 */
@AndroidEntryPoint
abstract class RefreshableAppWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var loader: WidgetDataLoader

    protected abstract fun push(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: WidgetSnapshot,
    )

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { loader.load() }
                    .onSuccess { snapshot ->
                        WidgetAlarms.scheduleNext(context, snapshot)
                        push(context, manager, appWidgetIds, snapshot)
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 「今日日程」小组件：今天的课程与日程时间轴。 */
@AndroidEntryPoint
class TodayAgendaWidgetProvider : RefreshableAppWidgetProvider() {
    override fun push(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        WidgetUpdater.pushAgenda(context, manager, appWidgetIds, snapshot)
    }
}

/** 「下节课」小组件：正在进行 / 接下来的一节课。 */
@AndroidEntryPoint
class NextClassWidgetProvider : RefreshableAppWidgetProvider() {
    override fun push(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        WidgetUpdater.pushNext(context, manager, appWidgetIds, snapshot)
    }
}

/** 「今日待办」小组件：可直接在桌面勾选完成。 */
@AndroidEntryPoint
class TodoListWidgetProvider : RefreshableAppWidgetProvider() {
    override fun push(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        WidgetUpdater.pushTodo(context, manager, appWidgetIds, snapshot)
    }
}

/** 「本周课表」小组件：周一~周日 × 节次的课程网格。 */
@AndroidEntryPoint
class WeekTimetableWidgetProvider : RefreshableAppWidgetProvider() {
    override fun push(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        WidgetUpdater.pushWeek(context, manager, appWidgetIds, snapshot)
    }
}
