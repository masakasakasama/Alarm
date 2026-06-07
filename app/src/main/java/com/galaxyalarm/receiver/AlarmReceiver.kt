package com.galaxyalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.service.AlarmService

/**
 * exact alarm の発火、および通知の停止/スヌーズ操作を受け取り、
 * 鳴動 Foreground Service へ転送する。受信即座にサービス起動。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive: $action")
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = action
            putExtras(intent)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to start AlarmService", e)
        }
    }

    companion object { private const val TAG = "AlarmReceiver" }
}
