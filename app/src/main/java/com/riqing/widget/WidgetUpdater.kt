package com.riqing.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.riqing.R
import com.riqing.core.common.TimeUtils

/** 把 WidgetSnapshot 渲染为 RemoteViews 并推送到所有已添加的小组件。 */
object WidgetUpdater {

    suspend fun refreshAll(context: Context, loader: WidgetDataLoader) {
        val snapshot = loader.load()
        pushAll(context, snapshot)
        WidgetAlarms.scheduleNext(context, snapshot)
    }

    fun pushAll(context: Context, snapshot: WidgetSnapshot) {
        val manager = AppWidgetManager.getInstance(context)
        pushAgenda(context, manager, installedIds(context, manager, TodayAgendaWidgetProvider::class.java), snapshot)
        pushNext(context, manager, installedIds(context, manager, NextClassWidgetProvider::class.java), snapshot)
        pushTodo(context, manager, installedIds(context, manager, TodoListWidgetProvider::class.java), snapshot)
        pushWeek(context, manager, installedIds(context, manager, WeekTimetableWidgetProvider::class.java), snapshot)
    }

    fun pushAgenda(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_today_agenda)
        views.setTextViewText(R.id.widget_date_label, snapshot.dateLabel)
        if (snapshot.weekLabel == null) {
            views.setViewVisibility(R.id.widget_week_label, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_week_label, View.VISIBLE)
            views.setTextViewText(R.id.widget_week_label, snapshot.weekLabel)
        }
        views.removeAllViews(R.id.widget_rows)

        if (snapshot.agendaRows.isEmpty()) {
            views.setViewVisibility(R.id.widget_rows, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_rows, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            val openPi = openAppIntent(context)
            snapshot.agendaRows
                .take(WidgetDataLoader.MAX_LIST_ROWS)
                .forEach { row -> views.addView(R.id.widget_rows, agendaRowViews(context, row, openPi)) }
            val rest = snapshot.agendaRows.size - WidgetDataLoader.MAX_LIST_ROWS
            if (rest > 0) {
                views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_more,
                    context.getString(R.string.widget_more_prefix, rest),
                )
                views.setOnClickPendingIntent(R.id.widget_more, openPi)
            } else {
                views.setViewVisibility(R.id.widget_more, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_header, openPi)
        }
        manager.updateAppWidget(ids, views)
    }

