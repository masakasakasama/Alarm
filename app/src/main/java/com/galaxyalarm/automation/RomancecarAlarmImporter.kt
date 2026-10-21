package com.galaxyalarm.automation

import android.content.Context
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar

/** Shared by the MacroDroid Intent bridge and the built-in direct Gmail sync. */
object RomancecarAlarmImporter {
    private const val GROUP = "ロマンスカー（自動）"
    private const val FUTURE_LIMIT_MS = 12 * 60 * 60_000L

    suspend fun import(app: AlarmApplication, arrivalMs: Long, station: String): Boolean {
        if (station.length > 100 || station.isBlank()) return false
        val repository = app.container.repository
        val group = repository.getGroups().firstOrNull { it.name == GROUP }
        val groupId = group?.id ?: repository.addGroup(GROUP)
        val label = RomancecarAutomation.label(arrivalMs, station)
        val current = repository.getAlarms().firstOrNull {
            it.groupId == groupId && RomancecarAutomation.matchesBooking(it.label, label, station)
        }

        val now = System.currentTimeMillis()
        val alarmAt = RomancecarAutomation.alarmTimestamp(arrivalMs, now)

        // A changed reservation might now arrive on another date, beyond the
        // 12-hour scheduling horizon. Disable the old reservation immediately.
        // The next sync inside the horizon will re-enable it with the new time.
        if (alarmAt == null) {
            if (arrivalMs > now + FUTURE_LIMIT_MS && current?.enabled == true) {
                return repository.setAlarmEnabled(current.id, false)
            }
            return true
        }

        val time = Calendar.getInstance().apply { timeInMillis = alarmAt }
        val hour = time.get(Calendar.HOUR_OF_DAY)
        val minute = time.get(Calendar.MINUTE)

        if (current?.enabled == true && current.hour == hour && current.minute == minute &&
            current.label == label && current.soundMode == SoundMode.VIBRATE_ONLY &&
            current.weekdaysMask == 0) return true

        val alarm = AlarmItem(
            id = current?.id ?: 0L,
            groupId = groupId,
            label = label,
            hour = hour,
            minute = minute,
            weekdaysMask = 0,
            enabled = true,
            soundMode = SoundMode.VIBRATE_ONLY,
            vibrationEnabled = true,
            vibrationPattern = VibrationPattern.LONG,
            snoozeEnabled = false,
            maxSnoozeCount = 0,
            autoStopMinutes = 2,
            createdAt = current?.createdAt ?: now,
        )
        val result = repository.saveAlarmChecked(alarm)
        val ok = when {
            result.scheduled -> true
            result.duplicateOf != null -> repository.setAlarmEnabled(result.duplicateOf, true)
            else -> false
        }
        if (ok) NextAlarmWidgetProvider.refresh(app)
        return ok
    }
}
