package com.galaxyalarm.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
        val timer = NotificationChannel(
            CHANNEL_TIMER,
            "タイマー",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "実行中タイマーの残り時間表示"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarm, service, health, alerts, timer))
    }

    fun cancelObsoleteNextAlarmStatus() {
        runCatching { nm.cancel(OBSOLETE_NEXT_ALARM_STATUS_ID) }
    }

    /** 実行中タイマーの常駐通知。残り時間をカウントダウン表示し、キャンセル操作を提供する。 */
    fun showTimerNotification(timerId: Int, endAt: Long, soundOn: Boolean) {
        val openPi = mainActivityPendingIntent(7100 + timerId)
        val cancelPi = PendingIntent.getBroadcast(
            context,
            7200 + timerId,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_TIMER_CANCEL
                putExtra(AlarmIntents.EXTRA_TIMER_ID, timerId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("タイマー実行中")
            .setContentText(if (soundOn) "終了時にアラームが鳴ります" else "終了時は音なし(バイブのみ)")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .addAction(Action.Builder(0, "キャンセル", cancelPi).build())
            .build()
        runCatching { nm.notify(TIMER_ID_BASE + timerId, notification) }
    }

    fun cancelTimerNotification(timerId: Int) {
        runCatching { nm.cancel(TIMER_ID_BASE + timerId) }
    }

    /**
     * Foreground Service を5秒以内に前面化するための一時通知。
     * DB読込中の短い時間でも通知タップが無反応にならないよう、必ずアプリを開ける PendingIntent を付ける。
     */
    fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("アラーム")
            .setContentText("アラームを起動しています")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainActivityPendingIntent(LOADING_NOTIFICATION_REQUEST_CODE))
            .build()

    fun showReliabilityWarning(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RELIABILITY_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainActivityPendingIntent(RELIABILITY_NOTIFICATION_REQUEST_CODE))
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
        // action + data URI + requestCode を occurrence ごとに一意化する。
        // 同じ occurrence の通知更新では PendingIntent 自体をキャンセルせず中身だけ更新し、
        // SystemUI が古い通知を保持している瞬間にタップしても CanceledException 相当の無反応を作らない。
        val openIntent = Intent(context, AlarmRingActivity::class.java).apply {
            action = ACTION_OPEN_ALARM_RING
            data = Uri.parse("galaxyalarm://ring/$occurrenceId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
            putExtra(EXTRA_ALARM_TIME_TEXT, timeText)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            occurrenceRequestCode(occurrenceId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            context,
            100_000_000 + occurrenceId.toInt(),
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmIntents.ACTION_STOP_ALL
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

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (label.isBlank()) "アラーム" else label)
            .setContentText("$timeText  停止できます")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(Action.Builder(0, "停止", stopPi).setAuthenticationRequired(false).build())
            .addAction(Action.Builder(0, "スヌーズ", snoozePi).setAuthenticationRequired(false).build())
            .build()

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (label.isBlank()) "アラーム" else label)
            .setContentText("$timeText  鳴動中")
            .setSubText("ロック画面で停止できます")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setLocalOnly(true)
            .setColor(Color.rgb(83, 136, 255))
            .setColorized(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(Action.Builder(0, "停止", stopPi).setAuthenticationRequired(false).build())
            .addAction(Action.Builder(0, "スヌーズ", snoozePi).setAuthenticationRequired(false).build())
            .build()
    }

    private fun mainActivityPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun occurrenceRequestCode(occurrenceId: Long): Int =
        (occurrenceId xor (occurrenceId ushr 32)).toInt()

    companion object {
        const val CHANNEL_ALARM = "alarm_ring"
        const val CHANNEL_SERVICE = "alarm_service"
        const val CHANNEL_HEALTH = "schedule_health"
        const val CHANNEL_RELIABILITY_ALERT = "alarm_reliability_alerts"
        const val CHANNEL_TIMER = "timer_running"
        const val FOREGROUND_ID = 42
        const val RELIABILITY_ALERT_ID = 43
        const val TIMER_ID_BASE = 44
        const val ACTION_OPEN_ALARM_RING = "com.galaxyalarm.action.OPEN_ALARM_RING"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_TIME_TEXT = "extra_alarm_time_text"
        private const val OBSOLETE_NEXT_ALARM_STATUS_ID = 45
        private const val RELIABILITY_NOTIFICATION_REQUEST_CODE = 7001
        private const val LOADING_NOTIFICATION_REQUEST_CODE = 7002
    }
}
