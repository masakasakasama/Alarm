package com.galaxyalarm.data.repo

import com.galaxyalarm.data.dao.AlarmEventLogDao
import com.galaxyalarm.data.dao.AlarmGroupDao
import com.galaxyalarm.data.dao.AlarmItemDao
import com.galaxyalarm.data.dao.ScheduledOccurrenceDao
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

/**
 * UI とデータ層の単一の入口。書き込み後は必ず再スケジュールを呼び、
 * 「DB と予約のズレ」を作らない。
 */
class AlarmRepository(
    private val groupDao: AlarmGroupDao,
    private val alarmDao: AlarmItemDao,
    private val occurrenceDao: ScheduledOccurrenceDao,
    private val logDao: AlarmEventLogDao,
    private val scheduler: AlarmScheduler,
) {
    // ---- 監視 ----
    fun observeGroups(): Flow<List<AlarmGroup>> = groupDao.observeAll()
    fun observeAlarms(): Flow<List<AlarmItem>> = alarmDao.observeAll()
    fun observeScheduled(): Flow<List<ScheduledOccurrence>> = occurrenceDao.observeScheduled()
    fun observeLogs() = logDao.observeRecent()

    suspend fun getGroups() = groupDao.getAll()
    suspend fun getAlarms() = alarmDao.getAll()
    suspend fun getAlarm(id: Long) = alarmDao.getById(id)
    suspend fun getGroup(id: Long) = groupDao.getById(id)
    suspend fun getAllScheduled() = occurrenceDao.getAllScheduled()
    suspend fun enabledCountInGroup(groupId: Long) = alarmDao.enabledCountInGroup(groupId)
    suspend fun alarmCount() = alarmDao.count()
    suspend fun groupCount() = groupDao.count()
    suspend fun latestLog() = logDao.latest()

    // ---- グループ ----
    suspend fun addGroup(name: String): Long {
        val order = groupDao.getAll().size
        return groupDao.insert(AlarmGroup(name = name, sortOrder = order))
    }

    suspend fun renameGroup(group: AlarmGroup, name: String) =
        groupDao.update(group.copy(name = name, updatedAt = System.currentTimeMillis()))

    suspend fun deleteGroup(group: AlarmGroup) {
        scheduler.cancelGroup(group.id)
        groupDao.delete(group) // CASCADE で配下アラームも削除
    }

    /** グループ ON/OFF → 配下アラームの予約を復元/キャンセル。 */
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean) {
        groupDao.setEnabled(groupId, enabled, System.currentTimeMillis())
        if (enabled) scheduler.rescheduleAll("group-on") else scheduler.cancelGroup(groupId)
    }

    /** 最低1つグループが必要。無ければ既定グループを作る。 */
    suspend fun ensureDefaultGroup(): Long {
        val groups = groupDao.getAll()
        return groups.firstOrNull()?.id ?: addGroup("既定グループ")
    }

    // ---- アラーム ----
    suspend fun saveAlarm(item: AlarmItem): Long {
        val id = if (item.id == 0L) {
            alarmDao.insert(item)
        } else {
            alarmDao.update(item.copy(updatedAt = System.currentTimeMillis()))
            item.id
        }
        // 編集後は当該アラームを貼り直す。
        scheduler.cancelAlarm(item.id.takeIf { it != 0L } ?: id)
        val saved = alarmDao.getById(id)!!
        val group = groupDao.getById(saved.groupId)
        if (saved.enabled && group?.enabled == true) scheduler.scheduleAlarm(saved)
        return id
    }

    suspend fun deleteAlarm(item: AlarmItem) {
        scheduler.cancelAlarm(item.id)
        alarmDao.delete(item)
    }

    /** アラーム ON/OFF → 予約復元/キャンセル。 */
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean) {
        alarmDao.setEnabled(alarmId, enabled, System.currentTimeMillis())
        if (enabled) {
            val a = alarmDao.getById(alarmId) ?: return
            val g = groupDao.getById(a.groupId)
            if (g?.enabled == true) scheduler.scheduleAlarm(a)
        } else {
            scheduler.cancelAlarm(alarmId)
        }
    }

    // ---- ログ ----
    suspend fun log(log: AlarmEventLog) { logDao.insert(log) }

    suspend fun rescheduleAll(reason: String) = scheduler.rescheduleAll(reason)
}
