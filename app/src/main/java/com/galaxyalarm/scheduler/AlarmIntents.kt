package com.galaxyalarm.scheduler

/** アラーム関連 Intent の action / extra キー定義。 */
object AlarmIntents {
    const val ACTION_FIRE = "com.galaxyalarm.action.FIRE"
    const val ACTION_STOP = "com.galaxyalarm.action.STOP"
    const val ACTION_STOP_ALL = "com.galaxyalarm.action.STOP_ALL"
    const val ACTION_SNOOZE = "com.galaxyalarm.action.SNOOZE"
    const val ACTION_TIMER_FIRE = "com.galaxyalarm.action.TIMER_FIRE"
    const val ACTION_TEST_FIRE = "com.galaxyalarm.action.TEST_FIRE"

    const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    const val EXTRA_ALARM_ID = "alarm_id"
}
