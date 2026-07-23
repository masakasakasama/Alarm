package com.galaxyalarm.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.galaxyalarm.MainActivity
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.scheduler.AlarmIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TimerEntry(
    val id: Int,
    val endAt: Long,
    val totalSeconds: Int,
    val soundOn: Boolean,
)

data class TimerHistoryEntry(val seconds: Int, val soundOn: Boolean)

@SuppressLint("ScheduleExactAlarm", "MissingPermission") // USE_EXACT_ALARM is declared for this clock app.
object TimerController {
    private const val PREFS = "timer_state"
    private const val KEY_IDS = "timer_ids"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 6
    const val TIMER_REQUEST_BASE = 770_001
    const val OCCURRENCE_ID_BASE = -1000L

    private val _timers = MutableStateFlow<List<TimerEntry>>(emptyList())
    val timers: StateFlow<List<TimerEntry>> = _timers

    private val _history = MutableStateFlow<List<TimerHistoryEntry>>(emptyList())
    val history: StateFlow<List<TimerHistoryEntry>> = _history

    private fun prefs(context: Context) =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.applicationContext.createDeviceProtectedStorageContext()
        else context.applicationContext)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun init(context: Context) {
        val p = prefs(context)
        _history.value = parseHistory(p.getString(KEY_HISTORY, "") ?: "")
        val now = System.currentTimeMillis()

        // Migrate from old single-timer format (endAt key without timer_ids)
        val oldEndAt = p.getLong("endAt", 0L)
        if (oldEndAt > 0L && !p.contains(KEY_IDS)) {
            if (oldEndAt > now) {
                val id = 1
                val soundOn = p.getBoolean("soundOn", true)
                val totalSeconds = p.getInt("totalSeconds", 5 * 60)
                val entry = TimerEntry(id, oldEndAt, totalSeconds, soundOn)
                _timers.value = listOf(entry)
                with(p.edit()) {
                    putString(KEY_IDS, "1")
                    putInt(KEY_NEXT_ID, 2)
                    putLong("timer_1_endAt", oldEndAt)
                    putInt("timer_1_total", totalSeconds)
                    putBoolean("timer_1_sound", soundOn)
                    remove("endAt"); remove("soundOn"); remove("totalSeconds")
                    apply()
                }
                runCatching { schedule(context, entry) }
                runCatching { NotificationHelper(context).showTimerNotification(id, oldEndAt, soundOn) }
            } else {
                p.edit().remove("endAt").apply()
            }
            return
        }

        val ids = (p.getString(KEY_IDS, "") ?: "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val validTimers = mutableListOf<TimerEntry>()
        val toClean = mutableListOf<Int>()
        for (id in ids) {
            val endAt = p.getLong("timer_${id}_endAt", 0L)
            if (endAt <= now) { toClean.add(id); continue }
            val entry = TimerEntry(
                id, endAt,
                p.getInt("timer_${id}_total", 5 * 60),
                p.getBoolean("timer_${id}_sound", true)
            )
            validTimers.add(entry)
            runCatching { schedule(context, entry) }
            runCatching { NotificationHelper(context).showTimerNotification(id, endAt, entry.soundOn) }
        }
        _timers.value = validTimers
        if (toClean.isNotEmpty()) {
            with(p.edit()) {
                toClean.forEach { id ->
                    remove("timer_${id}_endAt"); remove("timer_${id}_total"); remove("timer_${id}_sound")
                }
                putString(KEY_IDS, validTimers.joinToString(",") { it.id.toString() })
                apply()
            }
        }
    }

    fun start(context: Context, durationSeconds: Int, soundOn: Boolean = true): Int {
        if (durationSeconds <= 0) return -1
        val p = prefs(context)
        val id = p.getInt(KEY_NEXT_ID, 1)
        val endAt = System.currentTimeMillis() + durationSeconds * 1000L
        val entry = TimerEntry(id, endAt, durationSeconds, soundOn)
        val newTimers = _timers.value + entry
        _timers.value = newTimers
        val newHistory = addToHistory(_history.value, TimerHistoryEntry(durationSeconds, soundOn))
        _history.value = newHistory
        with(p.edit()) {
            putInt(KEY_NEXT_ID, id + 1)
            putString(KEY_IDS, newTimers.joinToString(",") { it.id.toString() })
            putLong("timer_${id}_endAt", endAt)
            putInt("timer_${id}_total", durationSeconds)
            putBoolean("timer_${id}_sound", soundOn)
            putString(KEY_HISTORY, encodeHistory(newHistory))
            apply()
        }
        runCatching { schedule(context, entry) }
        runCatching { NotificationHelper(context).showTimerNotification(id, endAt, soundOn) }
        return id
    }

    fun cancel(context: Context, timerId: Int) {
        val newTimers = _timers.value.filter { it.id != timerId }
        _timers.value = newTimers
        with(prefs(context).edit()) {
            putString(KEY_IDS, newTimers.joinToString(",") { it.id.toString() })
            remove("timer_${timerId}_endAt")
            remove("timer_${timerId}_total")
            remove("timer_${timerId}_sound")
            apply()
        }
        runCatching { alarmManager(context).cancel(buildFirePi(context, timerId, false)) }
        runCatching { NotificationHelper(context).cancelTimerNotification(timerId) }
    }

    fun onFired(context: Context, timerId: Int) {
        val newTimers = _timers.value.filter { it.id != timerId }
        _timers.value = newTimers
        with(prefs(context).edit()) {
            putString(KEY_IDS, newTimers.joinToString(",") { it.id.toString() })
            remove("timer_${timerId}_endAt")
            remove("timer_${timerId}_total")
            remove("timer_${timerId}_sound")
            apply()
        }
        runCatching { NotificationHelper(context).cancelTimerNotification(timerId) }
    }

    fun occurrenceId(timerId: Int): Long = OCCURRENCE_ID_BASE - timerId

    private fun parseHistory(csv: String): List<TimerHistoryEntry> =
        csv.split(",").mapNotNull { token ->
            val t = token.trim()
            if (t.contains(":")) {
                val (s, sound) = t.split(":", limit = 2)
                val sec = s.toIntOrNull() ?: return@mapNotNull null
                TimerHistoryEntry(sec, sound != "0")
            } else {
                val sec = t.toIntOrNull() ?: return@mapNotNull null
                TimerHistoryEntry(sec, true)
            }
        }.filter { it.seconds > 0 }.take(MAX_HISTORY)

    private fun encodeHistory(list: List<TimerHistoryEntry>): String =
        list.joinToString(",") { "${it.seconds}:${if (it.soundOn) "1" else "0"}" }

    private fun addToHistory(current: List<TimerHistoryEntry>, entry: TimerHistoryEntry): List<TimerHistoryEntry> =
        (listOf(entry) + current.filter { it.seconds != entry.seconds }).take(MAX_HISTORY)

    private fun schedule(context: Context, entry: TimerEntry) {
        val am = alarmManager(context)
        val pi = buildFirePi(context, entry.id, entry.soundOn)
        val showPi = PendingIntent.getActivity(
            context, TIMER_REQUEST_BASE + entry.id + 1_000_000,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(entry.endAt, showPi), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.endAt, pi)
        }
    }

    private fun buildFirePi(context: Context, timerId: Int, soundOn: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_TIMER_FIRE
            putExtra(AlarmIntents.EXTRA_TIMER_ID, timerId)
            putExtra(AlarmIntents.EXTRA_TIMER_SOUND, soundOn)
        }
        return PendingIntent.getBroadcast(
            context, TIMER_REQUEST_BASE + timerId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
