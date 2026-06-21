package com.galaxyalarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyalarm.MainActivity
import com.galaxyalarm.data.dao.AlarmEventLogDao
import com.galaxyalarm.data.dao.AlarmGroupDao
import com.galaxyalarm.data.dao.AlarmItemDao
import com.galaxyalarm.data.dao.ScheduledOccurrenceDao
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.reliability.PermissionChecker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room を真実として AlarmManager の予約を構築/再構築する。
 * - setAlarmClock を最優先、無理なら setExactAndAllowWhileIdle。
 * - 予約は ScheduledOccurrence 単位、requestCode は PK(=一意)を使うため衝突しない。
 */
class AlarmScheduler(
    private val context: Context,
    private val groupDao: AlarmGroupDao,
    private val alarmDao: AlarmItemDao,
    private val occurrenceDao: ScheduledOccurrenceDao,
    private val logDao: AlarmEventLogDao,
    private val permissions: PermissionChecker,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val mutex = Mutex()

    /** すべての有効アラーム(有効グループ内)を Room から再構築する。 */
    suspend fun rescheduleAll(reason: String) = mutex.withLock {
        Log.i(TAG, "rescheduleAll: $reason")
        // 既存 SCHEDULED を全キャンセル
        occurrenceDao.getAllScheduled().forEach { cancelOccurrence(it) }

        if (!permissions.canScheduleExactAlarms()) {
            logDao.insert(
                AlarmEventLog(
                    alarmId = null, groupId = null,
                    scheduledAtMillis = null, firedAtMillis = null, delayMs = null,
                    result = EventResult.FAILED_TO_SCHEDULE,
                    message = "exact alarm 権限なし: 再スケジュール中止 ($reason)"
                )
            )
            return@withLock
        }

        val groups = groupDao.getAll().associateBy { it.id }
        val alarms = alarmDao.getAll()
        var scheduled = 0
        for (alarm in alarms) {
            val group = groups[alarm.groupId] ?: continue
            if (!alarm.enabled || !group.enabled) continue
            if (scheduleAlarm(alarm)) scheduled++
        }
        // 古い終了済み予約・古いログを掃除
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        occurrenceDao.purgeOld(weekAgo)
        logDao.purgeOld(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        Log.i(TAG, "rescheduleAll done: $scheduled 件")
    }

    /** 1 アラームの次回発火を予約。成功なら true。 */
    suspend fun scheduleAlarm(alarm: AlarmItem): Boolean {
        val triggerAt = NextTriggerCalculator.nextTrigger(
            alarm.hour, alarm.minute, alarm.weekdaysMask
        )
        return scheduleOccurrence(alarm.id, alarm.groupId, triggerAt, snoozeCount = 0)
    }

    /** 指定時刻で予約を作る(スヌーズ再予約でも使用)。 */
    suspend fun scheduleOccurrence(
        alarmId: Long, groupId: Long, triggerAt: Long, snoozeCount: Int
    ): Boolean {
        if (!permissions.canScheduleExactAlarms()) return false
        // requestCode を一意にするため、まず行を挿入して PK を取得し requestCode に採用。
        val tempId = occurrenceDao.insert(
            ScheduledOccurrence(
                alarmId = alarmId, groupId = groupId,
                triggerAtMillis = triggerAt, requestCode = 0,
                status = OccurrenceStatus.SCHEDULED, snoozeCount = snoozeCount
            )
        )
        val requestCode = tempId.toInt()
        val occ = occurrenceDao.getById(tempId)!!.copy(requestCode = requestCode)
        occurrenceDao.update(occ)

        return try {
            val pi = firePendingIntent(occ.id, alarmId, requestCode)
            if (alarmManager.canScheduleExactAlarms() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                val showPi = showPendingIntent(alarmId, requestCode)
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showPi), pi
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pi
                )
            }
            true
        } catch (e: Exception) {
            occurrenceDao.setStatus(occ.id, OccurrenceStatus.FAILED, System.currentTimeMillis())
            logDao.insert(
                AlarmEventLog(
                    alarmId = alarmId, groupId = groupId,
                    scheduledAtMillis = triggerAt, firedAtMillis = null, delayMs = null,
                    result = EventResult.FAILED_TO_SCHEDULE,
                    message = "${e::class.java.simpleName}: ${e.message}"
                )
            )
            false
        }
    }

    /** 単一予約をキャンセル(AlarmManager とステータス両方)。 */
    suspend fun cancelOccurrence(occ: ScheduledOccurrence) {
        alarmManager.cancel(firePendingIntent(occ.id, occ.alarmId, occ.requestCode))
        occurrenceDao.setStatus(occ.id, OccurrenceStatus.CANCELED, System.currentTimeMillis())
    }

    /** アラーム単位の予約をキャンセル。 */
    suspend fun cancelAlarm(alarmId: Long) {
        occurrenceDao.getScheduledForAlarm(alarmId).forEach { cancelOccurrence(it) }
    }

    /** グループ単位の予約をキャンセル。 */
    suspend fun cancelGroup(groupId: Long) {
        occurrenceDao.getScheduledForGroup(groupId).forEach { cancelOccurrence(it) }
    }

    private fun firePendingIntent(occurrenceId: Long, alarmId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_FIRE
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPendingIntent(alarmId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ALARM_ID, alarmId)
        }
        return PendingIntent.getActivity(
            context, 900_000_000 + requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object { private const val TAG = "AlarmScheduler" }
}
