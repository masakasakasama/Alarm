package com.galaxyalarm.ui

import java.util.Calendar
import java.util.Locale

object TimeFormat {
    private val weekdays = arrayOf("日", "月", "火", "水", "木", "金", "土")

    fun hourMinute12(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val h12 = (hour % 12).let { if (it == 0) 12 else it }
        return String.format(Locale.JAPAN, "%d:%02d %s", h12, minute, ampm)
    }

    fun hourMinuteOnly(hour: Int, minute: Int): String {
        val h12 = (hour % 12).let { if (it == 0) 12 else it }
        return String.format(Locale.JAPAN, "%d:%02d", h12, minute)
    }

    fun nextTrigger(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return "-"
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val hm = hourMinute12(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val dayDiff = dayOfEpoch(c) - dayOfEpoch(today)
        val prefix = when (dayDiff) {
            0L -> "今日"
            1L -> "明日"
            2L -> "明後日"
            else -> {
                val wd = weekdays[c.get(Calendar.DAY_OF_WEEK) - 1]
                String.format(Locale.JAPAN, "%d/%d(%s)", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), wd)
            }
        }
        return "$prefix $hm"
    }

    fun nextTriggerDay(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return "-"
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val dayDiff = dayOfEpoch(c) - dayOfEpoch(today)
        return when (dayDiff) {
            0L -> "今日"
            1L -> "明日"
            2L -> "明後日"
            else -> {
                val wd = weekdays[c.get(Calendar.DAY_OF_WEEK) - 1]
                String.format(Locale.JAPAN, "%d/%d(%s)", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), wd)
            }
        }
    }

    fun remaining(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return ""
        val diff = millis - now
        if (diff <= 0) return "まもなく"
        val mins = diff / 60000
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "あと ${h}時間${m}分" else "あと ${m}分"
    }

    fun clock(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return hourMinute12(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun dateTime(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.JAPAN,
            "%d/%02d/%02d ",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        ) + hourMinute12(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun dayOfEpoch(c: Calendar): Long {
        val z = c.clone() as Calendar
        z.set(Calendar.HOUR_OF_DAY, 0)
        z.set(Calendar.MINUTE, 0)
        z.set(Calendar.SECOND, 0)
        z.set(Calendar.MILLISECOND, 0)
        return z.timeInMillis / 86_400_000L
    }
}
