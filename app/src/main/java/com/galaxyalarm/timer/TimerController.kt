package com.galaxyalarm.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.galaxyalarm.MainActivity
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.scheduler.AlarmIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * タイマーを「見た目だけのカウント」ではなく、AlarmManager の実予約 + 永続ストアで管理する。
 * - タブ移動・アプリ終了・端末再起動で消えない(prefs に endAt を保存、起動時に復元/再予約)。
 * - 時間が来たら AlarmReceiver → AlarmService で本物のアラームとして鳴る。
 * - endAt は秒精度(絶対時刻 millis)。
 */
object TimerController {
    private const val PREFS = "timer_state"
    private const val KEY_END_AT = "endAt"
    private const val KEY_TOTAL = "totalSeconds"
    const val TIMER_REQUEST_CODE = 770_001
    const val TIMER_OCCURRENCE_ID = -1000L

    /** 終了予定の絶対時刻(millis)。0 は未設定。UI が購読する。 */
    private val _endAt = MutableStateFlow(0L)
    val endAt: StateFlow<Long> = _endAt

    /** 直近に設定した合計秒(リセット表示用)。 */
    @Volatile var totalSeconds: Int = 5 * 60
        private set

    private fun prefs(context: Context) =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.applicationContext.createDeviceProtectedStorageContext()
        else context.applicationContext)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 起動時・再起動時に呼ぶ。保存済みタイマーを復元(未来なら再予約、過去なら破棄)。 */
    fun init(context: Context) {
        val p = prefs(context)
        val savedEnd = p.getLong(KEY_END_AT, 0L)
        totalSeconds = p.getInt(KEY_TOTAL, 5 * 60)
        if (savedEnd > System.currentTimeMillis()) {
            _endAt.value = savedEnd
            runCatching { schedule(context, savedEnd) }
        } else {
            _endAt.value = 0L
            if (savedEnd != 0L) clearPersisted(context)
        }
    }

    /** タイマー開始。durationSeconds 後に鳴る。 */
    fun start(context: Context, durationSeconds: Int) {
        if (durationSeconds <= 0) return
        val end = System.currentTimeMillis() + durationSeconds * 1000L
        totalSeconds = durationSeconds
        _endAt.value = end
        prefs(context).edit()
            .putLong(KEY_END_AT, end)
            .putInt(KEY_TOTAL, durationSeconds)
            .apply()
        runCatching { schedule(context, end) }
    }

    /** ユーザーによるキャンセル。予約も解除。 */
    fun cancel(context: Context) {
        _endAt.value = 0L
        clearPersisted(context)
        runCatching { alarmManager(context).cancel(firePendingIntent(context)) }
    }

    /** 発火時に AlarmService から呼ぶ(状態だけクリア、予約は消費済み)。 */
    fun onFired(context: Context) {
        _endAt.value = 0L
        clearPersisted(context)
    }

    private fun clearPersisted(context: Context) {
        prefs(context).edit().remove(KEY_END_AT).apply()
    }

    private fun schedule(context: Context, triggerAt: Long) {
        val am = alarmManager(context)
        val pi = firePendingIntent(context)
        val showPi = PendingIntent.getActivity(
            context, TIMER_REQUEST_CODE + 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
        } catch (e: SecurityException) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun firePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_TIMER_FIRE
        }
        return PendingIntent.getBroadcast(
            context, TIMER_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
