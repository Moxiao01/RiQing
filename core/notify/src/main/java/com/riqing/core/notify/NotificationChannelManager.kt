package com.riqing.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                NotificationChannels.EVENT_REMINDER,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "课程/日程开始前提醒" },
            NotificationChannel(
                NotificationChannels.TODO_DUE,
                "待办截止",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "待办截止提醒" },
            NotificationChannel(
                NotificationChannels.AGENT_RESULT,
                "Agent 结果",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Agent 长任务结果通知" },
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }
}
