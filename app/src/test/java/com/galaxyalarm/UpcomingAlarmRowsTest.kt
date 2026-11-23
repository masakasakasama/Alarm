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
}
