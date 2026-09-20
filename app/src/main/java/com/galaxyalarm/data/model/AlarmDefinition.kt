package com.galaxyalarm.data.model

import android.net.Uri
import com.galaxyalarm.data.entity.AlarmItem

/** Fields that define an alarm configuration. Database identity and runtime state are excluded. */
data class AlarmDefinitionKey(
    val groupId: Long,
    val label: String,
    val hour: Int,
    val minute: Int,
    val weekdaysMask: Int,
    val enabled: Boolean,
    val soundMode: SoundMode,
    val ringtoneUri: String?,
    val vibrationEnabled: Boolean,
    val vibrationPattern: VibrationPattern,
    val snoozeEnabled: Boolean,
    val snoozeMinutes: Int,
    val maxSnoozeCount: Int,
    val autoStopMinutes: Int,
    val fadeInSeconds: Int,
) {
    companion object {
        fun from(alarm: AlarmItem, groupIdentity: Long = alarm.groupId): AlarmDefinitionKey = AlarmDefinitionKey(
            groupId = groupIdentity,
            label = alarm.label,
            hour = alarm.hour,
            minute = alarm.minute,
            weekdaysMask = alarm.weekdaysMask,
            enabled = alarm.enabled,
            soundMode = alarm.soundMode,
            ringtoneUri = alarm.ringtoneUri?.takeIf(String::isNotBlank)?.let(Uri::parse)?.toString(),
            vibrationEnabled = alarm.vibrationEnabled,
            vibrationPattern = alarm.vibrationPattern,
            snoozeEnabled = alarm.snoozeEnabled,
            snoozeMinutes = alarm.snoozeMinutes,
            maxSnoozeCount = alarm.maxSnoozeCount,
            autoStopMinutes = alarm.autoStopMinutes,
            fadeInSeconds = alarm.fadeInSeconds,
        )
    }
}

fun AlarmItem.validateForSave(): String? = when {
    groupId <= 0L -> "グループを選択してください。"
    hour !in 0..23 -> "時刻の「時」が正しくありません。"
    minute !in 0..59 -> "時刻の「分」が正しくありません。"
    weekdaysMask !in 0..0b1111111 -> "曜日の設定が正しくありません。"
    snoozeMinutes !in 1..60 -> "スヌーズ間隔は1〜60分で設定してください。"
    maxSnoozeCount !in 0..20 -> "スヌーズ回数は0〜20回で設定してください。"
    autoStopMinutes !in 1..60 -> "自動停止時間は1〜60分で設定してください。"
    fadeInSeconds < 0 -> "フェードイン時間が正しくありません。"
    ringtoneUri?.isNotBlank() == true && runCatching {
        Uri.parse(ringtoneUri).scheme.isNullOrBlank()
    }.getOrDefault(true) -> "アラーム音のURIが正しくありません。"
    else -> null
}
