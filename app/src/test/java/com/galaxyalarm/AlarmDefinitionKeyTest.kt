package com.galaxyalarm

import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.AlarmDefinitionKey
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlarmDefinitionKeyTest {
    private val alarm = AlarmItem(
        id = 10,
        groupId = 2,
        label = "wake",
        hour = 7,
        minute = 35,
        weekdaysMask = 0b0011111,
        enabled = false,
        soundMode = SoundMode.SOUND,
        ringtoneUri = null,
        vibrationEnabled = true,
        vibrationPattern = VibrationPattern.SHORT,
        snoozeEnabled = true,
        snoozeMinutes = 5,
        maxSnoozeCount = 3,
        autoStopMinutes = 5,
        fadeInSeconds = 20,
    )

    @Test
    fun onOffStateDoesNotCreateAnotherDefinition() {
        assertEquals(
            AlarmDefinitionKey.from(alarm),
            AlarmDefinitionKey.from(alarm.copy(id = 11, enabled = true)),
        )
    }

    @Test
    fun anyDefinitionChangeRemainsDistinct() {
        val original = AlarmDefinitionKey.from(alarm)
        val variants = listOf(
            alarm.copy(groupId = 3),
            alarm.copy(label = "work"),
            alarm.copy(hour = 8),
            alarm.copy(minute = 36),
            alarm.copy(weekdaysMask = 0),
            alarm.copy(soundMode = SoundMode.VIBRATE_ONLY),
            alarm.copy(ringtoneUri = "content://tone/1"),
            alarm.copy(vibrationEnabled = false),
            alarm.copy(vibrationPattern = VibrationPattern.LONG),
            alarm.copy(snoozeEnabled = false),
            alarm.copy(snoozeMinutes = 10),
            alarm.copy(maxSnoozeCount = 4),
            alarm.copy(autoStopMinutes = 10),
            alarm.copy(fadeInSeconds = 30),
        )

        variants.forEach { assertNotEquals(original, AlarmDefinitionKey.from(it)) }
    }
}
