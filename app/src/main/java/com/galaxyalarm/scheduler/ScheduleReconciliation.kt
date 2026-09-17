package com.galaxyalarm.scheduler

import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence

internal data class SchedulePlan(
    val cancel: List<ScheduledOccurrence>,
    val reassert: List<ScheduledOccurrence>,
    val create: List<AlarmItem>,
)

/** Pure policy for deciding which existing AlarmManager entries may be replaced. */
internal object ScheduleReconciliation {
    fun plan(
        groups: List<AlarmGroup>,
        alarms: List<AlarmItem>,
        occurrences: List<ScheduledOccurrence>,
        now: Long,
        lateDeliveryGraceMs: Long,
    ): SchedulePlan {
        val groupsById = groups.associateBy { it.id }
        val alarmsById = alarms.associateBy { it.id }
        val activeAlarms = alarms.filter { alarm ->
            alarm.enabled && groupsById[alarm.groupId]?.enabled == true
        }
        val activeIds = activeAlarms.mapTo(mutableSetOf()) { it.id }
        val cutoff = now - lateDeliveryGraceMs
        val cancel = mutableListOf<ScheduledOccurrence>()
        val reassert = mutableListOf<ScheduledOccurrence>()

        // A pending snooze is a durable user request in its own right. One-shot alarms are
        // automatically disabled after the primary fire, so alarm.enabled must not be used
        // to invalidate a still-scheduled snooze. An explicit user OFF goes through
        // AlarmRepository.setAlarmEnabled(false), which cancels the scheduled snooze itself.
        occurrences.filter { it.snoozeCount > 0 }.forEach { occurrence ->
            val alarm = alarmsById[occurrence.alarmId]
            val groupEnabled = alarm?.let { groupsById[it.groupId]?.enabled == true } == true
            if (alarm == null || !groupEnabled || occurrence.triggerAtMillis < cutoff) {
                cancel += occurrence
            } else {
                reassert += occurrence
            }
        }

        val regularByAlarm = occurrences.filter { it.snoozeCount == 0 }.groupBy { it.alarmId }
        val create = mutableListOf<AlarmItem>()
        activeAlarms.forEach { alarm ->
            val candidates = regularByAlarm[alarm.id].orEmpty()
                .filter { it.triggerAtMillis >= cutoff }
                .sortedBy { it.triggerAtMillis }
            val keep = candidates.firstOrNull()
            if (keep == null) create += alarm else reassert += keep
            cancel += regularByAlarm[alarm.id].orEmpty().filter { it.id != keep?.id }
        }

        // Only regular occurrences depend on alarm.enabled. Snoozes were handled above.
        occurrences.filter { it.snoozeCount == 0 && it.alarmId !in activeIds }.forEach { occurrence ->
            if (cancel.none { it.id == occurrence.id }) cancel += occurrence
        }

        return SchedulePlan(
            cancel = cancel.distinctBy { it.id },
            reassert = reassert.distinctBy { it.id },
            create = create,
        )
    }
}
