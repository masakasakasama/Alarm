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
        // タイマーのキャンセルは通知操作。鳴動サービスを起こさず即時に予約解除する。
        if (action == AlarmIntents.ACTION_TIMER_CANCEL) {
            val timerId = intent.getIntExtra(AlarmIntents.EXTRA_TIMER_ID, -1)
            if (timerId >= 0) {
                runCatching { com.galaxyalarm.timer.TimerController.cancel(context, timerId) }
            }
            return
        }
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            this.action = if (action == AlarmIntents.ACTION_FIRE_BACKUP) {
                AlarmIntents.ACTION_FIRE
            } else {
                action
            }
            putExtras(intent)
            putExtra(AlarmIntents.EXTRA_BACKUP_FIRE, action == AlarmIntents.ACTION_FIRE_BACKUP)
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
