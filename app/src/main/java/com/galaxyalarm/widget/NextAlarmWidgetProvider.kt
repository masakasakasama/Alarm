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
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidgetProvider::class.java))
            updateAll(context, manager, ids)
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

        private suspend fun loadNextAlarm(context: Context): WidgetAlarm? {
            val db = AppDatabase.get(context)
            val groups = db.groupDao().getAll().associateBy { it.id }
            return db.alarmDao().getAll()
                .filter { alarm -> alarm.enabled && groups[alarm.groupId]?.enabled == true }
                .map { alarm ->
                    val group = groups[alarm.groupId]
                    WidgetAlarm(
                        time = NextTriggerCalculator.nextTrigger(alarm.hour, alarm.minute, alarm.weekdaysMask),
                        label = alarm.label.ifBlank { group?.name ?: "アラーム" },
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
                views.setTextViewText(R.id.widget_label, "${next.clock} ・ ${next.label}")
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                1001,
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
    val time: Long,
    val label: String,
    val clock: String,
)
