package com.galaxyalarm.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern

@Entity(tableName = "alarm_groups")
data class AlarmGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "alarm_items",
    foreignKeys = [ForeignKey(
        entity = AlarmGroup::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("groupId")]
)
data class AlarmItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,                       // 必ず1グループに属する
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val weekdaysMask: Int = 0,               // 0 = 一度きり
    val enabled: Boolean = true,
    val soundMode: SoundMode = SoundMode.SOUND,
    val ringtoneUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.SHORT,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozeCount: Int = 3,
    val autoStopMinutes: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "scheduled_occurrences",
    indices = [Index("alarmId"), Index("requestCode", unique = true), Index("status")]
)
data class ScheduledOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val groupId: Long,
    val triggerAtMillis: Long,
    val requestCode: Int,                    // PendingIntent 一意キー
    val status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    val snoozeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alarm_event_logs", indices = [Index("createdAt")])
data class AlarmEventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long?,
    val groupId: Long?,
    val scheduledAtMillis: Long?,
    val firedAtMillis: Long?,
    val delayMs: Long?,
    val result: EventResult,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
