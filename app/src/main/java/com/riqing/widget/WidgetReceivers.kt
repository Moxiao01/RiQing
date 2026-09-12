package com.riqing.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riqing.core.data.repo.TodoRepository
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小组件刷新广播：边界闹钟、开机、时间/时区变化、应用升级都会走到这里，
 * 统一重算快照并推送全部小组件。
 */
@AndroidEntryPoint
class WidgetAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var loader: WidgetDataLoader

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { WidgetUpdater.refreshAll(appContext, loader) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 待办勾选广播：在小组件上直接完成/恢复待办，然后刷新小组件。 */
@AndroidEntryPoint
class TodoActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var todoRepository: TodoRepository
    @Inject
    lateinit var planner: ReminderPlanner
    @Inject
    lateinit var loader: WidgetDataLoader

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val todoId = intent.getStringExtra(EXTRA_ID) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val todo = todoRepository.get(todoId)
                    if (todo != null) {
                        if (todo.isDone) {
                            todoRepository.update(todoId, isDone = false)
                        } else {
                            todoRepository.complete(todoId)
                        }
                        runCatching { planner.replanEntity(ReminderPlanner.TYPE_TODO, todoId) }
                    }
                }
                runCatching { WidgetUpdater.refreshAll(appContext, loader) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.riqing.action.WIDGET_TODO_TOGGLE"
        const val EXTRA_ID = "todoId"
    }
}
