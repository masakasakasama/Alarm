package com.galaxyalarm

import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import com.galaxyalarm.scheduler.ScheduleReconciliation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReconciliationTest {
    private val group = AlarmGroup(id = 1, name = "work", enabled = true)
    private val alarm = AlarmItem(id = 2, groupId = group.id, hour = 7, minute = 15)
    private val now = 1_000_000L

    @Test fun pendingSnoozeIsReassertedAndNeverReplacedByRegularAlarm() {
        val regular = occurrence(id = 10, triggerAt = now + 60_000, snoozeCount = 0)
        val snooze = occurrence(id = 11, triggerAt = now + 120_000, snoozeCount = 1)

        val plan = ScheduleReconciliation.plan(
            listOf(group), listOf(alarm), listOf(regular, snooze), now, 600_000L
        )

        assertEquals(setOf(10L, 11L), plan.reassert.map { it.id }.toSet())
        assertTrue(plan.cancel.isEmpty())
        assertTrue(plan.create.isEmpty())
    }

    @Test fun recentlyLateOccurrenceIsDeliveredInsteadOfMovedToTomorrow() {
        val late = occurrence(id = 12, triggerAt = now - 30_000, snoozeCount = 0)

        val plan = ScheduleReconciliation.plan(
            listOf(group), listOf(alarm), listOf(late), now, 600_000L
        )

        assertEquals(listOf(12L), plan.reassert.map { it.id })
        assertTrue(plan.create.isEmpty())
    }

    @Test fun disabledAlarmCancelsRegularAndSnoozeOccurrences() {
        val disabled = alarm.copy(enabled = false)
        val regular = occurrence(id = 13, triggerAt = now + 60_000, snoozeCount = 0)
        val snooze = occurrence(id = 14, triggerAt = now + 120_000, snoozeCount = 1)

        val plan = ScheduleReconciliation.plan(
            listOf(group), listOf(disabled), listOf(regular, snooze), now, 600_000L
        )

        assertEquals(setOf(13L, 14L), plan.cancel.map { it.id }.toSet())
        assertTrue(plan.reassert.isEmpty())
        assertTrue(plan.create.isEmpty())
    }

    @Test fun missingOccurrenceCreatesOneForAnEnabledAlarm() {
        val plan = ScheduleReconciliation.plan(
            listOf(group), listOf(alarm), emptyList(), now, 600_000L
        )

        assertEquals(listOf(alarm.id), plan.create.map { it.id })
        assertTrue(plan.cancel.isEmpty())
        assertTrue(plan.reassert.isEmpty())
    }

    @Test fun duplicateRegularOccurrencesKeepTheEarliestAndCancelTheRest() {
        val earliest = occurrence(id = 15, triggerAt = now + 60_000, snoozeCount = 0)
        val duplicate = occurrence(id = 16, triggerAt = now + 120_000, snoozeCount = 0)

        val plan = ScheduleReconciliation.plan(
            listOf(group), listOf(alarm), listOf(duplicate, earliest), now, 600_000L
        )

        assertEquals(listOf(earliest.id), plan.reassert.map { it.id })
        assertEquals(listOf(duplicate.id), plan.cancel.map { it.id })
        assertTrue(plan.create.isEmpty())
    }

    private fun occurrence(id: Long, triggerAt: Long, snoozeCount: Int) = ScheduledOccurrence(
        id = id,
        alarmId = alarm.id,
        groupId = group.id,
        triggerAtMillis = triggerAt,
        requestCode = id.toInt(),
        snoozeCount = snoozeCount,
    )
}
