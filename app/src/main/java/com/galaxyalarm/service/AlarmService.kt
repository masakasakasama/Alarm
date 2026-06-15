package com.galaxyalarm.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.ring.ActiveAlarm
import com.galaxyalarm.ring.ActiveAlarms
import com.galaxyalarm.ring.AlarmPlayer
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.scheduler.NextTriggerCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 鳴動中の Foreground Service。スタックで複数同時鳴動を管理し、
 * 音/バイブ/通知/自動停止を制御する。1件停止しても他は止めない。
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val player by lazy { AlarmPlayer(this) }
    private val notifier by lazy { NotificationHelper(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRunnables = mutableMapOf<Long, Runnable>()
    private var wakeLock: PowerManager.WakeLock? = null

    private val container get() = (application as AlarmApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService の 5 秒制約を満たすため、どのアクションでも即時に前面化。
        startForeground(NotificationHelper.FOREGROUND_ID, notifier.buildLoadingNotification())
        when (intent?.action) {
            AlarmIntents.ACTION_FIRE -> handleFire(intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1))
            AlarmIntents.ACTION_STOP -> handleStop(intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1))
            AlarmIntents.ACTION_SNOOZE -> handleSnooze(intent.getLongExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, -1))
            AlarmIntents.ACTION_STOP_ALL -> handleStopAll()
            AlarmIntents.ACTION_TIMER_FIRE -> handleTimerFire(
                intent.getIntExtra(AlarmIntents.EXTRA_TIMER_ID, -1),
                intent.getBooleanExtra(AlarmIntents.EXTRA_TIMER_SOUND, true)
            )
            AlarmIntents.ACTION_TEST_FIRE -> handleTestFire()
            else -> {}
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "galaxyalarm:ring"
        ).apply { acquire(10 * 60 * 1000L) }
    }

    private fun handleFire(occurrenceId: Long) {
        if (occurrenceId < 0) return
        acquireWakeLock()
        scope.launch {
            val occ = container.db.occurrenceDao().getById(occurrenceId) ?: return@launch
            if (occ.status != OccurrenceStatus.SCHEDULED) return@launch
            val alarm = container.db.alarmDao().getById(occ.alarmId) ?: return@launch
            val group = container.db.groupDao().getById(alarm.groupId) ?: return@launch
            if (!alarm.enabled || !group.enabled) return@launch
            val h12 = (alarm.hour % 12).let { if (it == 0) 12 else it }
            val ampm = if (alarm.hour < 12) "AM" else "PM"
            val timeText = String.format(Locale.JAPAN, "%d:%02d %s", h12, alarm.minute, ampm)

            // ログ: 発火と遅延。
            val now = System.currentTimeMillis()
            container.repository.log(
                AlarmEventLog(
                    alarmId = alarm.id, groupId = alarm.groupId,
                    scheduledAtMillis = occ.triggerAtMillis, firedAtMillis = now,
                    delayMs = now - occ.triggerAtMillis,
                    result = EventResult.FIRED,
                    message = "発火 ${alarm.label}"
                )
            )
            container.db.occurrenceDao().setStatus(occ.id, OccurrenceStatus.FIRED, now)

            // 繰り返しアラームは次回をすぐ再予約(取りこぼし防止)。
            if (com.galaxyalarm.data.model.Weekdays.isRepeating(alarm.weekdaysMask)) {
                val next = NextTriggerCalculator.nextTrigger(alarm.hour, alarm.minute, alarm.weekdaysMask)
                container.scheduler.scheduleOccurrence(alarm.id, alarm.groupId, next, 0)
            } else {
                // 一度きりは自動的に無効化。
                container.db.alarmDao().setEnabled(alarm.id, false, now)
            }

            handler.post {
                ActiveAlarms.push(ActiveAlarm(occ.id, alarm.id, alarm.label, timeText))
                startForeground(
                    NotificationHelper.FOREGROUND_ID,
                    notifier.buildAlarmNotification(occ.id, alarm.id, alarm.label, timeText)
                )
                // スタック最上段の音/バイブを再生。
                player.stop()
                player.start(alarm.soundMode, alarm.ringtoneUri, alarm.vibrationEnabled, alarm.vibrationPattern)
                // 画面OFF/ロック中のみ全画面を直接起動。使用中は全画面で乗っ取らず、
                // ヘッドアップ通知(停止/スヌーズボタン付き)のポップアップで操作してもらう。
                if (shouldLaunchFullScreen()) {
                    launchRingActivity(occ.id, alarm.id)
                }
                scheduleAutoStop(occ.id, alarm.autoStopMinutes)
            }
        }
    }

    /** タイマー発火: DBアラームに依存せず鳴らす。音なし設定ならバイブのみで鳴らす。 */
    private fun handleTimerFire(timerId: Int, soundOn: Boolean) {
        if (timerId < 0) return
        com.galaxyalarm.timer.TimerController.onFired(this, timerId)
        val mode = if (soundOn) com.galaxyalarm.data.model.SoundMode.SOUND
        else com.galaxyalarm.data.model.SoundMode.VIBRATE_ONLY
        ringTransient(com.galaxyalarm.timer.TimerController.occurrenceId(timerId), "タイマー", "タイマー終了", mode)
    }

    /** テスト鳴動: 信頼性チェックから即時に鳴らして経路(音/全画面/通知)を確認する。 */
    private fun handleTestFire() {
        ringTransient(TEST_OCCURRENCE_ID, "テスト鳴動", "テスト鳴動", com.galaxyalarm.data.model.SoundMode.SOUND)
    }

    /** DBに依存しない一時的な鳴動(タイマー/テスト共通)。 */
    private fun ringTransient(
        id: Long,
        label: String,
        timeText: String,
        soundMode: com.galaxyalarm.data.model.SoundMode,
    ) {
        acquireWakeLock()
        handler.post {
            ActiveAlarms.push(ActiveAlarm(id, -1L, label, timeText))
            startForeground(
                NotificationHelper.FOREGROUND_ID,
                notifier.buildAlarmNotification(id, -1L, label, timeText)
            )
            player.stop()
            player.start(
                soundMode,
                null,
                true,
                com.galaxyalarm.data.model.VibrationPattern.SHORT
            )
            if (shouldLaunchFullScreen()) launchRingActivity(id, -1L)
            scheduleAutoStop(id, 5)
        }
        scope.launch {
            container.repository.log(
                AlarmEventLog(
                    alarmId = null, groupId = null,
                    scheduledAtMillis = null, firedAtMillis = System.currentTimeMillis(), delayMs = null,
                    result = EventResult.FIRED, message = label
                )
            )
        }
    }

    /** 画面OFFまたはロック中なら全画面で起こす。使用中はヘッドアップ通知に任せる。 */
    private fun shouldLaunchFullScreen(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return !pm.isInteractive || km.isKeyguardLocked
    }

    private fun launchRingActivity(occurrenceId: Long, alarmId: Long) {
        val i = Intent(this, com.galaxyalarm.ring.AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        try { startActivity(i) } catch (e: Exception) { Log.e(TAG, "ring activity", e) }
    }

    private fun scheduleAutoStop(occurrenceId: Long, minutes: Int) {
        val r = Runnable {
            scope.launch {
                val occ = container.db.occurrenceDao().getById(occurrenceId)
                container.repository.log(
                    AlarmEventLog(
                        alarmId = occ?.alarmId, groupId = occ?.groupId,
                        scheduledAtMillis = occ?.triggerAtMillis, firedAtMillis = null, delayMs = null,
                        result = EventResult.MISSED,
                        message = "自動停止(${minutes}分超過)"
                    )
                )
            }
            stopOne(occurrenceId)
        }
        autoStopRunnables[occurrenceId] = r
        handler.postDelayed(r, minutes.coerceAtLeast(1) * 60_000L)
    }

    private fun handleStop(occurrenceId: Long) {
        scope.launch {
            val occ = container.db.occurrenceDao().getById(occurrenceId)
            container.repository.log(
                AlarmEventLog(
                    alarmId = occ?.alarmId, groupId = occ?.groupId,
                    scheduledAtMillis = occ?.triggerAtMillis, firedAtMillis = null, delayMs = null,
                    result = EventResult.DISMISSED, message = "停止"
                )
            )
        }
        stopOne(occurrenceId)
    }

    private fun handleSnooze(occurrenceId: Long) {
        scope.launch {
            val occ = container.db.occurrenceDao().getById(occurrenceId) ?: return@launch
            val alarm = container.db.alarmDao().getById(occ.alarmId) ?: return@launch
            if (alarm.snoozeEnabled && occ.snoozeCount < alarm.maxSnoozeCount) {
                val next = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
                container.scheduler.scheduleOccurrence(alarm.id, alarm.groupId, next, occ.snoozeCount + 1)
                container.repository.log(
                    AlarmEventLog(
                        alarmId = alarm.id, groupId = alarm.groupId,
                        scheduledAtMillis = next, firedAtMillis = null, delayMs = null,
                        result = EventResult.SNOOZED,
                        message = "スヌーズ${occ.snoozeCount + 1}回目 (+${alarm.snoozeMinutes}分)"
                    )
                )
            }
        }
        stopOne(occurrenceId)
    }

    /** 1件だけ止める。他は鳴動継続。空になったらサービス終了。 */
    private fun stopOne(occurrenceId: Long) {
        handler.post {
            autoStopRunnables.remove(occurrenceId)?.let { handler.removeCallbacks(it) }
            ActiveAlarms.remove(occurrenceId)
            val top = ActiveAlarms.top()
            if (top == null) {
                finishService()
            } else {
                // 次のアラームの音/バイブへ切り替え。
                scope.launch {
                    val alarm = container.db.alarmDao().getById(top.alarmId)
                    handler.post {
                        player.stop()
                        if (alarm != null) {
                            player.start(alarm.soundMode, alarm.ringtoneUri, alarm.vibrationEnabled, alarm.vibrationPattern)
                            startForeground(
                                NotificationHelper.FOREGROUND_ID,
                                notifier.buildAlarmNotification(top.occurrenceId, top.alarmId, top.label, top.timeText)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleStopAll() {
        handler.post {
            autoStopRunnables.values.forEach { handler.removeCallbacks(it) }
            autoStopRunnables.clear()
            ActiveAlarms.clear()
            finishService()
        }
    }

    private fun finishService() {
        player.stop()
        getSystemService(NotificationManager::class.java).cancel(NotificationHelper.FOREGROUND_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val TEST_OCCURRENCE_ID = -2000L
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.JAPAN)
        fun fmt(t: Long) = timeFmt.format(Date(t))
    }
}
