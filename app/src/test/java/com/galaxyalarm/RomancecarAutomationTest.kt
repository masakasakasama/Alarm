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

    @Test fun ticketChangeReusesOriginalAlarmIdentity() {
        val oldLabel = "ロマンスカー到着3分前 2026/10/15 本厚木 [E123]"
        val newLabel = RomancecarAutomation.label(1_800_000_000_000L, "新宿|E123")
        assertEquals("新宿", RomancecarAutomation.station("新宿|E123"))
        assertEquals("E123", RomancecarAutomation.bookingCode("新宿|E123"))
        org.junit.Assert.assertTrue(RomancecarAutomation.matchesBooking(oldLabel, newLabel, "新宿|E123"))
        org.junit.Assert.assertFalse(RomancecarAutomation.matchesBooking(oldLabel, newLabel, "新宿|E124"))
    }

    @Test fun identicalBookingProducesSameLabel() {
        assertEquals(
            RomancecarAutomation.label(1_800_000_000_000L, "新宿"),
            RomancecarAutomation.label(1_800_000_000_000L, "新宿"),
        )
    }
    @Test fun boardingAndArrivalKeepSeparateIdentitiesForSameBooking() {
        val at = 1_800_000_000_000L
        val boarding = RomancecarAutomation.label(at, "新宿|E123", departure = true)
        val arrival = RomancecarAutomation.label(at, "本厚木|E123")
        org.junit.Assert.assertTrue(boarding.startsWith("ロマンスカー乗車3分前 "))
        org.junit.Assert.assertTrue(RomancecarAutomation.matchesBooking(boarding,
            RomancecarAutomation.label(at + 60_000L, "町田|E123", true), "町田|E123", true))
        org.junit.Assert.assertFalse(RomancecarAutomation.matchesBooking(arrival, boarding, "新宿|E123", true))
        org.junit.Assert.assertFalse(RomancecarAutomation.matchesBooking(boarding, arrival, "本厚木|E123"))
    }

    @Test fun boardingBeforeMidnightUsesPreviousDayForAlarm() {
        val at = java.time.Instant.parse("2026-10-23T15:01:00Z").toEpochMilli()
        assertEquals(at - 180_000L, RomancecarAutomation.alarmTimestamp(at, at - 3_600_000L))
        org.junit.Assert.assertTrue(RomancecarAutomation.label(at, "新宿|E123", true).contains("2026/10/24"))
    }
}
