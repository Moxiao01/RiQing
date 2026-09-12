package com.riqing

import android.app.Application
import androidx.room.InvalidationTracker
import com.riqing.core.database.RiQingDatabase
import com.riqing.core.notify.NotificationChannelManager
import com.riqing.core.notify.ReminderPlanner
import com.riqing.widget.WidgetDataLoader
import com.riqing.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RiQingApp : Application() {
    @Inject lateinit var channelManager: NotificationChannelManager
    @Inject lateinit var reminderPlanner: ReminderPlanner
    @Inject lateinit var widgetDataLoader: WidgetDataLoader
    @Inject lateinit var database: RiQingDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var widgetRefreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        channelManager.ensureChannels()
        // 启动时按当前数据重排提醒（覆盖改期/删除/过期后的残留闹钟）
        appScope.launch { runCatching { reminderPlanner.replanAll() } }

        // 小组件：启动即刷新，并在课程/日程/待办等数据变化时自动同步到桌面
        scheduleWidgetRefresh()
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                "courses", "events", "event_exceptions", "semesters", "period_slots", "todos",
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    scheduleWidgetRefresh()
                }
            },
        )
    }

    private fun scheduleWidgetRefresh() {
        widgetRefreshJob?.cancel()
        widgetRefreshJob = appScope.launch {
            delay(400)
            runCatching { WidgetUpdater.refreshAll(applicationContext, widgetDataLoader) }
        }
    }
}
