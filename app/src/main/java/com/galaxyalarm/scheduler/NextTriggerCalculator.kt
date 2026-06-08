package com.galaxyalarm.scheduler

import com.galaxyalarm.data.model.Weekdays
import java.util.Calendar
import java.util.TimeZone

object NextTriggerCalculator {

    private val DAY_INDEX_TO_CAL = intArrayOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    fun nextTrigger(
        hour: Int,
        minute: Int,
        weekdaysMask: Int,
        fromMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val base = Calendar.getInstance(timeZone).apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!Weekdays.isRepeating(weekdaysMask)) {
            val candidate = (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            if (candidate.timeInMillis <= fromMillis && isSameMinute(candidate, fromMillis, timeZone)) {
                return fromMillis + CURRENT_MINUTE_GRACE_MS
            }
            if (candidate.timeInMillis <= fromMillis) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val dayIndex = calToDayIndex(candidate.get(Calendar.DAY_OF_WEEK))
            if (Weekdays.has(weekdaysMask, dayIndex) && candidate.timeInMillis > fromMillis) {
                return candidate.timeInMillis
            }
        }

        return (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
    }

    private fun calToDayIndex(calDay: Int): Int =
        DAY_INDEX_TO_CAL.indexOf(calDay)

    private fun isSameMinute(target: Calendar, millis: Long, timeZone: TimeZone): Boolean {
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return target.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
            target.get(Calendar.HOUR_OF_DAY) == now.get(Calendar.HOUR_OF_DAY) &&
            target.get(Calendar.MINUTE) == now.get(Calendar.MINUTE)
    }

    private const val CURRENT_MINUTE_GRACE_MS = 10_000L
}
