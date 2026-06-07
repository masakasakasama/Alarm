package com.galaxyalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyalarm.AlarmApplication
import kotlinx.coroutines.launch

/**
 * 再起動・アプリ更新・時刻/TZ変更で全アラームを再構築する。
 * いずれも「予約が消える可能性のある」イベント。
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "system event: $action")
        val app = context.applicationContext as AlarmApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.rescheduleAll("system:$action")
                app.container.reliabilityChecker.runCheck()
            } catch (e: Exception) {
                Log.e(TAG, "reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "SystemEventReceiver" }
}
