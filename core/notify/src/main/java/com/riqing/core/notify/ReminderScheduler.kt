package com.riqing.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.riqing.core.common.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderSpec(
    val notificationId: Int,
    val requestCode: Int,
    val channelId: String,
    val title: String,
    val text: String,
    val triggerAtMillis: Long,
    val contentIntentAction: String,
    val extraEntityId: String,
    val extraEntityType: String,
)

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(spec: ReminderSpec) {
        channelManager.ensureChannels()
        if (spec.triggerAtMillis <= TimeUtils.nowMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_TITLE, spec.title)
            putExtra(ReminderReceiver.EXTRA_TEXT, spec.text)
            putExtra(ReminderReceiver.EXTRA_CHANNEL, spec.channelId)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, spec.notificationId)
            putExtra(ReminderReceiver.EXTRA_ENTITY_ID, spec.extraEntityId)
            putExtra(ReminderReceiver.EXTRA_ENTITY_TYPE, spec.extraEntityType)
            putExtra(ReminderReceiver.EXTRA_CONTENT_ACTION, spec.contentIntentAction)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            spec.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.triggerAtMillis, pi)
        }
    }

    fun cancel(requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    fun sendTestNow(title: String, text: String) {
        channelManager.ensureChannels()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_CHANNEL, NotificationChannels.EVENT_REMINDER)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
        }
        context.sendBroadcast(intent)
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var channelManager: NotificationChannelManager
    @Inject lateinit var planner: ReminderPlanner

    override fun onReceive(context: Context, intent: Intent) {
        channelManager.ensureChannels()
        when (intent.action) {
            ACTION_SHOW, ACTION_REMINDER -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "日清提醒"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationChannels.EVENT_REMINDER
                val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1)
                val contentAction = intent.getStringExtra(EXTRA_CONTENT_ACTION)
                val entityId = intent.getStringExtra(EXTRA_ENTITY_ID)
                val entityType = intent.getStringExtra(EXTRA_ENTITY_TYPE)

                val contentIntent = contentAction?.let { action ->
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        this.action = action
                        putExtra("entityId", entityId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
                val pendingContent = contentIntent?.let {
                    android.app.PendingIntent.getActivity(
                        context,
                        id,
                        it,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                }

                val notification = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .apply { pendingContent?.let { setContentIntent(it) } }
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(id, notification)
                } catch (_: SecurityException) {
                    // 无通知权限
                }

                // 真实提醒触发后，为同一实体排下一个偏移/下一次课（重复与多偏移场景）
                if (intent.action == ACTION_REMINDER && entityType != null && entityId != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            planner.replanEntity(entityType, entityId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.riqing.action.REMINDER"
        const val ACTION_SHOW = "com.riqing.action.SHOW"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_NOTIFICATION_ID = "nid"
        const val EXTRA_ENTITY_ID = "entityId"
        const val EXTRA_ENTITY_TYPE = "entityType"
        const val EXTRA_CONTENT_ACTION = "contentAction"
    }
}
