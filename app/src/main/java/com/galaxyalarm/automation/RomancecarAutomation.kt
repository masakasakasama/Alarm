package com.galaxyalarm.automation

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Private local bridge for a user-configured MacroDroid workflow.
 * The secret is generated on-device and is never embedded in source or backed up to GitHub.
 */
object RomancecarAutomation {
    const val ACTION_SET_ARRIVAL = "com.galaxyalarm.action.SET_ROMANCECAR_ARRIVAL"
    const val EXTRA_TOKEN = "auth_token"
    const val EXTRA_ARRIVAL_MS = "arrival_ms"
    const val EXTRA_STATION = "station"

    private const val PREFS = "romancecar_automation"
    private const val SECRET = "secret"
    private const val THREE_MINUTES_MS = 3 * 60_000L
    private const val MAX_FUTURE_MS = 12 * 60 * 60_000L
    private const val MIN_LEAD_MS = 60_000L

    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(SECRET, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return synchronized(this) {
            prefs.getString(SECRET, null)?.takeIf { it.isNotBlank() } ?: generateToken().also {
                prefs.edit().putString(SECRET, it).commit()
            }
        }
    }

    fun rotateToken(context: Context): String = synchronized(this) {
        generateToken().also {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(SECRET, it).commit()
        }
    }

    fun authorized(context: Context, providedToken: String?): Boolean {
        if (providedToken.isNullOrBlank() || providedToken.length > 128) return false
        return MessageDigest.isEqual(
            token(context).toByteArray(Charsets.UTF_8),
            providedToken.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * A normal one-time alarm stores wall-clock HH:mm, not a calendar date.
     * Reject timestamps outside the next 12 hours or mismatched local scheduling to
     * prevent a booking made days in advance from ringing on the wrong day.
     */
    fun alarmTimestamp(arrivalAtMs: Long, now: Long = System.currentTimeMillis()): Long? {
        if (arrivalAtMs <= THREE_MINUTES_MS) return null
        val alarmAt = arrivalAtMs - THREE_MINUTES_MS
        if (alarmAt < now + MIN_LEAD_MS || alarmAt > now + MAX_FUTURE_MS) return null
        return alarmAt
    }

    /**
     * MacroDroid passes a single station text variable, optionally followed by
     * a stable EMot confirmation code separated by '|'.
     * Example: "本厚木|E123". No ticket IDs or access tokens are sent.
     */
    fun station(raw: String): String =
        raw.substringBefore('|').replace(Regex("[\\r\\n\\t]"), "").trim().take(24)

    fun bookingCode(raw: String): String? =
        raw.substringAfter('|', "").trim().takeIf { it.matches(Regex("E[0-9A-Za-z]{3,10}")) }

    fun label(arrivalAtMs: Long, rawStation: String): String {
        val date = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        }.format(Date(arrivalAtMs))
        val stationName = station(rawStation)
        val code = bookingCode(rawStation)
        return "ロマンスカー到着3分前 $date ${stationName.ifBlank { "目的地" }}" +
            (code?.let { " [$it]" } ?: "")
    }

    fun matchesBooking(previousLabel: String, nextLabel: String, stationRaw: String): Boolean {
        val code = bookingCode(stationRaw)
        if (code != null) {
            return previousLabel.endsWith(" [$code]") &&
                previousLabel.startsWith("ロマンスカー到着3分前 ")
        }
        return previousLabel == nextLabel
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
