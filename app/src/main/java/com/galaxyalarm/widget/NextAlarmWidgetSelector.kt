package com.galaxyalarm.widget

import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.OccurrenceStatus

internal data class WidgetAlarmCandidate(
    val alarm: AlarmItem,
    val groupName: String,
    val triggerAtMillis: Long,
)

internal fun selectNextWidgetAlarm(
    alarms: List<AlarmItem>,
    groups: List<AlarmGroup>,
    occurrences: List<ScheduledOccurrence>,
    now: Long,
): WidgetAlarmCandidate? {
    val alarmsById = alarms.associateBy { it.id }
    val groupsById = groups.associateBy { it.id }

    return occurrences.asSequence()
        .filter { occurrence ->
            occurrence.status == OccurrenceStatus.SCHEDULED &&
                occurrence.triggerAtMillis > now
        }
        .mapNotNull { occurrence ->
            val alarm = alarmsById[occurrence.alarmId] ?: return@mapNotNull null
            val group = groupsById[alarm.groupId] ?: return@mapNotNull null
            val canStillRing = alarm.enabled || occurrence.snoozeCount > 0
            if (!canStillRing || !group.enabled) return@mapNotNull null

            WidgetAlarmCandidate(
                alarm = alarm,
                groupName = group.name,
                triggerAtMillis = occurrence.triggerAtMillis,
            )
        }
        .minByOrNull { it.triggerAtMillis }
}
