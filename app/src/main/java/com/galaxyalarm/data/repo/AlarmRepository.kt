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

data class AlarmSaveResult(
    val alarmId: Long,
    val scheduled: Boolean,
)

class AlarmRepository(
    private val groupDao: AlarmGroupDao,
    private val alarmDao: AlarmItemDao,
    private val occurrenceDao: ScheduledOccurrenceDao,
    private val logDao: AlarmEventLogDao,
    private val scheduler: AlarmScheduler,
) {
    fun observeGroups(): Flow<List<AlarmGroup>> = groupDao.observeAll()
    fun observeAlarms(): Flow<List<AlarmItem>> = alarmDao.observeAll()
    fun observeScheduled(): Flow<List<ScheduledOccurrence>> = occurrenceDao.observeScheduled()
    fun observeLogs() = logDao.observeRecent()

    suspend fun getGroups() = groupDao.getAll()
    suspend fun getVisibleGroups() = groupDao.getAll().filterNot { isDefaultGroupName(it.name) }
    suspend fun getAlarms() = alarmDao.getAll()
    suspend fun getAlarm(id: Long) = alarmDao.getById(id)
    suspend fun getGroup(id: Long) = groupDao.getById(id)
    suspend fun getAllScheduled() = occurrenceDao.getAllScheduled()
    suspend fun enabledCountInGroup(groupId: Long) = alarmDao.enabledCountInGroup(groupId)
    suspend fun alarmCount() = alarmDao.count()
    suspend fun groupCount() = getVisibleGroups().size
    suspend fun latestLog() = logDao.latest()
    fun hasSystemReservation(occurrence: ScheduledOccurrence) =
        scheduler.hasSystemReservation(occurrence)

    suspend fun addGroup(name: String): Long {
        val order = getVisibleGroups().size
        return groupDao.insert(AlarmGroup(name = name, sortOrder = order))
    }

    suspend fun renameGroup(group: AlarmGroup, name: String) {
        if (isDefaultGroupName(group.name)) return
        groupDao.update(group.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGroup(group: AlarmGroup) {
        if (isDefaultGroupName(group.name)) return
        scheduler.cancelGroup(group.id)
        groupDao.delete(group)
    }

    /**
     * グループONは各アラームのOS予約成功を確認してからDBをONにする。
     * 予約失敗したアラームはOFFのまま残し、画面だけONになる状態を作らない。
     */
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean): Boolean {
        val group = groupDao.getById(groupId)
        if (group != null && isDefaultGroupName(group.name)) return true

        val now = System.currentTimeMillis()
        if (!enabled) {
            scheduler.cancelGroup(groupId)
            alarmDao.setEnabledForGroup(groupId, false, now)
            groupDao.setEnabled(groupId, false, now)
            return true
        }

        groupDao.setEnabled(groupId, true, now)
        var allScheduled = true
        alarmDao.getByGroup(groupId).forEach { alarm ->
            val candidate = alarm.copy(enabled = true, updatedAt = now)
            val scheduled = scheduler.replaceAlarm(candidate)
            if (scheduled) {
                alarmDao.setEnabled(alarm.id, true, now)
            } else {
                scheduler.cancelAlarm(alarm.id)
                alarmDao.setEnabled(alarm.id, false, now)
                allScheduled = false
            }
        }
        return allScheduled
    }

    suspend fun ensureDefaultGroup(): Long {
        val groups = groupDao.getAll()
        val ungrouped = groups.firstOrNull { isDefaultGroupName(it.name) }
            ?: return groupDao.insert(AlarmGroup(name = DEFAULT_GROUP_NAME, enabled = true, sortOrder = Int.MIN_VALUE))
        if (!ungrouped.enabled || ungrouped.name != DEFAULT_GROUP_NAME) {
            groupDao.update(
                ungrouped.copy(
                    name = DEFAULT_GROUP_NAME,
                    enabled = true,
                    sortOrder = Int.MIN_VALUE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return ungrouped.id
    }

    suspend fun ensureImagePresetGroups(): Pair<Int, Int> {
        val presets = listOf(
            PresetGroup("電車内", listOf(
                PresetAlarm(0, 45),
                PresetAlarm(7, 48),
                PresetAlarm(8, 30, "ロマンスカー"),
                PresetAlarm(9, 30),
                PresetAlarm(12, 0),
                PresetAlarm(12, 59),
                PresetAlarm(13, 0),
            )),
            PresetGroup("ロマンスカー 8:31発", listOf(
                PresetAlarm(7, 15),
                PresetAlarm(7, 20),
                PresetAlarm(7, 25),
                PresetAlarm(7, 40),
                PresetAlarm(8, 28),
                PresetAlarm(9, 23, "音なし", SoundMode.VIBRATE_ONLY),
            )),
            PresetGroup("ホテル", listOf(
                PresetAlarm(8, 46),
                PresetAlarm(8, 50),
                PresetAlarm(8, 55),
                PresetAlarm(9, 0),
                PresetAlarm(9, 15),
            )),
            PresetGroup("在宅", listOf(
                PresetAlarm(8, 35),
                PresetAlarm(8, 40),
                PresetAlarm(8, 45),
                PresetAlarm(12, 58),
            )),
            PresetGroup("午前在宅", listOf(
                PresetAlarm(8, 15),
                PresetAlarm(8, 20),
                PresetAlarm(8, 25),
                PresetAlarm(8, 30),
            )),
            // 飛行機(フライト用)。時刻は未定のため空グループとして用意。
            PresetGroup("飛行機", emptyList()),
        )

        val groupsByName = groupDao.getAll().associateBy { it.name }.toMutableMap()
        val alarms = alarmDao.getAll().toMutableList()
        var insertedGroups = 0
        var insertedAlarms = 0

        presets.forEachIndexed { index, preset ->
            val group = groupsByName[preset.name] ?: run {
                val id = groupDao.insert(
                    AlarmGroup(name = preset.name, enabled = true, sortOrder = groupsByName.size + index)
                )
                insertedGroups += 1
                groupDao.getById(id)!!.also { groupsByName[preset.name] = it }
            }
            preset.alarms.forEach { presetAlarm ->
                val duplicate = alarms.any {
                    it.groupId == group.id &&
                        it.hour == presetAlarm.hour &&
                        it.minute == presetAlarm.minute &&
                        it.label == presetAlarm.label
                }
                if (!duplicate) {
                    val id = alarmDao.insert(
                        AlarmItem(
                            groupId = group.id,
                            label = presetAlarm.label,
                            hour = presetAlarm.hour,
                            minute = presetAlarm.minute,
                            enabled = false,
                            soundMode = presetAlarm.soundMode,
                            vibrationEnabled = true
                        )
                    )
                    alarmDao.getById(id)?.let { alarms += it }
                    insertedAlarms += 1
                }
            }
        }
        return insertedGroups to insertedAlarms
    }

    /** Compatibility wrapper for callers that only need the id. */
    suspend fun saveAlarm(item: AlarmItem): Long = saveAlarmChecked(item).alarmId

    /**
     * ONで保存する場合は、OS予約成功を確認してから enabled=true を永続化する。
     * 既存アラームの予約失敗時は元データと元予約を保持する。新規作成の失敗時は仮行を削除する。
     */
    suspend fun saveAlarmChecked(item: AlarmItem): AlarmSaveResult {
        val normalized = item.withSafeSoundMode()
        val wantsEnabled = normalized.enabled
        val now = System.currentTimeMillis()

        if (!wantsEnabled) {
            val id = if (normalized.id == 0L) {
                alarmDao.insert(normalized.copy(enabled = false))
            } else {
                scheduler.cancelAlarm(normalized.id)
                alarmDao.update(normalized.copy(enabled = false, updatedAt = now))
                normalized.id
            }
            return AlarmSaveResult(id, true)
        }

        if (normalized.id == 0L) {
            // AlarmManagerのrequestを作るにはalarmIdが必要なので、OFF状態の仮行だけ先に作る。
            val stagedId = alarmDao.insert(normalized.copy(enabled = false))
            val candidate = normalized.copy(id = stagedId, enabled = true, updatedAt = now)
            enableGroupForAlarm(candidate.groupId)
            val scheduled = scheduler.replaceAlarm(candidate)
            if (!scheduled) {
                runCatching { scheduler.cancelAlarm(stagedId) }
                alarmDao.getById(stagedId)?.let { alarmDao.delete(it) }
                return AlarmSaveResult(0L, false)
            }
            return try {
                alarmDao.update(candidate)
                AlarmSaveResult(stagedId, true)
            } catch (error: Exception) {
                runCatching { scheduler.cancelAlarm(stagedId) }
                alarmDao.getById(stagedId)?.let { runCatching { alarmDao.delete(it) } }
                throw error
            }
        }

        val original = alarmDao.getById(normalized.id)
            ?: return AlarmSaveResult(normalized.id, false)
        val candidate = normalized.copy(enabled = true, updatedAt = now)
        enableGroupForAlarm(candidate.groupId)
        val scheduled = scheduler.replaceAlarm(candidate)
        if (!scheduled) {
            // replaceAlarmは失敗時に旧予約を保持するためDBも元のままにする。
            return AlarmSaveResult(original.id, false)
        }

        return try {
            alarmDao.update(candidate)
            AlarmSaveResult(candidate.id, true)
        } catch (error: Exception) {
            // DB更新だけ失敗した場合は、可能な限り元の予約状態へ戻す。
            runCatching {
                if (original.enabled) scheduler.replaceAlarm(original) else scheduler.cancelAlarm(original.id)
            }
            throw error
        }
    }

    suspend fun deleteAlarm(item: AlarmItem) {
        scheduler.cancelAlarm(item.id)
        alarmDao.delete(item)
    }

    /** ONは予約成功後、OFFは予約取消成功後にDBへ反映する。 */
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean): Boolean {
        val alarm = alarmDao.getById(alarmId) ?: return false
        val now = System.currentTimeMillis()

        if (!enabled) {
            scheduler.cancelAlarm(alarmId)
            alarmDao.setEnabled(alarmId, false, now)
            return true
        }

        enableGroupForAlarm(alarm.groupId)
        val candidate = alarm.copy(enabled = true, updatedAt = now)
        val scheduled = scheduler.replaceAlarm(candidate)
        if (!scheduled) return false

        return try {
            alarmDao.setEnabled(alarmId, true, now)
            true
        } catch (error: Exception) {
            runCatching { scheduler.cancelAlarm(alarmId) }
            throw error
        }
    }

    suspend fun log(log: AlarmEventLog) { logDao.insert(log) }

    suspend fun rescheduleAll(reason: String) {
        enableGroupsForEnabledAlarms()
        scheduler.rescheduleAll(reason)
    }

    suspend fun recalculateRegularAlarms(reason: String) {
        enableGroupsForEnabledAlarms()
        scheduler.recalculateRegularAlarms(reason)
    }

    suspend fun exportBackupJson(): String {
        val groups = groupDao.getAll()
        val alarms = alarmDao.getAll()
        val groupNames = groups.associate { it.id to it.name }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("groups", JSONArray().apply {
                groups.filterNot { isDefaultGroupName(it.name) }.forEach { group ->
                    put(JSONObject()
                        .put("localId", group.id)
                        .put("name", group.name)
                        .put("enabled", group.enabled)
                        .put("sortOrder", group.sortOrder))
                }
            })
            .put("alarms", JSONArray().apply {
                alarms.forEach { alarm ->
                    put(JSONObject()
                        .put("groupLocalId", alarm.groupId)
                        .put("groupName", groupNames[alarm.groupId] ?: DEFAULT_GROUP_NAME)
                        .put("label", alarm.label)
                        .put("hour", alarm.hour)
                        .put("minute", alarm.minute)
                        .put("weekdaysMask", alarm.weekdaysMask)
                        .put("enabled", alarm.enabled)
                        .put("soundMode", alarm.soundMode.name)
                        .put("ringtoneUri", alarm.ringtoneUri)
                        .put("vibrationEnabled", alarm.vibrationEnabled)
                        .put("vibrationPattern", alarm.vibrationPattern.name)
                        .put("snoozeEnabled", alarm.snoozeEnabled)
                        .put("snoozeMinutes", alarm.snoozeMinutes)
                        .put("maxSnoozeCount", alarm.maxSnoozeCount)
                        .put("autoStopMinutes", alarm.autoStopMinutes))
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
            val groupJson = groupsJson.getJSONObject(i)
            val oldId = groupJson.optLong("localId", 0L)
            val name = groupJson.optString("name").ifBlank { DEFAULT_GROUP_NAME }
            if (isDefaultGroupName(name)) {
                groupIdMap[oldId] = ensureDefaultGroup()
                continue
            }
            val local = groupsByName[name] ?: run {
                val id = groupDao.insert(
                    AlarmGroup(
                        name = name,
                        enabled = groupJson.optBoolean("enabled", true),
                        sortOrder = groupJson.optInt("sortOrder", groupsByName.size)
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
            val alarmJson = alarmsJson.getJSONObject(i)
            val groupName = alarmJson.optString("groupName").ifBlank { DEFAULT_GROUP_NAME }
            val groupId = groupIdMap[alarmJson.optLong("groupLocalId", 0L)]
                ?: groupsByName[groupName]?.id
                ?: ensureDefaultGroup()
            val label = alarmJson.optString("label")
            val hour = alarmJson.optInt("hour")
            val minute = alarmJson.optInt("minute")
            val weekdaysMask = alarmJson.optInt("weekdaysMask")
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
                enabled = alarmJson.optBoolean("enabled", true),
                soundMode = enumValueOrDefault(alarmJson.optString("soundMode"), SoundMode.SOUND),
                ringtoneUri = alarmJson.optString("ringtoneUri").ifBlank { null },
                vibrationEnabled = alarmJson.optBoolean("vibrationEnabled", true),
                vibrationPattern = enumValueOrDefault(alarmJson.optString("vibrationPattern"), VibrationPattern.SHORT),
                snoozeEnabled = alarmJson.optBoolean("snoozeEnabled", true),
                snoozeMinutes = alarmJson.optInt("snoozeMinutes", 5),
                maxSnoozeCount = alarmJson.optInt("maxSnoozeCount", 3),
                autoStopMinutes = alarmJson.optInt("autoStopMinutes", 5)
            ).withSafeSoundMode()
            val id = alarmDao.insert(item)
            alarmDao.getById(id)?.let { existingAlarms += it }
            insertedAlarms += 1
        }
        rescheduleAll("github-backup-restore")
        return insertedGroups to insertedAlarms
    }

    private suspend fun enableGroupsForEnabledAlarms() {
        val groups = groupDao.getAll().associateBy { it.id }
        val enabledGroupIds = alarmDao.getAll().filter { it.enabled }.map { it.groupId }.toSet()
        val now = System.currentTimeMillis()
        enabledGroupIds.forEach { groupId ->
            val group = groups[groupId] ?: return@forEach
            if (!group.enabled || isDefaultGroupName(group.name)) {
                groupDao.setEnabled(group.id, true, now)
            }
        }
    }

    private suspend fun enableGroupForAlarm(groupId: Long) {
        val group = groupDao.getById(groupId) ?: return
        if (!group.enabled || isDefaultGroupName(group.name)) {
            groupDao.setEnabled(group.id, true, System.currentTimeMillis())
        }
    }

    private fun AlarmItem.withSafeSoundMode(): AlarmItem =
        if (soundMode == SoundMode.SILENT || soundMode == SoundMode.VIBRATE_ONLY) {
            copy(soundMode = SoundMode.VIBRATE_ONLY, vibrationEnabled = true)
        } else {
            this
        }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private data class PresetGroup(
        val name: String,
        val alarms: List<PresetAlarm>,
    )

    private data class PresetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String = "",
        val soundMode: SoundMode = SoundMode.SOUND,
    )

    companion object {
        const val DEFAULT_GROUP_NAME = "グループなし"
        private val LEGACY_DEFAULT_GROUP_NAMES = setOf(
            DEFAULT_GROUP_NAME,
            "既定グループ",
            "デフォルト",
            "Default",
            "譌｢螳壹げ繝ｫ繝ｼ繝・",
            "繝・ヵ繧ｩ繝ｫ繝・",
            "隴鯉ｽ｢陞ｳ螢ｹ縺堤ｹ晢ｽｫ郢晢ｽｼ郢昴・"
        )

        fun isDefaultGroupName(name: String): Boolean = name in LEGACY_DEFAULT_GROUP_NAMES
    }
}
