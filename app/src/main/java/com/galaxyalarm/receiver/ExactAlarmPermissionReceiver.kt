package com.galaxyalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyalarm.AlarmApplication
import kotlinx.coroutines.launch

/**
 * Android 12+: exact alarm 権限の状態が変わったら全再スケジュール。
 * 付与時は予約復元、取消時は再構築試行(失敗はログに残す)。
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "exact alarm permission changed")
        val app = context.applicationContext as AlarmApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.repository.rescheduleAll("exact-permission-changed")
                app.container.reliabilityChecker.runCheck()
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "ExactAlarmPermReceiver" }
}
