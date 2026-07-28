package com.galaxyalarm.ring

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.data.model.OccurrenceStatus
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.service.AlarmService
import kotlinx.coroutines.launch

/** Immediate stop path shared by notification, lock screen, hardware keys, and the app UI. */
object AlarmStopController {
    fun stopAllNow(context: Context, hintedOccurrenceId: Long? = null) {
        val appContext = context.applicationContext
        val occurrenceIds = buildSet {
            ActiveAlarms.stack.value
                .map { it.occurrenceId }
                .filter { it >= 0L }
                .forEach(::add)
            hintedOccurrenceId?.takeIf { it >= 0L }?.let(::add)
        }

        AlarmDismissalStore(appContext).markDismissed(occurrenceIds)
        ActiveAlarms.clear()
        cancelVibration(appContext)
        runCatching {
            appContext.getSystemService(NotificationManager::class.java)
                .cancel(NotificationHelper.FOREGROUND_ID)
        }
        runCatching {
            appContext.stopService(Intent(appContext, AlarmService::class.java))
        }

        val app = appContext as? AlarmApplication ?: return
        app.appScope.launch {
            val container = app.containerOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            occurrenceIds.forEach { occurrenceId ->
                runCatching { container.scheduler.cancelBackup(occurrenceId) }
                val occurrence = container.db.occurrenceDao().getById(occurrenceId)
                if (occurrence != null) {
                    container.db.occurrenceDao()
                        .setStatus(occurrenceId, OccurrenceStatus.CANCELED, now)
                    runCatching {
                        container.repository.log(
                            AlarmEventLog(
                                alarmId = occurrence.alarmId,
                                groupId = occurrence.groupId,
                                scheduledAtMillis = occurrence.triggerAtMillis,
                                firedAtMillis = now,
                                delayMs = now - occurrence.triggerAtMillis,
                                result = EventResult.DISMISSED,
                                message = "即時全停止",
                            )
                        )
                    }
                }
            }
        }
    }

    private fun cancelVibration(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        }
    }
}
