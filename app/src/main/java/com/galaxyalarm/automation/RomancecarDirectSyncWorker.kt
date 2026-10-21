package com.galaxyalarm.automation

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.galaxyalarm.AlarmApplication
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pulls a minimal list of future EMot trips from a user-owned Apps Script web app.
 * Gmail access remains on Google's side; no Gmail credentials are stored on Android.
 * WorkManager periodic work is inexact and runs no more often than every 15 minutes.
 */
class RomancecarDirectSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val url = RomancecarDirectSyncSettings.readUrl(context)
        if (!RomancecarDirectSyncSettings.validUrl(url)) return Result.success()
        val now = System.currentTimeMillis()
        return try {
            val response = withContext(Dispatchers.IO) {
                readBookings(url, RomancecarAutomation.token(context))
            }
            val body = JSONObject(response)
            if (!body.optBoolean("ok", false)) {
                throw IllegalStateException("GAS authentication or response failed")
            }
            val bookings = body.optJSONArray("bookings") ?: throw IllegalStateException("bookings missing")
            if (bookings.length() > 60) throw IllegalStateException("Unexpected booking count")
            val app = context as AlarmApplication
            var successCount = 0
            for (index in 0 until bookings.length()) {
                val booking = bookings.getJSONObject(index)
                val arrival = booking.getLong("arrival_ms")
                val station = booking.getString("station")
                if (station.isBlank() || station.length > 100) continue
                if (arrival <= now || arrival > now + 45L * 24L * 60L * 60_000L) continue
                if (!RomancecarAlarmImporter.import(app, arrival, station)) {
                    throw IllegalStateException("OS alarm scheduling failed")
                }
                successCount++
            }
            RomancecarDirectSyncSettings.recordSuccess(context, successCount)
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Direct Romancecar sync failed", error)
            RomancecarDirectSyncSettings.recordFailure(context, error.javaClass.simpleName)
            Result.retry()
        }
    }

    private fun readBookings(webAppUrl: String, token: String): String {
        val connection = URL(webAppUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = JSONObject().put("token", token).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                readBounded(connection)
            } else if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER
            ) {
                // Google Apps Script ContentService answers with a 302 redirect.
                // Follow only Google's own HTTPS response host, never forward the POST token.
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Google response missing location")
                val destination = URL(location)
                if (destination.protocol != "https" ||
                    destination.host != "script.googleusercontent.com"
                ) throw IllegalStateException("Unexpected Apps Script redirect")
                val forwarded = destination.openConnection() as HttpURLConnection
                try {
                    forwarded.requestMethod = "GET"
                    forwarded.connectTimeout = 12_000
                    forwarded.readTimeout = 18_000
                    forwarded.instanceFollowRedirects = false
                    if (forwarded.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("Google response HTTP ${forwarded.responseCode}")
                    }
                    readBounded(forwarded)
                } finally {
                    forwarded.disconnect()
                }
            } else {
                throw IllegalStateException("Apps Script HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(connection: HttpURLConnection): String =
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(4096)
            val text = StringBuilder()
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                if (text.length + count > 80_000) throw IllegalStateException("Oversized response")
                text.append(chars, 0, count)
            }
            text.toString()
        }

    companion object {
        private const val TAG = "RomancecarDirectSync"
        private const val PERIODIC = "romancecar_direct_sync_periodic"
        private const val ONCE = "romancecar_direct_sync_once"

        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (!RomancecarDirectSyncSettings.validUrl(RomancecarDirectSyncSettings.readUrl(context))) {
                manager.cancelUniqueWork(PERIODIC)
                return
            }
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val periodic = PeriodicWorkRequestBuilder<RomancecarDirectSyncWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(network).build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        }

        fun syncNow(context: Context, expedited: Boolean = false) {
            if (!RomancecarDirectSyncSettings.validUrl(RomancecarDirectSyncSettings.readUrl(context))) return
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val builder = OneTimeWorkRequestBuilder<RomancecarDirectSyncWorker>().setConstraints(network)
            // On Android 12+ expedite notification-triggered sync when quota permits.
            // WorkManager falls back to normal scheduling if expedited quota is exhausted.
            if (expedited && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE, builder.build()
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(ONCE)
        }
    }
}

object RomancecarDirectSyncSettings {
    private const val PREFERENCES = "romancecar_direct_sync"
    private const val URL = "script_url"
    private const val LAST_OK = "last_success"
    private const val LAST_ERROR = "last_error"
    private const val LAST_COUNT = "last_count"
    private const val INSTANT_SYNC_ENABLED = "instant_sync_enabled"
    private const val LAST_NOTIFICATION_SYNC = "last_notification_sync_elapsed"
    private const val NOTIFICATION_DEBOUNCE_MS = 25_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun readUrl(context: Context): String = prefs(context).getString(URL, "").orEmpty()

    fun isInstantSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(INSTANT_SYNC_ENABLED, false)

    fun setInstantSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(INSTANT_SYNC_ENABLED, enabled).apply()
    }

    /**
     * Ignore duplicate/summary Gmail notification updates for 25 seconds.
     * The existing 15-minute WorkManager sync remains an independent fallback.
     * elapsedRealtime avoids wall-clock changes; reset-on-reboot is handled.
     */
    fun claimNotificationSync(context: Context): Boolean = synchronized(this) {
        val preferences = prefs(context)
        val now = SystemClock.elapsedRealtime()
        val previous = preferences.getLong(LAST_NOTIFICATION_SYNC, -1L)
        if (previous >= 0 && now >= previous && now - previous < NOTIFICATION_DEBOUNCE_MS) {
            return@synchronized false
        }
        preferences.edit().putLong(LAST_NOTIFICATION_SYNC, now).apply()
        true
    }

    fun validUrl(value: String): Boolean =
        Regex("^https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]+/exec$").matches(value)

    fun saveUrl(context: Context, value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !validUrl(trimmed)) return false
        prefs(context).edit().putString(URL, trimmed).apply()
        return true
    }

    fun recordSuccess(context: Context, count: Int) {
        prefs(context).edit()
            .putLong(LAST_OK, System.currentTimeMillis())
            .putInt(LAST_COUNT, count)
            .remove(LAST_ERROR).apply()
    }

    fun recordFailure(context: Context, error: String) {
        prefs(context).edit().putString(LAST_ERROR, error.take(80)).apply()
    }

    fun status(context: Context): String {
        val prefs = prefs(context)
        val lastOk = prefs.getLong(LAST_OK, 0L)
        val error = prefs.getString(LAST_ERROR, null)
        if (error != null) return "前回同期エラー: $error"
        if (lastOk == 0L) return "未同期"
        return "同期成功：${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.JAPAN).format(java.util.Date(lastOk))}／予約 ${prefs.getInt(LAST_COUNT, 0)} 件"
    }
}
