package com.galaxyalarm.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRingRecoveryTest {

    @Test
    fun notificationTapRecoversMissingInMemoryAlarm() {
        val recovered = NotificationRingRecovery.candidate(
            isNotificationTap = true,
            occurrenceId = 123L,
            alarmId = 45L,
            label = "Wake up",
            timeText = "7:00 AM",
            dismissed = false,
            alreadyActive = false,
        )

        assertEquals(
            ActiveAlarm(123L, 45L, "Wake up", "7:00 AM"),
            recovered,
        )
    }

    @Test
    fun dismissedAlarmIsNeverRecovered() {
        assertNull(
            NotificationRingRecovery.candidate(
                isNotificationTap = true,
                occurrenceId = 123L,
                alarmId = 45L,
                label = "Wake up",
                timeText = "7:00 AM",
                dismissed = true,
                alreadyActive = false,
            )
        )
    }

    @Test
    fun existingActiveAlarmIsNotDuplicated() {
        assertNull(
            NotificationRingRecovery.candidate(
                isNotificationTap = true,
                occurrenceId = 123L,
                alarmId = 45L,
                label = "Wake up",
                timeText = "7:00 AM",
                dismissed = false,
                alreadyActive = true,
            )
        )
    }

    @Test
    fun nonNotificationLaunchDoesNotInventState() {
        assertNull(
            NotificationRingRecovery.candidate(
                isNotificationTap = false,
                occurrenceId = 123L,
                alarmId = 45L,
                label = "Wake up",
                timeText = "7:00 AM",
                dismissed = false,
                alreadyActive = false,
            )
        )
    }

    @Test
    fun transientNegativeOccurrenceCanStillRecover() {
        val recovered = NotificationRingRecovery.candidate(
            isNotificationTap = true,
            occurrenceId = -1001L,
            alarmId = -1L,
            label = "タイマー",
            timeText = "タイマー終了",
            dismissed = false,
            alreadyActive = false,
        )

        assertEquals(-1001L, recovered?.occurrenceId)
    }
}
