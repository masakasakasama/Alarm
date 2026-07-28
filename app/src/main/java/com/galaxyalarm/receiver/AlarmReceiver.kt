package com.galaxyalarm.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.ring.ActiveAlarm
import com.galaxyalarm.ring.ActiveAlarms
import com.galaxyalarm.ring.AlarmDismissalStore
import com.galaxyalarm.ring.AlarmRingActivity
import com.galaxyalarm.ring.AlarmStopController
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
        val occurrenceId = intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1L)
        if (action == AlarmIntents.ACTION_STOP || action == AlarmIntents.ACTION_STOP_ALL) {
            AlarmStopController.stopAllNow(context, occurrenceId)
            return
        }
        val dismissalStore = AlarmDismissalStore(context)
        if (
            (action == AlarmIntents.ACTION_FIRE || action == AlarmIntents.ACTION_FIRE_BACKUP) &&
            occurrenceId >= 0L &&
            dismissalStore.isDismissed(occurrenceId)
        ) {
            Log.i(TAG, "ignored dismissed occurrence: $occurrenceId")
            return
        }
        if (action == AlarmIntents.ACTION_SNOOZE && occurrenceId >= 0L) {
            dismissalStore.markDismissed(listOf(occurrenceId))
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
            if (action == AlarmIntents.ACTION_FIRE || action == AlarmIntents.ACTION_FIRE_BACKUP) {
                scheduleServiceRetryOrVibrate(context, intent)
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission") // USE_EXACT_ALARM is declared for this clock app.
    private fun scheduleServiceRetryOrVibrate(context: Context, source: Intent) {
        val retryCount = source.getIntExtra(AlarmIntents.EXTRA_RECEIVER_RETRY_COUNT, 0)
        val occurrenceId = source.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1L)
        if (retryCount < MAX_SERVICE_RETRIES && occurrenceId >= 0L) {
            val retryIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_FIRE_BACKUP
                putExtras(source)
                putExtra(AlarmIntents.EXTRA_RECEIVER_RETRY_COUNT, retryCount + 1)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                occurrenceId.toInt() xor RETRY_REQUEST_MASK xor retryCount,
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                val alarmManager = context.getSystemService(AlarmManager::class.java)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + SERVICE_RETRY_DELAY_MS,
                    pendingIntent,
                )
            }.onSuccess { return }
                .onFailure { Log.e(TAG, "failed to schedule AlarmService retry", it) }
        }

        if (occurrenceId >= 0L && AlarmDismissalStore(context).isDismissed(occurrenceId)) return
        if (occurrenceId >= 0L) {
            val active = ActiveAlarm(occurrenceId, -1L, "アラーム", "")
            ActiveAlarms.push(active)
            runCatching {
                context.getSystemService(NotificationManager::class.java).notify(
                    NotificationHelper.FOREGROUND_ID,
                    NotificationHelper(context).buildAlarmNotification(
                        occurrenceId,
                        -1L,
                        active.label,
                        active.timeText,
                    )
                )
            }
            runCatching {
                context.startActivity(
                    Intent(context, AlarmRingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
                    }
                )
            }
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(
                    EMERGENCY_VIBRATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(
                        effect,
                        VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_ALARM)
                            .build(),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(
                        effect,
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            }
        }.onFailure { Log.e(TAG, "emergency alarm vibration failed", it) }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val MAX_SERVICE_RETRIES = 2
        private const val SERVICE_RETRY_DELAY_MS = 3_000L
        private const val EMERGENCY_VIBRATION_MS = 10_000L
        private const val RETRY_REQUEST_MASK = 0x40000000
    }
}
