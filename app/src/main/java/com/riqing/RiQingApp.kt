package com.riqing

import android.app.Application
import com.riqing.core.notify.NotificationChannelManager
import com.riqing.core.notify.ReminderPlanner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RiQingApp : Application() {
    @Inject lateinit var channelManager: NotificationChannelManager
    @Inject lateinit var reminderPlanner: ReminderPlanner

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        channelManager.ensureChannels()
        // 启动时按当前数据重排提醒（覆盖改期/删除/过期后的残留闹钟）
        appScope.launch { runCatching { reminderPlanner.replanAll() } }
    }
}
