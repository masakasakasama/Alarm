package com.galaxyalarm

import com.galaxyalarm.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmRequestCodeTest {

    @Test
    fun backupRequestCode_neverMatchesPrimary() {
        listOf(0, 1, 42, Int.MAX_VALUE, Int.MIN_VALUE).forEach { primary ->
            assertNotEquals(primary, AlarmScheduler.backupRequestCode(primary))
        }
    }

    @Test
    fun backupRequestCode_isUniqueAndReversible() {
        val primaryCodes = (1..10_000).toList()
        val backupCodes = primaryCodes.map(AlarmScheduler::backupRequestCode)

        assertEquals(backupCodes.size, backupCodes.toSet().size)
        assertEquals(
            primaryCodes,
            backupCodes.map(AlarmScheduler::backupRequestCode)
        )
    }
}
