package com.galaxyalarm.ring

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * A synchronous, device-protected stop barrier. Alarm callbacks check this before
 * starting output so a delayed backup or an in-flight coroutine cannot ring again.
 */
class AlarmDismissalStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref") // The stop barrier must be durable before this call returns.
    fun markDismissed(occurrenceIds: Collection<Long>, now: Long = System.currentTimeMillis()) {
        if (occurrenceIds.isEmpty()) return
        dismissedInProcess.addAll(occurrenceIds)
        val editor = prefs.edit()
        occurrenceIds.distinct().forEach { editor.putLong(key(it), now) }
        if (!editor.commit()) Log.e(TAG, "failed to persist alarm dismissal barrier")
        prune(now)
    }

    fun isDismissed(occurrenceId: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (occurrenceId in dismissedInProcess) return true
        val dismissedAt = prefs.getLong(key(occurrenceId), 0L)
        if (dismissedAt == 0L) return false
        if (now - dismissedAt <= RETENTION_MS) return true
        prefs.edit().remove(key(occurrenceId)).apply()
        return false
    }

    private fun prune(now: Long) {
        val editor = prefs.edit()
        prefs.all.forEach { (name, value) ->
            val timestamp = value as? Long ?: return@forEach
            if (name.startsWith(KEY_PREFIX) && now - timestamp > RETENTION_MS) {
                editor.remove(name)
            }
        }
        editor.apply()
    }

    private fun key(occurrenceId: Long) = "$KEY_PREFIX$occurrenceId"

    private companion object {
        const val TAG = "AlarmDismissalStore"
        const val PREFS_NAME = "alarm_dismissals"
        const val KEY_PREFIX = "occurrence_"
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        val dismissedInProcess = ConcurrentHashMap.newKeySet<Long>()
    }
}
