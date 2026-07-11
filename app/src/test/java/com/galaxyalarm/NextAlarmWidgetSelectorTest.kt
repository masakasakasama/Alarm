package com.galaxyalarm

import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.widget.selectNextWidgetAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmWidgetSelectorTest {
    private val now = 1_000_000L
    private val enabledGroup = AlarmGroup(id = 10L, name = "Morning", enabled = true)
    private val alarm = AlarmItem(
        id = 20L,
        groupId = enabledGroup.id,
        hour = 6,
        minute = 0,
        enabled = true,
    )

    @Test
    fun endedOccurrenceIsNeverShownAsNextAlarm() {
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm),
            groups = listOf(enabledGroup),
            occurrences = listOf(occurrence(id = 1L, alarmId = alarm.id, triggerAt = now - 1L)),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun firedOccurrenceIsNeverShownEvenWhenItsTimestampIsFuture() {
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm),
            groups = listOf(enabledGroup),
            occurrences = listOf(
                occurrence(
                    id = 1L,
                    alarmId = alarm.id,
                    triggerAt = now + 60_000L,
                    status = OccurrenceStatus.FIRED,
                )
            ),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun selectsEarliestActualFutureOccurrence() {
        val laterAlarm = alarm.copy(id = 21L, hour = 7)
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm, laterAlarm),
            groups = listOf(enabledGroup),
            occurrences = listOf(
                occurrence(id = 1L, alarmId = laterAlarm.id, triggerAt = now + 120_000L),
                occurrence(id = 2L, alarmId = alarm.id, triggerAt = now + 60_000L),
            ),
            now = now,
        )

        assertEquals(alarm.id, result?.alarm?.id)
        assertEquals(now + 60_000L, result?.triggerAtMillis)
    }

    @Test
    fun disabledAlarmIsNotShown() {
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm.copy(enabled = false)),
            groups = listOf(enabledGroup),
            occurrences = listOf(occurrence(id = 1L, alarmId = alarm.id, triggerAt = now + 60_000L)),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun scheduledSnoozeRemainsVisibleAfterOneShotDisables() {
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm.copy(enabled = false)),
            groups = listOf(enabledGroup),
            occurrences = listOf(
                occurrence(
                    id = 1L,
                    alarmId = alarm.id,
                    triggerAt = now + 60_000L,
                    snoozeCount = 1,
                )
            ),
            now = now,
        )

        assertEquals(alarm.id, result?.alarm?.id)
    }

    @Test
    fun disabledGroupIsNotShown() {
        val result = selectNextWidgetAlarm(
            alarms = listOf(alarm),
            groups = listOf(enabledGroup.copy(enabled = false)),
            occurrences = listOf(occurrence(id = 1L, alarmId = alarm.id, triggerAt = now + 60_000L)),
            now = now,
        )

        assertNull(result)
    }

    private fun occurrence(
        id: Long,
        alarmId: Long,
        triggerAt: Long,
        snoozeCount: Int = 0,
        status: OccurrenceStatus = OccurrenceStatus.SCHEDULED,
    ) = ScheduledOccurrence(
        id = id,
        alarmId = alarmId,
        groupId = enabledGroup.id,
        triggerAtMillis = triggerAt,
        requestCode = id.toInt(),
        status = status,
        snoozeCount = snoozeCount,
    )
}
