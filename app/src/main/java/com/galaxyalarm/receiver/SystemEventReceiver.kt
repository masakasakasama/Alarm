package com.galaxyalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.reliability.ScheduleHealthWorker
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "system event: $action")
        ScheduleHealthWorker.runSoon(context, "system:$action")
        // 端末再起動などで失われたタイマー予約を復元。
        runCatching { com.galaxyalarm.timer.TimerController.init(context) }

        val app = context.applicationContext as AlarmApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.rescheduleAll("system:$action")
                val report = app.container.reliabilityChecker.runCheck()
                NextAlarmWidgetProvider.refresh(context)
                if (report.hasCritical) {
                    val issues = report.items.filter { !it.ok }.joinToString("、") { it.title }
                    NotificationHelper(context).showReliabilityWarning(
                        title = "アラーム設定を確認してください",
                        message = "OS更新または端末再起動後に要対応項目があります: $issues"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "SystemEventReceiver" }
}
