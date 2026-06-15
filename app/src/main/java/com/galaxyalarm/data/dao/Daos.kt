package com.galaxyalarm.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.OccurrenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmGroupDao {
    @Insert suspend fun insert(group: AlarmGroup): Long
    @Update suspend fun update(group: AlarmGroup)
    @Delete suspend fun delete(group: AlarmGroup)
    @Query("SELECT * FROM alarm_groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AlarmGroup>>
    @Query("SELECT * FROM alarm_groups ORDER BY sortOrder, id")
    suspend fun getAll(): List<AlarmGroup>
    @Query("SELECT * FROM alarm_groups WHERE id = :id")
    suspend fun getById(id: Long): AlarmGroup?
    @Query("UPDATE alarm_groups SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)
    @Query("SELECT COUNT(*) FROM alarm_groups")
    suspend fun count(): Int
}

@Dao
interface AlarmItemDao {
    @Insert suspend fun insert(item: AlarmItem): Long
    @Update suspend fun update(item: AlarmItem)
    @Delete suspend fun delete(item: AlarmItem)
    @Query("SELECT * FROM alarm_items ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmItem>>
    @Query("SELECT * FROM alarm_items ORDER BY hour, minute, id")
    suspend fun getAll(): List<AlarmItem>
    @Query("SELECT * FROM alarm_items WHERE id = :id")
    suspend fun getById(id: Long): AlarmItem?
    @Query("SELECT * FROM alarm_items WHERE groupId = :groupId ORDER BY hour, minute, id")
    suspend fun getByGroup(groupId: Long): List<AlarmItem>
    @Query("UPDATE alarm_items SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)
    @Query("UPDATE alarm_items SET enabled = :enabled, updatedAt = :now WHERE groupId = :groupId")
    suspend fun setEnabledForGroup(groupId: Long, enabled: Boolean, now: Long)
    @Query("SELECT COUNT(*) FROM alarm_items WHERE groupId = :groupId AND enabled = 1")
    suspend fun enabledCountInGroup(groupId: Long): Int
    @Query("SELECT COUNT(*) FROM alarm_items")
    suspend fun count(): Int
}

@Dao
interface ScheduledOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occ: ScheduledOccurrence): Long
    @Update suspend fun update(occ: ScheduledOccurrence)
    @Delete suspend fun delete(occ: ScheduledOccurrence)
    @Query("SELECT * FROM scheduled_occurrences WHERE id = :id")
    suspend fun getById(id: Long): ScheduledOccurrence?
    @Query("SELECT * FROM scheduled_occurrences WHERE requestCode = :rc LIMIT 1")
    suspend fun getByRequestCode(rc: Int): ScheduledOccurrence?
    @Query("SELECT * FROM scheduled_occurrences WHERE status = :status")
    suspend fun getByStatus(status: OccurrenceStatus): List<ScheduledOccurrence>
    @Query("SELECT * FROM scheduled_occurrences WHERE alarmId = :alarmId AND status = 'SCHEDULED'")
    suspend fun getScheduledForAlarm(alarmId: Long): List<ScheduledOccurrence>
    @Query("SELECT * FROM scheduled_occurrences WHERE groupId = :groupId AND status = 'SCHEDULED'")
    suspend fun getScheduledForGroup(groupId: Long): List<ScheduledOccurrence>
    @Query("SELECT * FROM scheduled_occurrences WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis")
    suspend fun getAllScheduled(): List<ScheduledOccurrence>
    @Query("SELECT * FROM scheduled_occurrences WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis")
    fun observeScheduled(): Flow<List<ScheduledOccurrence>>
    @Query("SELECT MAX(requestCode) FROM scheduled_occurrences")
    suspend fun maxRequestCode(): Int?
    @Query("UPDATE scheduled_occurrences SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: OccurrenceStatus, now: Long)
    @Query("DELETE FROM scheduled_occurrences WHERE status IN ('FIRED','CANCELED','FAILED') AND updatedAt < :before")
    suspend fun purgeOld(before: Long)
}

@Dao
interface AlarmEventLogDao {
    @Insert suspend fun insert(log: AlarmEventLog): Long
    @Query("SELECT * FROM alarm_event_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<AlarmEventLog>>
    @Query("SELECT * FROM alarm_event_logs ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): AlarmEventLog?
    @Query("DELETE FROM alarm_event_logs WHERE createdAt < :before")
    suspend fun purgeOld(before: Long)
}
