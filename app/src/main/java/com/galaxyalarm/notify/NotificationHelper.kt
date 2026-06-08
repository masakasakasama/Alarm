package com.galaxyalarm.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import com.galaxyalarm.MainActivity
import com.galaxyalarm.R
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.ring.AlarmRingActivity
import com.galaxyalarm.scheduler.AlarmIntents

class NotificationHelper(private val context: Context) {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val health = NotificationChannel(
            CHANNEL_HEALTH,
            context.getString(R.string.health_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val alerts = NotificationChannel(
            CHANNEL_RELIABILITY_ALERT,
            "アラーム要対応",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "OS更新や権限変更でアラームが鳴らない恐れがある時の警告"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarm, service, health, alerts))
    }

    fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("アラーム")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()

    fun showReliabilityWarning(title: String, message: String) {
        val pi = PendingIntent.getActivity(
            context,
            7001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RELIABILITY_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(RELIABILITY_ALERT_ID, notification) }
    }

    fun buildAlarmNotification(
        occurrenceId: Long,
        alarmId: Long,
        label: String,
        timeText: String,
    ): Notification {
        val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            occurrenceId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            context,
            100_000_000 + occurrenceId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_STOP
                putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getBroadcast(
            context,
            200_000_000 + occurrenceId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_SNOOZE
                putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (label.isBlank()) "アラーム" else label)
            .setContentText("$timeText  鳴動中")
            .setSubText("ロック画面で操作できます")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setLocalOnly(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(Action.Builder(0, "停止", stopPi).setAuthenticationRequired(false).build())
            .addAction(Action.Builder(0, "スヌーズ", snoozePi).setAuthenticationRequired(false).build())
            .build()
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_ring"
        const val CHANNEL_SERVICE = "alarm_service"
        const val CHANNEL_HEALTH = "schedule_health"
        const val CHANNEL_RELIABILITY_ALERT = "alarm_reliability_alerts"
        const val FOREGROUND_ID = 42
        const val RELIABILITY_ALERT_ID = 43
    }
}
