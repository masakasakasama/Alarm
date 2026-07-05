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
import java.util.Calendar
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
            views.setTextViewText(R.id.widget_small_label, next?.let { remaining(it.time) } ?: "予定なし")
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
                    val groupName = group?.name ?: "グループなし"
                    val alarmLabel = alarm.label.trim()
                    WidgetAlarm(
                        alarmId = alarm.id,
                        time = NextTriggerCalculator.nextTrigger(alarm.hour, alarm.minute, alarm.weekdaysMask),
                        displayName = displayName(groupName, alarmLabel),
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
                views.setTextViewText(R.id.widget_time, next.clock)
                views.setTextViewText(
                    R.id.widget_label,
                    "${dayLabel(next.time)} ・ ${remaining(next.time)} ・ ${next.displayName}"
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAlarmIntent(context, next?.alarmId))
            return views
        }

        private fun openAlarmIntent(context: Context, alarmId: Long?): PendingIntent {
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

        private fun formatClock(hour: Int, minute: Int): String {
            val ampm = if (hour < 12) "AM" else "PM"
            val h12 = (hour % 12).let { if (it == 0) 12 else it }
            return String.format(Locale.JAPAN, "%d:%02d %s", h12, minute, ampm)
        }

        private fun displayName(groupName: String, alarmLabel: String): String =
            when {
                alarmLabel.isBlank() && groupName == "グループなし" -> "アラーム"
                alarmLabel.isBlank() -> groupName
                groupName == "グループなし" -> alarmLabel
                alarmLabel == groupName -> groupName
                else -> "$groupName / $alarmLabel"
            }

        private fun dayLabel(millis: Long, now: Long = System.currentTimeMillis()): String {
            val target = Calendar.getInstance().apply { timeInMillis = millis }
            val today = Calendar.getInstance().apply { timeInMillis = now }
            val diff = dayOfEpoch(target) - dayOfEpoch(today)
            return when (diff) {
                0L -> "今日"
                1L -> "明日"
                2L -> "明後日"
                else -> String.format(Locale.JAPAN, "%d/%d", target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH))
            }
        }

        private fun remaining(millis: Long, now: Long = System.currentTimeMillis()): String {
            val minutes = ((millis - now) / 60_000L).coerceAtLeast(0)
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) "あと${hours}時間${mins}分" else "あと${mins}分"
        }

        private fun dayOfEpoch(c: Calendar): Long {
            val z = c.clone() as Calendar
            z.set(Calendar.HOUR_OF_DAY, 0)
            z.set(Calendar.MINUTE, 0)
            z.set(Calendar.SECOND, 0)
            z.set(Calendar.MILLISECOND, 0)
            return z.timeInMillis / 86_400_000L
        }
    }
}

private data class WidgetAlarm(
    val alarmId: Long,
    val time: Long,
    val displayName: String,
    val clock: String,
)

/** 1x1 のコンパクト版「次のアラーム」ウィジェット(⏰+時刻のみ)。 */
class NextAlarmSmallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NextAlarmWidgetProvider.updateAllSmall(context, appWidgetManager, appWidgetIds)
    }
}