    fun pushNext(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_next_class)
        val next = snapshot.next
        views.setTextViewText(R.id.next_status, next.statusText)
        views.setTextViewText(R.id.next_title, next.title)
        if (next.subtitle == null) {
            views.setViewVisibility(R.id.next_subtitle, View.GONE)
        } else {
            views.setViewVisibility(R.id.next_subtitle, View.VISIBLE)
            views.setTextViewText(R.id.next_subtitle, next.subtitle)
        }
        views.setOnClickPendingIntent(R.id.next_root, openAppIntent(context))
        manager.updateAppWidget(ids, views)
    }

    fun pushTodo(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_todo_list)
        val active = snapshot.todoActiveCount
        views.setTextViewText(
            R.id.widget_todo_count,
            if (active == 0 && snapshot.todoDoneCount > 0) {
                context.getString(R.string.widget_todo_all_done)
            } else if (active > 0) {
                context.getString(R.string.widget_todo_remaining, active)
            } else {
                ""
            },
        )
        views.removeAllViews(R.id.widget_rows)

        if (snapshot.todoRows.isEmpty()) {
            views.setViewVisibility(R.id.widget_rows, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_rows, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            snapshot.todoRows
                .take(WidgetDataLoader.MAX_LIST_ROWS)
                .forEach { row -> views.addView(R.id.widget_rows, todoRowViews(context, row)) }
            val rest = snapshot.todoRows.size - WidgetDataLoader.MAX_LIST_ROWS
            val openPi = openAppIntent(context)
            if (rest > 0) {
                views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_more,
                    context.getString(R.string.widget_more_prefix, rest),
                )
                views.setOnClickPendingIntent(R.id.widget_more, openPi)
            } else {
                views.setViewVisibility(R.id.widget_more, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_header, openPi)
        }
        manager.updateAppWidget(ids, views)
    }

    fun pushWeek(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        snapshot: WidgetSnapshot,
    ) {
        if (ids.isEmpty()) return
        val week = snapshot.week
        val views = RemoteViews(context.packageName, R.layout.widget_week_timetable)
        views.setTextViewText(R.id.widget_week_title, week.title)
        views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
        views.removeAllViews(R.id.widget_day_header)
        views.removeAllViews(R.id.widget_grid)

        if (week.rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_day_header, View.GONE)
            views.setViewVisibility(R.id.widget_grid, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, week.emptyText ?: context.getString(R.string.widget_week_no_course))
        } else {
            views.setViewVisibility(R.id.widget_day_header, View.VISIBLE)
            views.setViewVisibility(R.id.widget_grid, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            // 星期表头行：今天一列用主题色圆角高亮
            views.addView(
                R.id.widget_day_header,
                weekDayHeaderViews(
                    context,
                    week.dayLabels.map { label ->
                        WidgetWeekCell(label, emptyList(), null, WidgetWeekSegment.SINGLE)
                    },
                    week.todayColumn,
                ),
            )
            // 行数多时缩小字号，保证课名尽量完整显示
            val textSp = when {
                week.rows.size <= 8 -> 10f
                week.rows.size <= 12 -> 9f
                else -> 8f
            }
            week.rows.forEach { row ->
                views.addView(R.id.widget_grid, weekGridRowViews(context, row, week.todayColumn, textSp))
            }
        }
        manager.updateAppWidget(ids, views)
    }

    /** 星期表头行：今天一列用主题色圆角高亮。 */
    private fun weekDayHeaderViews(
        context: Context,
        cells: List<WidgetWeekCell>,
        todayColumn: Int,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_week_day_header_row).apply {
        setTextViewText(R.id.week_gutter, "")
        cells.take(WEEK_CELL_IDS.size).forEachIndexed { index, cell ->
            val cellId = WEEK_CELL_IDS[index]
            setTextViewText(cellId, cell.name)
            setInt(cellId, "setMaxLines", 1)
            if (index == todayColumn) {
                setInt(cellId, "setBackgroundResource", R.drawable.widget_day_header_today)
                setTextColor(cellId, context.getColor(R.color.widget_on_accent))
            } else {
                setInt(cellId, "setBackgroundResource", 0)
                setTextColor(cellId, context.getColor(R.color.widget_text_secondary))
            }
        }
    }

    /**
     * 课程网格行：节次号 + 7 列。
     * 课程块用预置圆角背景：单节四角圆角；连堂块首节上圆角、中段直角贴合、尾节下圆角，
     * 拼成一个整体圆角矩形；块与块之间靠背景 inset 留出缝隙。
     * 格子是容器，课程格里塞入 weekCourseCellViews 分行限行显示课名/教师/教室。
     */
    private fun weekGridRowViews(
        context: Context,
        row: WidgetWeekRow,
        todayColumn: Int,
        textSp: Float,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_week_grid_row).apply {
        setTextViewText(R.id.week_gutter, row.gutterText)
        row.cells.take(WEEK_CELL_IDS.size).forEachIndexed { index, cell ->
            val cellId = WEEK_CELL_IDS[index]
            when {
                // 课程块：圆角背景 + 课名（连堂块带教师/地点详情行）
                cell.colorToken != null -> {
                    setInt(
                        cellId,
                        "setBackgroundResource",
                        courseBlockDrawable(context, cell.colorToken, cell.segment),
                    )
                    removeAllViews(cellId)
                    addView(cellId, weekCourseCellViews(context, cell, textSp))
                }
                // 今天列的空格子铺一层浅色底，方便竖向对齐扫读
                index == todayColumn ->
                    setInt(cellId, "setBackgroundResource", R.drawable.widget_today_tint_cell)
                else -> setInt(cellId, "setBackgroundResource", 0)
            }
        }
    }

    /**
     * 课程格内部：课名与教师/教室详情行各占一条 TextView，分别限行并省略——
     * 课名最多 2 行、详情各 1 行（缩小字号），课名再长也不会折进详情行，
     * 保证教师/教室始终有固定位置显示。
     */
    private fun weekCourseCellViews(
        context: Context,
        cell: WidgetWeekCell,
        textSp: Float,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_week_cell_course).apply {
        setTextViewText(R.id.week_cell_name, cell.name)
        setTextViewTextSize(R.id.week_cell_name, TypedValue.COMPLEX_UNIT_SP, textSp)
        val detailSize = textSp * DETAIL_TEXT_SCALE
        cell.detailLines.take(2).forEachIndexed { index, line ->
            val detailId = if (index == 0) R.id.week_cell_detail1 else R.id.week_cell_detail2
            setTextViewText(detailId, line)
            setTextViewTextSize(detailId, TypedValue.COMPLEX_UNIT_SP, detailSize)
        }
    }

    /** 详情行（教师/教室）相对课名的字号比例。 */
    private const val DETAIL_TEXT_SCALE = 0.85f

    /** 8 种课程色 × 4 种连堂段位的圆角背景图（c1~c8 之外的颜色回落到 c1）。 */
    private fun courseBlockDrawable(context: Context, token: String, segment: WidgetWeekSegment): Int {
        val color = token.lowercase().trim().takeIf { it.matches(Regex("c[1-8]")) } ?: "c1"
        val suffix = when (segment) {
            WidgetWeekSegment.SINGLE -> "single"
            WidgetWeekSegment.TOP -> "top"
            WidgetWeekSegment.MIDDLE -> "mid"
            WidgetWeekSegment.BOTTOM -> "bottom"
        }
        val id = DrawablesRes.get(context, "widget_course_${color}_$suffix")
        return if (id != 0) id else DrawablesRes.get(context, "widget_course_c1_single")
    }

    /** 颜色 token 来自数据，资源 id 只能按名字运行时解析。 */
    private object DrawablesRes {
        fun get(context: Context, name: String): Int =
            context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private val WEEK_CELL_IDS = intArrayOf(
        R.id.week_cell1, R.id.week_cell2, R.id.week_cell3, R.id.week_cell4,
        R.id.week_cell5, R.id.week_cell6, R.id.week_cell7,
    )

    private fun agendaRowViews(context: Context, row: WidgetAgendaRow, openPi: PendingIntent): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_agenda_row).apply {
            setTextViewText(R.id.row_time, row.timeText)
            setTextViewText(R.id.row_title, row.title)
            setTextViewText(R.id.row_subtitle, row.subtitle.orEmpty())
            setInt(R.id.row_color_bar, "setColorFilter", row.colorArgb)
            setViewVisibility(R.id.row_badge, if (row.isOngoing) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.row_root, openPi)
        }

    private fun todoRowViews(context: Context, row: WidgetTodoRow): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_todo_row).apply {
            setTextViewText(R.id.todo_title, row.title)
            setTextColor(
                R.id.todo_title,
                context.getColor(if (row.isDone) R.color.widget_text_secondary else R.color.widget_on_surface),
            )
            setInt(
                R.id.todo_title,
                "setPaintFlags",
                if (row.isDone) {
                    Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
                } else {
                    Paint.ANTI_ALIAS_FLAG
                },
            )
            setImageViewResource(
                R.id.todo_check,
                if (row.isDone) R.drawable.widget_check_on else R.drawable.widget_check_off,
            )
            if (row.rightText == null) {
                setViewVisibility(R.id.todo_right, View.GONE)
            } else {
                setViewVisibility(R.id.todo_right, View.VISIBLE)
                setTextViewText(R.id.todo_right, row.rightText)
                setTextColor(
                    R.id.todo_right,
                    context.getColor(if (row.rightIsOverdue) R.color.widget_overdue else R.color.widget_text_secondary),
                )
            }
            setOnClickPendingIntent(R.id.todo_row_root, toggleTodoIntent(context, row.id))
        }

    /** 点击小组件打开 App 主界面。 */
    fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toggleTodoIntent(context: Context, todoId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            todoId.hashCode() and 0x7fffffff,
            Intent(context, TodoActionReceiver::class.java)
                .setAction(TodoActionReceiver.ACTION_TOGGLE)
                .putExtra(TodoActionReceiver.EXTRA_ID, todoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun installedIds(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<*>,
    ): IntArray = manager.getAppWidgetIds(ComponentName(context, provider)) ?: IntArray(0)
}

/** 在课节起止 / 明天首项 / 午夜等边界唤醒一次，保证「下节课」与跨天内容准确。 */
object WidgetAlarms {
    const val ACTION_WIDGET_REFRESH = "com.riqing.action.WIDGET_REFRESH"
    private const val REQUEST_CODE = 20010

    fun scheduleNext(context: Context, snapshot: WidgetSnapshot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = snapshot.nextBoundaryAt
        if (triggerAt <= TimeUtils.nowMillis()) return
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetAlarmReceiver::class.java).setAction(ACTION_WIDGET_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
