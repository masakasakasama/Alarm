package com.galaxyalarm.data.model

/** 音モード。SILENT でも通知と画面表示は出す。 */
enum class SoundMode { SOUND, VIBRATE_ONLY, SILENT }

/** 予約(ScheduledOccurrence)の状態。 */
enum class OccurrenceStatus { SCHEDULED, FIRED, CANCELED, FAILED }

/** イベントログの結果種別。 */
enum class EventResult { FIRED, DISMISSED, SNOOZED, MISSED, FAILED_TO_SCHEDULE }

/** バイブパターンの種類。 */
enum class VibrationPattern {
    SHORT, LONG, DOUBLE, HEARTBEAT;

    /** OS に渡す ms 配列(オフ,オン,...)。 */
    fun toTimings(): LongArray = when (this) {
        SHORT -> longArrayOf(0, 400, 600)
        LONG -> longArrayOf(0, 1000, 800)
        DOUBLE -> longArrayOf(0, 300, 200, 300, 1000)
        HEARTBEAT -> longArrayOf(0, 200, 150, 200, 1200)
    }
}

/** 曜日マスクのユーティリティ。bit0=月 ... bit6=日。0 はワンショット(繰り返しなし)。 */
object Weekdays {
    const val NONE = 0
    val LABELS = listOf("月", "火", "水", "木", "金", "土", "日")
    fun has(mask: Int, dayIndex: Int): Boolean = (mask and (1 shl dayIndex)) != 0
    fun toggle(mask: Int, dayIndex: Int): Int = mask xor (1 shl dayIndex)
    fun isRepeating(mask: Int): Boolean = mask != NONE
    fun label(mask: Int): String = when {
        mask == NONE -> "一度きり"
        mask == 0b1111111 -> "毎日"
        mask == 0b0011111 -> "平日"
        mask == 0b1100000 -> "週末"
        else -> LABELS.filterIndexed { i, _ -> has(mask, i) }.joinToString("")
    }
}
