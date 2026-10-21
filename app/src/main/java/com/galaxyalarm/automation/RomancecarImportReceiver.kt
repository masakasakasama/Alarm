package com.galaxyalarm.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Accepts a token-authenticated explicit Broadcast from MacroDroid.
 * Never accepts untrusted email bodies, direct HTTP requests or arbitrary alarm modes.
 */
class RomancecarImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RomancecarAutomation.ACTION_SET_ARRIVAL) return
        if (!RomancecarAutomation.authorized(context, intent.getStringExtra(RomancecarAutomation.EXTRA_TOKEN))) {
            Log.w(TAG, "Rejected unauthenticated automation request")
            return
        }

        val arrivalMs = intent.getStringExtra(RomancecarAutomation.EXTRA_ARRIVAL_MS)
            ?.toLongOrNull() ?: return
        val alarmAt = RomancecarAutomation.alarmTimestamp(arrivalMs) ?: run {
            Log.w(TAG, "Rejected stale or distant arrival")
            return
        }
        val station = intent.getStringExtra(RomancecarAutomation.EXTRA_STATION).orEmpty()
        val label = RomancecarAutomation.label(arrivalMs, station)
        val app = context.applicationContext as? AlarmApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                withTimeout(9_000L) {
                    val repo = app.container.repository
                    val group = repo.getGroups().firstOrNull { it.name == GROUP_NAME }
                    val groupId = group?.id ?: repo.addGroup(GROUP_NAME)
                    val time = Calendar.getInstance().apply { timeInMillis = alarmAt }
                    val current = repo.getAlarms().firstOrNull {
                        it.groupId == groupId && RomancecarAutomation.matchesBooking(it.label, label, station)
                    }
                    val requested = AlarmItem(
                        id = current?.id ?: 0,
                        groupId = groupId,
                        label = label,
                        hour = time.get(Calendar.HOUR_OF_DAY),
                        minute = time.get(Calendar.MINUTE),
                        weekdaysMask = 0,
                        enabled = true,
                        soundMode = SoundMode.VIBRATE_ONLY,
                        vibrationEnabled = true,
                        vibrationPattern = VibrationPattern.LONG,
                        snoozeEnabled = false,
                        maxSnoozeCount = 0,
                        autoStopMinutes = 2,
                        createdAt = current?.createdAt ?: System.currentTimeMillis(),
                    )
                    val result = repo.saveAlarmChecked(requested)
                    val scheduled = when {
                        result.scheduled -> true
                        result.duplicateOf != null -> repo.setAlarmEnabled(result.duplicateOf, true)
                        else -> false
                    }
                    if (scheduled) {
                        NextAlarmWidgetProvider.refresh(context)
                        Log.i(TAG, "Arrival alarm scheduled: $label")
                    } else {
                        Log.e(TAG, "Arrival alarm was not scheduled: ${result.error ?: "exact alarm failure"}")
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to import romancecar arrival", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "RomancecarImport"
        const val GROUP_NAME = "ロマンスカー（自動）"
    }
}
