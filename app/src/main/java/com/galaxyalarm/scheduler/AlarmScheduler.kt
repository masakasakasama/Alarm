package com.galaxyalarm.scheduler

import android.annotation.SuppressLint
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
 * - ユーザー可視の setAlarmClock を主予約と予備予約に使う。
 * - 予約は ScheduledOccurrence 単位、requestCode は PK(=一意)を使うため衝突しない。
 */
@SuppressLint("ScheduleExactAlarm", "MissingPermission") // Lint only recognizes SCHEDULE_EXACT_ALARM.
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

    /** Room と AlarmManager を、既存予約を先に失わないように整合させる。 */
    suspend fun rescheduleAll(reason: String) = mutex.withLock {
        Log.i(TAG, "rescheduleAll: $reason")
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

        val groups = groupDao.getAll()
        val alarms = alarmDao.getAll()
        val plan = ScheduleReconciliation.plan(
            groups = groups,
            alarms = alarms,
            occurrences = occurrenceDao.getAllScheduled(),
            now = System.currentTimeMillis(),
            lateDeliveryGraceMs = LATE_DELIVERY_GRACE_MS,
        )
        plan.cancel.forEach { cancelOccurrenceLocked(it) }
        var reasserted = 0
        for (occurrence in plan.reassert) {
            if (reassertOccurrenceLocked(occurrence)) reasserted += 1
        }
        var created = 0
        for (alarm in plan.create) {
            if (scheduleAlarmLocked(alarm)) created += 1
        }
        // 古い終了済み予約・古いログを掃除
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        occurrenceDao.purgeOld(weekAgo)
        logDao.purgeOld(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        Log.i(TAG, "rescheduleAll done: reasserted=$reasserted created=$created canceled=${plan.cancel.size}")
    }

    /** 壁時計の変更時だけ通常予約を再計算し、保留中のスヌーズは保持する。 */
    suspend fun recalculateRegularAlarms(reason: String) = mutex.withLock {
        Log.i(TAG, "recalculateRegularAlarms: $reason")
        if (!permissions.canScheduleExactAlarms()) return@withLock
        val groups = groupDao.getAll().associateBy { it.id }
        alarmDao.getAll().forEach { alarm ->
            val oldRegular = occurrenceDao.getScheduledForAlarm(alarm.id)
                .filter { it.snoozeCount == 0 }
            val enabled = alarm.enabled && groups[alarm.groupId]?.enabled == true
            if (!enabled) {
                occurrenceDao.getScheduledForAlarm(alarm.id).forEach { cancelOccurrenceLocked(it) }
            } else if (scheduleAlarmLocked(alarm)) {
                oldRegular.forEach { cancelOccurrenceLocked(it) }
            }
        }
    }

    /** 1 アラームの次回発火を予約。成功なら true。 */
    suspend fun scheduleAlarm(alarm: AlarmItem): Boolean {
        return mutex.withLock { scheduleAlarmLocked(alarm) }
    }

    /** Replace every existing occurrence for an edited alarm as one serialized operation. */
    suspend fun replaceAlarm(alarm: AlarmItem): Boolean = mutex.withLock {
        val old = occurrenceDao.getScheduledForAlarm(alarm.id)
        val scheduled = scheduleAlarmLocked(alarm)
        if (scheduled) old.forEach { cancelOccurrenceLocked(it) }
        scheduled
    }

    /** Keep the regular next occurrence and replace only an existing snooze retry. */
    suspend fun replaceSnooze(
        alarmId: Long,
        groupId: Long,
        triggerAt: Long,
        snoozeCount: Int,
    ): Boolean = mutex.withLock {
        occurrenceDao.getScheduledForAlarm(alarmId)
            .filter { it.snoozeCount > 0 }
            .forEach { cancelOccurrenceLocked(it) }
        scheduleOccurrenceLocked(alarmId, groupId, triggerAt, snoozeCount)
    }

    private suspend fun scheduleAlarmLocked(alarm: AlarmItem): Boolean {
        val triggerAt = NextTriggerCalculator.nextTrigger(
            alarm.hour, alarm.minute, alarm.weekdaysMask
        )
        return scheduleOccurrenceLocked(alarm.id, alarm.groupId, triggerAt, snoozeCount = 0)
    }

    /** 指定時刻で予約を作る(スヌーズ再予約でも使用)。 */
    suspend fun scheduleOccurrence(
        alarmId: Long, groupId: Long, triggerAt: Long, snoozeCount: Int
    ): Boolean = mutex.withLock {
        scheduleOccurrenceLocked(alarmId, groupId, triggerAt, snoozeCount)
    }

    private suspend fun scheduleOccurrenceLocked(
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
            val showPi = showPendingIntent(alarmId, requestCode)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
            scheduleBackup(occ, triggerAt)
            true
        } catch (e: Exception) {
            runCatching { cancelPendingIntent(firePendingIntent(occ.id, alarmId, requestCode)) }
            runCatching { cancelPendingIntent(backupPendingIntent(occ)) }
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
        mutex.withLock { cancelOccurrenceLocked(occ) }
    }

    private suspend fun cancelOccurrenceLocked(occ: ScheduledOccurrence) {
        cancelPendingIntent(firePendingIntent(occ.id, occ.alarmId, occ.requestCode))
        cancelPendingIntent(backupPendingIntent(occ))
        occurrenceDao.setStatus(occ.id, OccurrenceStatus.CANCELED, System.currentTimeMillis())
    }

    /** The primary fire succeeded, so its delayed safety retry is no longer needed. */
    suspend fun cancelBackup(occurrenceId: Long) = mutex.withLock {
        occurrenceDao.getById(occurrenceId)?.let { cancelPendingIntent(backupPendingIntent(it)) }
    }

    /** アラーム単位の予約をキャンセル。 */
    suspend fun cancelAlarm(alarmId: Long) {
        mutex.withLock {
            occurrenceDao.getScheduledForAlarm(alarmId).forEach { cancelOccurrenceLocked(it) }
        }
    }

    /** グループ単位の予約をキャンセル。 */
    suspend fun cancelGroup(groupId: Long) {
        mutex.withLock {
            occurrenceDao.getScheduledForGroup(groupId).forEach { cancelOccurrenceLocked(it) }
        }
    }

    private suspend fun reassertOccurrenceLocked(occ: ScheduledOccurrence): Boolean {
        val now = System.currentTimeMillis()
        val triggerAt = maxOf(occ.triggerAtMillis, now + REASSERT_DELAY_MS)
        return try {
            val pi = firePendingIntent(occ.id, occ.alarmId, occ.requestCode)
            val showPi = showPendingIntent(occ.alarmId, occ.requestCode)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
            val backupScheduled = scheduleBackup(occ, triggerAt)
            if (triggerAt != occ.triggerAtMillis) {
                occurrenceDao.update(occ.copy(triggerAtMillis = triggerAt, updatedAt = now))
            }
            backupScheduled
        } catch (error: Exception) {
            Log.e(TAG, "failed to reassert occurrence ${occ.id}", error)
            false
        }
    }

    private fun scheduleBackup(occ: ScheduledOccurrence, primaryAt: Long): Boolean {
        val backupAt = primaryAt + BACKUP_DELAY_MS
        val backupPi = backupPendingIntent(occ)
        return runCatching {
            val showPi = showPendingIntent(occ.alarmId, occ.requestCode)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(backupAt, showPi), backupPi)
            true
        }.getOrElse { error ->
            runCatching { cancelPendingIntent(backupPi) }
            Log.e(TAG, "failed to schedule backup for occurrence ${occ.id}", error)
            false
        }
    }

    private fun backupPendingIntent(occ: ScheduledOccurrence): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_FIRE_BACKUP
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, occ.id)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, occ.alarmId)
        }
        return PendingIntent.getBroadcast(
            context, backupRequestCode(occ.requestCode), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun hasSystemReservation(occ: ScheduledOccurrence): Boolean {
        val primary = PendingIntent.getBroadcast(
            context,
            occ.requestCode,
            Intent(context, AlarmReceiver::class.java).apply { action = AlarmIntents.ACTION_FIRE },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        val backup = PendingIntent.getBroadcast(
            context,
            backupRequestCode(occ.requestCode),
            Intent(context, AlarmReceiver::class.java).apply { action = AlarmIntents.ACTION_FIRE_BACKUP },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        return primary != null && backup != null
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
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

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val BACKUP_DELAY_MS = 10_000L
        private const val REASSERT_DELAY_MS = 2_000L
        internal const val LATE_DELIVERY_GRACE_MS = 10 * 60_000L

        internal fun backupRequestCode(primaryRequestCode: Int): Int =
            primaryRequestCode xor Int.MIN_VALUE
    }
}
