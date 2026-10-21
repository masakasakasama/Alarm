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
                    val scheduled = RomancecarAlarmImporter.import(app, arrivalMs, station)
                    if (scheduled) {
                        Log.i(TAG, "Arrival alarm accepted by shared importer")
                    } else {
                        Log.e(TAG, "Arrival alarm was not scheduled")
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
