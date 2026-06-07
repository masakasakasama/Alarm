package com.galaxyalarm.data.repo

import com.galaxyalarm.data.dao.AlarmEventLogDao
import com.galaxyalarm.data.dao.AlarmGroupDao
import com.galaxyalarm.data.dao.AlarmItemDao
import com.galaxyalarm.data.dao.ScheduledOccurrenceDao
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

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

    suspend fun exportBackupJson(): String {
        val groups = groupDao.getAll()
        val alarms = alarmDao.getAll()
        val groupNames = groups.associate { it.id to it.name }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("groups", JSONArray().apply {
                groups.forEach { g ->
                    put(JSONObject()
                        .put("localId", g.id)
                        .put("name", g.name)
                        .put("enabled", g.enabled)
                        .put("sortOrder", g.sortOrder))
                }
            })
            .put("alarms", JSONArray().apply {
                alarms.forEach { a ->
                    put(JSONObject()
                        .put("groupLocalId", a.groupId)
                        .put("groupName", groupNames[a.groupId] ?: "")
                        .put("label", a.label)
                        .put("hour", a.hour)
                        .put("minute", a.minute)
                        .put("weekdaysMask", a.weekdaysMask)
                        .put("enabled", a.enabled)
                        .put("soundMode", a.soundMode.name)
                        .put("ringtoneUri", a.ringtoneUri)
                        .put("vibrationEnabled", a.vibrationEnabled)
                        .put("vibrationPattern", a.vibrationPattern.name)
                        .put("snoozeEnabled", a.snoozeEnabled)
                        .put("snoozeMinutes", a.snoozeMinutes)
                        .put("maxSnoozeCount", a.maxSnoozeCount)
                        .put("autoStopMinutes", a.autoStopMinutes))
                }
            })
            .toString()
    }

    suspend fun mergeBackupJson(jsonText: String): Pair<Int, Int> {
        val json = JSONObject(jsonText)
        val groupsJson = json.optJSONArray("groups") ?: JSONArray()
        val alarmsJson = json.optJSONArray("alarms") ?: JSONArray()
        val groupIdMap = mutableMapOf<Long, Long>()
        var insertedGroups = 0
        val groupsByName = groupDao.getAll().associateBy { it.name }.toMutableMap()

        for (i in 0 until groupsJson.length()) {
            val g = groupsJson.getJSONObject(i)
            val oldId = g.optLong("localId", 0L)
            val name = g.optString("name").ifBlank { "既定グループ" }
            val local = groupsByName[name] ?: run {
                val id = groupDao.insert(
                    AlarmGroup(
                        name = name,
                        enabled = g.optBoolean("enabled", true),
                        sortOrder = g.optInt("sortOrder", groupsByName.size)
                    )
                )
                insertedGroups += 1
                groupDao.getById(id)!!.also { groupsByName[name] = it }
            }
            groupIdMap[oldId] = local.id
        }

        var insertedAlarms = 0
        val existingAlarms = alarmDao.getAll().toMutableList()
        for (i in 0 until alarmsJson.length()) {
            val a = alarmsJson.getJSONObject(i)
            val groupName = a.optString("groupName").ifBlank { "既定グループ" }
            val groupId = groupIdMap[a.optLong("groupLocalId", 0L)]
                ?: groupsByName[groupName]?.id
                ?: ensureDefaultGroup()
            val label = a.optString("label")
            val hour = a.optInt("hour")
            val minute = a.optInt("minute")
            val weekdaysMask = a.optInt("weekdaysMask")
            val duplicate = existingAlarms.any {
                it.groupId == groupId &&
                    it.label == label &&
                    it.hour == hour &&
                    it.minute == minute &&
                    it.weekdaysMask == weekdaysMask
            }
            if (duplicate) continue

            val item = AlarmItem(
                groupId = groupId,
                label = label,
                hour = hour,
                minute = minute,
                weekdaysMask = weekdaysMask,
                enabled = a.optBoolean("enabled", true),
                soundMode = enumValueOrDefault(a.optString("soundMode"), SoundMode.SOUND),
                ringtoneUri = a.optString("ringtoneUri").ifBlank { null },
                vibrationEnabled = a.optBoolean("vibrationEnabled", true),
                vibrationPattern = enumValueOrDefault(a.optString("vibrationPattern"), VibrationPattern.SHORT),
                snoozeEnabled = a.optBoolean("snoozeEnabled", true),
                snoozeMinutes = a.optInt("snoozeMinutes", 5),
                maxSnoozeCount = a.optInt("maxSnoozeCount", 3),
                autoStopMinutes = a.optInt("autoStopMinutes", 5)
            )
            val id = alarmDao.insert(item)
            alarmDao.getById(id)?.let { existingAlarms += it }
            insertedAlarms += 1
        }
        rescheduleAll("github-backup-restore")
        return insertedGroups to insertedAlarms
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
