package com.galaxyalarm

import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.ui.edit.soundForGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSoundPolicyTest {
    private val groups = listOf(AlarmGroup(id = 1, name = "音無し"), AlarmGroup(id = 2, name = "ロマ 8:31発"))

    @Test fun groupChangeSetsSoundModeForNewAndExistingAlarms() {
        val alarm = AlarmItem(id = 5, groupId = 2, hour = 8, minute = 28, soundMode = SoundMode.SOUND)
        val silent = alarm.copy(groupId = 1, vibrationEnabled = false).soundForGroup(groups)
        assertEquals(SoundMode.VIBRATE_ONLY, silent.soundMode)
        assertTrue(silent.vibrationEnabled)
        assertEquals(SoundMode.SOUND, silent.copy(groupId = 2).soundForGroup(groups).soundMode)
        assertEquals(SoundMode.VIBRATE_ONLY, alarm.copy(id = 0, groupId = 1).soundForGroup(groups).soundMode)
    }
}
