package com.galaxyalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.reliability.ScheduleHealthWorker
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        Log.i(TAG, "system event: $action")
        val app = context.applicationContext as AlarmApplication
        app.initializeAvailable()
        val container = app.containerOrNull() ?: return
        container.reliabilityStore.lastSystemEvent = action
        container.reliabilityStore.lastSystemEventAt = System.currentTimeMillis()
        val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
        if (unlocked) {
            ScheduleHealthWorker.schedule(context)
            runCatching { com.galaxyalarm.timer.TimerController.init(context) }
        }

        val pending = goAsync()
        app.appScope.launch {
            try {
                if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                    container.repository.recalculateRegularAlarms("system:$action")
                } else {
                    container.repository.rescheduleAll("system:$action")
                }
                val report = if (unlocked) container.reliabilityChecker.runCheck() else null
                if (unlocked) NextAlarmWidgetProvider.refresh(context)
                if (report?.hasCritical == true) {
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

    companion object {
        private const val TAG = "SystemEventReceiver"
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_UNLOCKED,
        )
    }
}
