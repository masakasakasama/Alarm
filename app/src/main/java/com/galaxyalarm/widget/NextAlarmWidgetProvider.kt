package com.galaxyalarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.galaxyalarm.MainActivity
import com.galaxyalarm.R
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.scheduler.NextTriggerCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class NextAlarmWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        /** 通常+1x1の両ウィジェットをまとめて更新する。 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidgetProvider::class.java))
            updateAll(context, manager, ids)
            val smallIds = manager.getAppWidgetIds(ComponentName(context, NextAlarmSmallWidgetProvider::class.java))
            updateAllSmall(context, manager, smallIds)
        }

        private fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val next = loadNextAlarm(context.applicationContext)
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildView(context, next))
                }
            }
        }

        internal fun updateAllSmall(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val next = loadNextAlarm(context.applicationContext)
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildSmallView(context, next))
                }
            }
        }

        private fun buildSmallView(context: Context, next: WidgetAlarm?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm_small)
            views.setTextViewText(R.id.widget_small_time, next?.clock ?: "--:--")
            views.setTextViewText(R.id.widget_small_label, next?.label ?: "予定なし")
            views.setOnClickPendingIntent(R.id.widget_small_root, openAlarmIntent(context, next?.alarmId))
            return views
        }

        private suspend fun loadNextAlarm(context: Context): WidgetAlarm? {
            val db = AppDatabase.get(context)
            val groups = db.groupDao().getAll().associateBy { it.id }
            return db.alarmDao().getAll()
                .filter { alarm -> alarm.enabled && groups[alarm.groupId]?.enabled == true }
                .map { alarm ->
                    val group = groups[alarm.groupId]
                    WidgetAlarm(
                        alarmId = alarm.id,
                        time = NextTriggerCalculator.nextTrigger(alarm.hour, alarm.minute, alarm.weekdaysMask),
                        label = alarm.label.ifBlank { group?.name ?: "アラーム" },
                        groupName = group?.name ?: "グループなし",
                        clock = formatClock(alarm.hour, alarm.minute)
                    )
                }
                .minByOrNull { it.time }
        }

        private fun buildView(context: Context, next: WidgetAlarm?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            views.setTextViewText(R.id.widget_title, "次のアラーム")
            if (next == null) {
                views.setTextViewText(R.id.widget_time, "予定なし")
                views.setTextViewText(R.id.widget_label, "アラームを追加してください")
            } else {
                views.setTextViewText(R.id.widget_time, formatDateTime(next.time))
                views.setTextViewText(R.id.widget_label, "${next.clock} ・ ${next.groupName} ・ ${next.label}")
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAlarmIntent(context, next?.alarmId))
            return views
        }

        private fun openAlarmIntent(context: Context, alarmId: Long?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                alarmId?.let { putExtra(MainActivity.EXTRA_OPEN_ALARM_ID, it) }
            }
            return PendingIntent.getActivity(
                context,
                1001 + (alarmId?.toInt() ?: 0),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun formatDateTime(millis: Long): String =
            SimpleDateFormat("M/d h:mm a", Locale.JAPAN).format(millis)

        private fun formatClock(hour: Int, minute: Int): String {
            val ampm = if (hour < 12) "AM" else "PM"
            val h12 = (hour % 12).let { if (it == 0) 12 else it }
            return String.format(Locale.JAPAN, "%d:%02d %s", h12, minute, ampm)
        }
    }
}

private data class WidgetAlarm(
    val alarmId: Long,
    val time: Long,
    val label: String,
    val groupName: String,
    val clock: String,
)

/** 1x1 のコンパクト版「次のアラーム」ウィジェット(⏰+時刻のみ)。 */
class NextAlarmSmallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NextAlarmWidgetProvider.updateAllSmall(context, appWidgetManager, appWidgetIds)
    }
}
