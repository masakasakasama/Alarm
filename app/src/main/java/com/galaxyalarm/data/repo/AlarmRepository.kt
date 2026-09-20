package com.galaxyalarm.data.repo

import com.galaxyalarm.data.dao.AlarmEventLogDao
import com.galaxyalarm.data.dao.AlarmGroupDao
import com.galaxyalarm.data.dao.AlarmItemDao
import com.galaxyalarm.data.dao.ScheduledOccurrenceDao
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.data.model.AlarmDefinitionKey
import com.galaxyalarm.data.model.validateForSave
import com.galaxyalarm.scheduler.AlarmScheduler
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class AlarmSaveResult(
    val alarmId: Long,
    val scheduled: Boolean,
    val duplicateOf: Long? = null,
    val error: String? = null,
)

class AlarmRepository(
    private val database: AppDatabase,
    private val groupDao: AlarmGroupDao,
    private val alarmDao: AlarmItemDao,
    private val occurrenceDao: ScheduledOccurrenceDao,
    private val logDao: AlarmEventLogDao,
    private val scheduler: AlarmScheduler,
) {
    private val saveMutex = Mutex()
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
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean): Boolean = saveMutex.withLock {
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

        return saveMutex.withLock {
            database.withTransaction {
                val groupsByName = groupDao.getAll().associateBy { it.name }.toMutableMap()
                val ungroupedIds = groupsByName.values.filter { isDefaultGroupName(it.name) }.mapTo(mutableSetOf()) { it.id }
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
                        val candidate = AlarmItem(
                            groupId = group.id,
                            label = presetAlarm.label,
                            hour = presetAlarm.hour,
                            minute = presetAlarm.minute,
                            enabled = false,
                            soundMode = presetAlarm.soundMode,
                            vibrationEnabled = true,
                        ).withSafeSoundMode()
                        if (findDuplicate(alarms, candidate, ungroupedIds, candidate.id) == null) {
                            val id = alarmDao.insert(candidate)
                            alarmDao.getById(id)?.let { alarms += it }
                            insertedAlarms += 1
                        }
                    }
                }
                insertedGroups to insertedAlarms
            }
        }
    }

    /** Compatibility wrapper for callers that only need the id. */
    suspend fun saveAlarm(item: AlarmItem): Long = saveAlarmChecked(item).alarmId

    /**
     * ONで保存する場合は、OS予約成功を確認してから enabled=true を永続化する。
     * 既存アラームの予約失敗時は元データと元予約を保持する。新規作成の失敗時は仮行を削除する。
     */
    suspend fun saveAlarmChecked(item: AlarmItem): AlarmSaveResult = saveMutex.withLock {
        val normalized = item.withSafeSoundMode().copy(
            ringtoneUri = item.ringtoneUri?.takeIf(String::isNotBlank)
        )
        normalized.validateForSave()?.let { return@withLock AlarmSaveResult(item.id, false, error = it) }
        val wantsEnabled = normalized.enabled
        val now = System.currentTimeMillis()
        val ungroupedIds = groupDao.getAll().filter { isDefaultGroupName(it.name) }.mapTo(mutableSetOf()) { it.id }
        if (groupDao.getById(normalized.groupId) == null) {
            return@withLock AlarmSaveResult(item.id, false, error = "選択したグループが見つかりません。")
        }

        if (!wantsEnabled) {
            val preview = database.withTransaction {
                val current = if (normalized.id > 0L) alarmDao.getById(normalized.id) else null
                if (normalized.id > 0L && current == null) return@withTransaction SaveCheck.Invalid
                val duplicate = findDuplicate(alarmDao.getAll(), normalized, ungroupedIds, normalized.id)
                if (duplicate != null) return@withTransaction SaveCheck.Duplicate(duplicate.id)
                SaveCheck.Ready
            }
            when (preview) {
                is SaveCheck.Duplicate -> return@withLock AlarmSaveResult(item.id, false, duplicateOf = preview.id)
                SaveCheck.Invalid -> return@withLock AlarmSaveResult(item.id, false, error = "アラームが見つかりません。")
                else -> Unit
            }
            if (normalized.id > 0L) scheduler.cancelAlarm(normalized.id)
            val saved = database.withTransaction {
                val duplicate = findDuplicate(alarmDao.getAll(), normalized, ungroupedIds, normalized.id)
                if (duplicate != null) return@withTransaction SaveCheck.Duplicate(duplicate.id)
                val id = if (normalized.id == 0L) alarmDao.insert(normalized.copy(enabled = false))
                else {
                    alarmDao.update(normalized.copy(enabled = false, updatedAt = now))
                    normalized.id
                }
                SaveCheck.Saved(id)
            }
            return@withLock saved.toResult()
        }

        val current = if (normalized.id > 0L) alarmDao.getById(normalized.id) else null
        if (normalized.id > 0L && current == null) {
            return@withLock AlarmSaveResult(normalized.id, false, error = "編集対象のアラームが見つかりません。")
        }
        val candidate = normalized.copy(enabled = true, updatedAt = now)
        val checked = database.withTransaction {
            val duplicate = findDuplicate(alarmDao.getAll(), candidate, ungroupedIds, candidate.id)
            if (duplicate != null) return@withTransaction SaveCheck.Duplicate(duplicate.id)
            if (candidate.id == 0L) {
                // Reserve a unique row before scheduling; keep it OFF until the OS accepts it.
                SaveCheck.Staged(alarmDao.insert(candidate.copy(enabled = false)))
            } else {
                SaveCheck.Ready
            }
        }
        when (checked) {
            is SaveCheck.Duplicate -> return@withLock AlarmSaveResult(item.id, false, duplicateOf = checked.id)
            SaveCheck.Invalid -> return@withLock AlarmSaveResult(item.id, false, error = "編集対象のアラームが見つかりません。")
            else -> Unit
        }

        val id = when (checked) {
            is SaveCheck.Staged -> checked.id
            else -> candidate.id
        }
        val scheduledAlarm = candidate.copy(id = id)
        enableGroupForAlarm(scheduledAlarm.groupId)
        if (!scheduler.replaceAlarm(scheduledAlarm)) {
            if (checked is SaveCheck.Staged) {
                runCatching { scheduler.cancelAlarm(id) }
                database.withTransaction { alarmDao.getById(id)?.let { alarmDao.delete(it) } }
            }
            return@withLock AlarmSaveResult(if (checked is SaveCheck.Staged) 0L else id, false)
        }

        try {
            database.withTransaction { alarmDao.update(scheduledAlarm) }
            AlarmSaveResult(id, true)
        } catch (error: Exception) {
            runCatching {
                if (current?.enabled == true) scheduler.replaceAlarm(current) else scheduler.cancelAlarm(id)
            }
            if (checked is SaveCheck.Staged) {
                database.withTransaction { alarmDao.getById(id)?.let { alarmDao.delete(it) } }
            }
            throw error
        }
    }

    private suspend fun findDuplicate(
        existing: List<AlarmItem>,
        candidate: AlarmItem,
        ungroupedIds: Set<Long>,
        excludingId: Long,
    ): AlarmItem? {
        fun key(alarm: AlarmItem) = AlarmDefinitionKey.from(
            alarm.withSafeSoundMode(),
            groupIdentity = if (alarm.groupId in ungroupedIds) UNGROUPED_IDENTITY else alarm.groupId,
        )
        return existing.firstOrNull { it.id != excludingId && key(it) == key(candidate) }
    }

    private sealed interface SaveCheck {
        data object Ready : SaveCheck
        data class Staged(val id: Long) : SaveCheck
        data class Saved(val id: Long) : SaveCheck
        data class Duplicate(val id: Long) : SaveCheck
        data object Invalid : SaveCheck
    }

    private fun SaveCheck.toResult(): AlarmSaveResult = when (this) {
        is SaveCheck.Saved -> AlarmSaveResult(id, true)
        is SaveCheck.Duplicate -> AlarmSaveResult(0L, false, duplicateOf = id)
        SaveCheck.Invalid -> AlarmSaveResult(0L, false, error = "アラームが見つかりません。")
        else -> error("Unexpected save check result")
    }

    /**
     * Removes definitions that are identical apart from their current ON/OFF state.
     * An enabled alarm wins so an update never silently disables a working alarm.
     */
    suspend fun consolidateExactDuplicates(): Int = saveMutex.withLock {
        val groups = groupDao.getAll()
        val ungroupedIds = groups.filter { isDefaultGroupName(it.name) }.mapTo(mutableSetOf()) { it.id }
        fun key(alarm: AlarmItem) = AlarmDefinitionKey.from(
            alarm.withSafeSoundMode(),
            groupIdentity = if (alarm.groupId in ungroupedIds) UNGROUPED_IDENTITY else alarm.groupId,
        )
        val duplicates = alarmDao.getAll()
            .groupBy(::key)
            .values
            .filter { it.size > 1 }
            .flatMap { matches ->
                val keeper = matches.sortedWith(
                    compareByDescending<AlarmItem> { it.enabled }.thenBy { it.id }
                ).first()
                matches.filter { it.id != keeper.id }
            }

        duplicates.forEach { scheduler.cancelAlarm(it.id) }
        database.withTransaction {
            duplicates.forEach { duplicate ->
                alarmDao.getById(duplicate.id)?.let { alarmDao.delete(it) }
            }
        }
        duplicates.size
    }

    suspend fun deleteAlarm(item: AlarmItem) {
        scheduler.cancelAlarm(item.id)
        alarmDao.delete(item)
    }

    /** ONは予約成功後、OFFは予約取消成功後にDBへ反映する。 */
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean): Boolean = saveMutex.withLock {
        setAlarmEnabledLocked(alarmId, enabled)
    }

    private suspend fun setAlarmEnabledLocked(alarmId: Long, enabled: Boolean): Boolean {
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
                        .put("autoStopMinutes", alarm.autoStopMinutes)
                        .put("fadeInSeconds", alarm.fadeInSeconds))
                }
            })
            .toString()
    }

    suspend fun mergeBackupJson(jsonText: String): Pair<Int, Int> = saveMutex.withLock {
        val json = JSONObject(jsonText)
        val groupsJson = json.optJSONArray("groups") ?: JSONArray()
        val alarmsJson = json.optJSONArray("alarms") ?: JSONArray()
        val imported = database.withTransaction {
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

            val ungroupedIds = groupDao.getAll().filter { isDefaultGroupName(it.name) }.mapTo(mutableSetOf()) { it.id }
            val existingAlarms = alarmDao.getAll().toMutableList()
            val pendingScheduleIds = mutableListOf<Long>()
            var insertedAlarms = 0
            for (i in 0 until alarmsJson.length()) {
                val alarmJson = alarmsJson.getJSONObject(i)
                val groupName = alarmJson.optString("groupName").ifBlank { DEFAULT_GROUP_NAME }
                val groupId = groupIdMap[alarmJson.optLong("groupLocalId", 0L)]
                    ?: groupsByName[groupName]?.id
                    ?: ensureDefaultGroup()
                val desiredEnabled = alarmJson.optBoolean("enabled", true)
                val ringtoneUri = if (alarmJson.isNull("ringtoneUri")) null
                    else alarmJson.optString("ringtoneUri").takeIf(String::isNotBlank)
                val item = AlarmItem(
                    groupId = groupId,
                    label = alarmJson.optString("label"),
                    hour = alarmJson.optInt("hour"),
                    minute = alarmJson.optInt("minute"),
                    weekdaysMask = alarmJson.optInt("weekdaysMask"),
                    enabled = desiredEnabled,
                    soundMode = enumValueFromBackup(alarmJson.optString("soundMode"), SoundMode.SOUND),
                    ringtoneUri = ringtoneUri,
                    vibrationEnabled = alarmJson.optBoolean("vibrationEnabled", true),
                    vibrationPattern = enumValueFromBackup(alarmJson.optString("vibrationPattern"), VibrationPattern.SHORT),
                    snoozeEnabled = alarmJson.optBoolean("snoozeEnabled", true),
                    snoozeMinutes = alarmJson.optInt("snoozeMinutes", 5),
                    maxSnoozeCount = alarmJson.optInt("maxSnoozeCount", 3),
                    autoStopMinutes = alarmJson.optInt("autoStopMinutes", 5),
                    fadeInSeconds = alarmJson.optInt("fadeInSeconds", 0),
                ).withSafeSoundMode()
                item.validateForSave()?.let { throw IllegalArgumentException("バックアップの設定が不正です: $it") }
                if (findDuplicate(existingAlarms, item, ungroupedIds, item.id) != null) continue

                val id = alarmDao.insert(item.copy(enabled = false))
                alarmDao.getById(id)?.let { existingAlarms += it.copy(enabled = desiredEnabled) }
                if (desiredEnabled) pendingScheduleIds += id
                insertedAlarms += 1
            }
            ImportResult(insertedGroups, insertedAlarms, pendingScheduleIds)
        }
        imported.pendingScheduleIds.forEach { setAlarmEnabledLocked(it, true) }
        rescheduleAll("github-backup-restore")
        imported.insertedGroups to imported.insertedAlarms
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

    private inline fun <reified T : Enum<T>> enumValueFromBackup(name: String, default: T): T =
        if (name.isBlank()) default else runCatching { enumValueOf<T>(name) }
            .getOrElse { throw IllegalArgumentException("バックアップに未対応の設定があります: $name") }

    private data class ImportResult(val insertedGroups: Int, val insertedAlarms: Int, val pendingScheduleIds: List<Long>)

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
        private const val UNGROUPED_IDENTITY = Long.MIN_VALUE
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
