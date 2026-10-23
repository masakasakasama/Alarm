package com.galaxyalarm.automation

import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode

/** Visible alarms are reusable HH:mm slots; booking identity never belongs in their label. */
object RomancecarAlarmSlots {
    fun isLegacyLabel(label: String): Boolean =
        label.startsWith("ロマンスカー乗車3分前 ") || label.startsWith("ロマンスカー到着3分前 ")

    fun isSilentOneTime(alarm: AlarmItem): Boolean =
        alarm.weekdaysMask == 0 && alarm.soundMode != SoundMode.SOUND

    fun select(alarms: List<AlarmItem>, groupId: Long, hour: Int, minute: Int): AlarmItem? =
        alarms.filter {
            it.groupId == groupId && it.hour == hour && it.minute == minute && isSilentOneTime(it)
        }.sortedWith(compareByDescending<AlarmItem> { it.enabled }.thenBy { it.id }).firstOrNull()
}
