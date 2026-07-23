package com.galaxyalarm

import com.galaxyalarm.scheduler.NextTriggerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NextTriggerCalculatorTest {

    private fun cal(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            clear(); set(y, mo - 1, d, h, mi, 0)
        }.timeInMillis

    @Test fun oneShot_future_today() {
        val from = cal(2026, 6, 7, 5, 0)
        val next = NextTriggerCalculator.nextTrigger(7, 30, 0, from, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(cal(2026, 6, 7, 7, 30), next)
    }

    @Test fun oneShot_past_movesToTomorrow() {
        val from = cal(2026, 6, 7, 8, 0)
        val next = NextTriggerCalculator.nextTrigger(7, 30, 0, from, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(cal(2026, 6, 8, 7, 30), next)
    }

    @Test fun oneShot_sameMinute_firesSoon() {
        val from = cal(2026, 6, 7, 8, 0) + 30_000
        val next = NextTriggerCalculator.nextTrigger(8, 0, 0, from, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(from + 10_000, next)
    }

    @Test fun repeating_picksNextMatchingWeekday() {
        // 2026-06-07 は日曜。月曜のみ(bit0)に設定。
        val from = cal(2026, 6, 7, 9, 0)
        val mondayOnly = 0b0000001
        val next = NextTriggerCalculator.nextTrigger(6, 0, mondayOnly, from, TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(cal(2026, 6, 8, 6, 0), next) // 翌日の月曜
    }

    @Test fun repeating_sameMinute_firesSoonInsteadOfNextWeek() {
        val zone = TimeZone.getTimeZone("Asia/Tokyo")
        val from = cal(2026, 6, 8, 7, 15) + 30_000 // Monday 07:15:30
        val mondayOnly = 0b0000001
        val next = NextTriggerCalculator.nextTrigger(7, 15, mondayOnly, from, zone)
        assertEquals(from + 10_000, next)
    }

    @Test fun result_isAlwaysFuture() {
        val from = System.currentTimeMillis()
        val next = NextTriggerCalculator.nextTrigger(0, 0, 0b1111111, from)
        assertTrue(next > from)
    }
}
