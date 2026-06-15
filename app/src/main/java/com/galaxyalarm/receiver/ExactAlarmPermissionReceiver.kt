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

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "exact alarm permission changed")
        ScheduleHealthWorker.runSoon(context, "exact-permission-changed")

        val app = context.applicationContext as AlarmApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.rescheduleAll("exact-permission-changed")
                val report = app.container.reliabilityChecker.runCheck()
                NextAlarmWidgetProvider.refresh(context)
                if (report.hasCritical) {
                    val issues = report.items.filter { !it.ok }.joinToString("、") { it.title }
                    NotificationHelper(context).showReliabilityWarning(
                        title = "アラーム権限を確認してください",
                        message = "正確なアラーム権限の変更後に要対応項目があります: $issues"
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "ExactAlarmPermReceiver" }
}
