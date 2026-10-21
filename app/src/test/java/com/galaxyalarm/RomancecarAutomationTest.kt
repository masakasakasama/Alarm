package com.galaxyalarm

import com.galaxyalarm.automation.RomancecarAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomancecarAutomationTest {
    @Test fun subtractsThreeMinutes() {
        val now = 1_800_000_000_000L
        assertEquals(now + 27 * 60_000L, RomancecarAutomation.alarmTimestamp(now + 30 * 60_000L, now))
    }

    @Test fun rejectsPastAndDistantBookings() {
        val now = 1_800_000_000_000L
        assertNull(RomancecarAutomation.alarmTimestamp(now + 3 * 60_000L, now))
        assertNull(RomancecarAutomation.alarmTimestamp(now + 24 * 60 * 60_000L, now))
    }

    @Test fun identicalBookingProducesSameLabel() {
        assertEquals(
            RomancecarAutomation.label(1_800_000_000_000L, "新宿"),
            RomancecarAutomation.label(1_800_000_000_000L, "新宿"),
        )
    }
}
