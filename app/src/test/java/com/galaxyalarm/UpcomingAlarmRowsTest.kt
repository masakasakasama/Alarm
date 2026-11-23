package com.galaxyalarm

import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.ui.AlarmRow
import com.galaxyalarm.ui.sortedUpcomingAlarms
import org.junit.Assert.assertEquals
import org.junit.Test

class UpcomingAlarmRowsTest {
    @Test fun verifiedReservationsAreOrderedIncludingSnoozesAndSimultaneousAlarms() {
        fun row(id: Long, at: Long?, enabled: Boolean = true) = AlarmRow(
            AlarmItem(id = id, groupId = 1, hour = 8, minute = 0, enabled = enabled),
            "グループ", true, at,
        )
        val rows = listOf(row(8, 300), row(2, 100), row(4, null), row(3, 100, enabled = false))
        assertEquals(listOf(2L, 3L, 8L), sortedUpcomingAlarms(rows).map { it.alarm.id })
    }

    @Test fun aNewlyReservedTomorrowMorningAlarmIsIncludedAfterThreeEveningAlarms() {
        val midnight = 86_400_000L
        val evening = listOf(18, 19, 20).map { hour ->
            AlarmRow(
                AlarmItem(id = hour.toLong(), groupId = 1, hour = hour, minute = 0),
                "グループなし", true, hour * 3_600_000L,
            )
        }
        val morning = AlarmRow(
            AlarmItem(id = 7, groupId = 1, hour = 7, minute = 0),
            "グループなし", true, null,
        )
        assertEquals(3, sortedUpcomingAlarms(listOf(morning) + evening).size)
        val saved = morning.copy(nextTriggerAt = midnight + 7 * 3_600_000L)
        assertEquals(listOf(18L, 19L, 20L, 7L), sortedUpcomingAlarms(listOf(saved) + evening).map { it.alarm.id })
    }
}
