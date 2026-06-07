package com.galaxyalarm.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.galaxyalarm.R
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.ring.AlarmRingActivity
import com.galaxyalarm.scheduler.AlarmIntents

/** 通知チャンネルと鳴動通知の生成を集約。 */
class NotificationHelper(private val context: Context) {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val alarm = NotificationChannel(
            CHANNEL_ALARM, context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // 音は AlarmPlayer 側で制御
            enableVibration(false)
        }
        val service = NotificationChannel(
            CHANNEL_SERVICE, context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val health = NotificationChannel(
            CHANNEL_HEALTH, context.getString(R.string.health_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannels(listOf(alarm, service, health))
    }

    /** startForeground を即時に満たすための最小通知。 */
    fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("アラーム")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

    /** 鳴動用の高優先度・全画面通知を作る。 */
    fun buildAlarmNotification(occurrenceId: Long, alarmId: Long, label: String, timeText: String): Notification {
        val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, occurrenceId.toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            context, 100_000_000 + occurrenceId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_STOP
                putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getBroadcast(
            context, 200_000_000 + occurrenceId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_SNOOZE
                putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (label.isBlank()) "アラーム" else label)
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(0, "停止", stopPi)
            .addAction(0, "スヌーズ", snoozePi)
            .build()
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_ring"
        const val CHANNEL_SERVICE = "alarm_service"
        const val CHANNEL_HEALTH = "schedule_health"
        const val FOREGROUND_ID = 42
    }
}
