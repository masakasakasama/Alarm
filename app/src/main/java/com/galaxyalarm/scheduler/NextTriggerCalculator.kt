package com.galaxyalarm.scheduler

import com.galaxyalarm.data.model.Weekdays
import java.util.Calendar
import java.util.TimeZone

/**
 * 次回鳴動時刻(epoch millis)を計算する。一覧/詳細/グループで矛盾しないよう、
 * アプリ全体でこの関数のみを使う。weekdaysMask=0 のときは一度きり(過去なら翌日扱い)。
 */
object NextTriggerCalculator {

    /** bit0=月 ... bit6=日。Calendar.MONDAY..SUNDAY と対応付ける。 */
    private val DAY_INDEX_TO_CAL = intArrayOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
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
            // 一度きり: 本日の指定時刻、過ぎていれば翌日。
            val c = (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            if (c.timeInMillis <= fromMillis) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }

        // 繰り返し: 今日から最大7日先まで探索。
        for (offset in 0..7) {
            val c = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val dayIndex = calToDayIndex(c.get(Calendar.DAY_OF_WEEK))
            if (Weekdays.has(weekdaysMask, dayIndex) && c.timeInMillis > fromMillis) {
                return c.timeInMillis
            }
        }
        // 理論上到達しないが安全側で翌日同時刻。
        return (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
    }

    private fun calToDayIndex(calDay: Int): Int =
        DAY_INDEX_TO_CAL.indexOf(calDay)
}
