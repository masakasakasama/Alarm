package com.galaxyalarm.ui

import java.util.Calendar
import java.util.Locale

/** 次回鳴動時刻を「今日/明日/M/d (曜) HH:mm」で表す。一覧・詳細・グループで共通使用。 */
object TimeFormat {
    private val WD = arrayOf("日", "月", "火", "水", "木", "金", "土")

    fun nextTrigger(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return "—"
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val hm = String.format(Locale.JAPAN, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val dayDiff = dayOfEpoch(c) - dayOfEpoch(today)
        val prefix = when (dayDiff) {
            0L -> "今日"
            1L -> "明日"
            2L -> "明後日"
            else -> {
                val wd = WD[c.get(Calendar.DAY_OF_WEEK) - 1]
                String.format(Locale.JAPAN, "%d/%d(%s)", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), wd)
            }
        }
        return "$prefix $hm"
    }

    /** 残り時間「あと X時間Y分」。 */
    fun remaining(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return ""
        val diff = millis - now
        if (diff <= 0) return "まもなく"
        val mins = diff / 60000
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 -> "あと ${h}時間${m}分"
            else -> "あと ${m}分"
        }
    }

    fun clock(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(Locale.JAPAN, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun dateTime(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.JAPAN, "%d/%02d/%02d %02d:%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    private fun dayOfEpoch(c: Calendar): Long {
        val z = c.clone() as Calendar
        z.set(Calendar.HOUR_OF_DAY, 0); z.set(Calendar.MINUTE, 0)
        z.set(Calendar.SECOND, 0); z.set(Calendar.MILLISECOND, 0)
        return z.timeInMillis / 86_400_000L
    }
}
