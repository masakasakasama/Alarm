package com.galaxyalarm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import com.galaxyalarm.MainActivity
import com.galaxyalarm.R
import com.galaxyalarm.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class NextAlarmWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH_AT_BOUNDARY) {
            val pendingResult = goAsync()
            refresh(context) { pendingResult.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 通常+1x1の両ウィジェットをまとめて更新する。 */
        fun refresh(context: Context, onComplete: (() -> Unit)? = null) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(context) ?: run {
                onComplete?.invoke()
                return
            }
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidgetProvider::class.java))
            val smallIds = manager.getAppWidgetIds(ComponentName(context, NextAlarmSmallWidgetProvider::class.java))
            if (ids.isEmpty() && smallIds.isEmpty()) {
                cancelBoundaryRefresh(appContext)
                onComplete?.invoke()
                return
            }

            widgetScope.launch {
                try {
                    val next = loadNextAlarm(appContext)
                    ids.forEach { id ->
                        manager.updateAppWidget(id, buildView(appContext, next))
                    }
                    smallIds.forEach { id ->
                        manager.updateAppWidget(id, buildSmallView(appContext, next))
                    }
                    scheduleBoundaryRefresh(appContext, next?.time)
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to refresh next alarm widget", error)
                } finally {
                    onComplete?.invoke()
                }
            }
        }

        private fun buildSmallView(context: Context, next: WidgetAlarm?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm_small_v2)
            views.setTextViewText(R.id.widget_small_time, next?.clock ?: "--:--")
            if (next == null) {
                views.setChronometerCountDown(R.id.widget_small_label, false)
                views.setChronometer(R.id.widget_small_label, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.widget_small_label, "予定なし")
            } else {
                val countdownBase = SystemClock.elapsedRealtime() +
                    (next.time - System.currentTimeMillis()).coerceAtLeast(0L)
                views.setChronometer(R.id.widget_small_label, countdownBase, "あと%s", true)
                views.setChronometerCountDown(R.id.widget_small_label, true)
            }
            views.setOnClickPendingIntent(R.id.widget_small_root, openAlarmIntent(context, next?.alarmId))
            return views
        }

        private suspend fun loadNextAlarm(context: Context): WidgetAlarm? {
            val db = AppDatabase.get(context)
            val candidate = selectNextWidgetAlarm(
                alarms = db.alarmDao().getAll(),
                groups = db.groupDao().getAll(),
                occurrences = db.occurrenceDao().getAllScheduled(),
                now = System.currentTimeMillis(),
            ) ?: return null

            return WidgetAlarm(
                alarmId = candidate.alarm.id,
                time = candidate.triggerAtMillis,
                displayName = displayName(candidate.groupName, candidate.alarm.label.trim()),
                clock = formatClock(candidate.triggerAtMillis),
            )
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
                    "${dayLabel(next.time)} ・ ${next.displayName}"
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

        private fun formatClock(millis: Long): String {
            val time = Calendar.getInstance().apply { timeInMillis = millis }
            val hour = time.get(Calendar.HOUR_OF_DAY)
            val minute = time.get(Calendar.MINUTE)
            val ampm = if (hour < 12) "AM" else "PM"
            val h12 = (hour % 12).let { if (it == 0) 12 else it }
            return String.format(Locale.JAPAN, "%d:%02d %s", h12, minute, ampm)
        }

        private fun scheduleBoundaryRefresh(context: Context, triggerAtMillis: Long?) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = boundaryRefreshIntent(context)
            alarmManager.cancel(pendingIntent)
            if (triggerAtMillis == null) return

            val refreshAt = maxOf(
                triggerAtMillis + BOUNDARY_REFRESH_DELAY_MS,
                System.currentTimeMillis() + MIN_REFRESH_DELAY_MS,
            )
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        refreshAt,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        refreshAt,
                        pendingIntent,
                    )
                }
            }
        }

        private fun cancelBoundaryRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarmManager.cancel(boundaryRefreshIntent(context))
        }

        private fun boundaryRefreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, NextAlarmWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_AT_BOUNDARY
            }
            return PendingIntent.getBroadcast(
                context,
                BOUNDARY_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
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

        private fun dayOfEpoch(c: Calendar): Long {
            val z = c.clone() as Calendar
            z.set(Calendar.HOUR_OF_DAY, 0)
            z.set(Calendar.MINUTE, 0)
            z.set(Calendar.SECOND, 0)
            z.set(Calendar.MILLISECOND, 0)
            return z.timeInMillis / 86_400_000L
        }

        private const val ACTION_REFRESH_AT_BOUNDARY =
            "com.galaxyalarm.widget.action.REFRESH_AT_ALARM_BOUNDARY"
        private const val BOUNDARY_REFRESH_REQUEST_CODE = 810_001
        private const val BOUNDARY_REFRESH_DELAY_MS = 1_000L
        private const val MIN_REFRESH_DELAY_MS = 250L
        private const val TAG = "NextAlarmWidget"
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
        NextAlarmWidgetProvider.refresh(context)
    }
}
