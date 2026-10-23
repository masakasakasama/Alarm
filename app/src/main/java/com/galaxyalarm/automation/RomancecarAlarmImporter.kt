package com.galaxyalarm.automation

import android.content.Context
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** A booking can change without creating a second visible alarm at the same HH:mm. */
object RomancecarAlarmImporter {
    const val GROUP = "音無し"
    const val LEGACY_GROUP = "ロマンスカー（自動）"
    private const val PREFS = "romancecar_alarm_slots"
    private const val MANAGED = "managed_ids"
    private val importMutex = Mutex()

    data class Event(val atMs: Long, val station: String, val departure: Boolean = false)

    suspend fun sync(app: AlarmApplication, events: List<Event>): Boolean {
        val ok = sync(app, app.container.repository, events)
        if (ok) NextAlarmWidgetProvider.refresh(app)
        return ok
    }

    internal suspend fun sync(context: Context, repository: AlarmRepository, events: List<Event>): Boolean =
        importMutex.withLock { reconcile(context, repository, events, "direct") }

    /** MacroDroid supplies one event at a time; retain its other upcoming arrivals. */
    suspend fun import(
        app: AlarmApplication, arrivalMs: Long, station: String, departure: Boolean = false,
    ): Boolean = importMutex.withLock {
        if (station.length > 100 || station.isBlank()) return@withLock false
        val previous = readEvents(app, "bridge").filterNot {
            val code = RomancecarAutomation.bookingCode(station)
            it.departure == departure && if (code != null) {
                RomancecarAutomation.bookingCode(it.station) == code
            } else it.station == station && it.atMs == arrivalMs
        }
        val ok = reconcile(app, app.container.repository, previous + Event(arrivalMs, station, departure), "bridge")
        if (ok) NextAlarmWidgetProvider.refresh(app)
        ok
    }

    private fun readEvents(app: Context, source: String): List<Event> {
        val raw = app.getSharedPreferences(PREFS, 0).getString(source, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val entry = array.getJSONArray(index)
                Event(entry.getLong(0), entry.getString(1), entry.getBoolean(2))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun migrate(app: AlarmApplication) = importMutex.withLock { migrateLocked(app, app.container.repository) }

    private suspend fun migrateLocked(app: Context, repository: AlarmRepository) {
        val migrated = repository.consolidateRomancecarSlots()
        if (migrated.isEmpty()) return
        val prefs = app.getSharedPreferences(PREFS, 0)
        prefs.edit().putStringSet(MANAGED, prefs.getStringSet(MANAGED, emptySet()).orEmpty() +
            migrated.map { it.toString() }).commit()
    }

    private suspend fun reconcile(
        app: Context, repository: AlarmRepository, events: List<Event>, source: String,
    ): Boolean {
        if (events.size > 120 || events.any { it.station.isBlank() || it.station.length > 100 }) return false
        migrateLocked(app, repository)
        val prefs = app.getSharedPreferences(PREFS, 0)
        val now = System.currentTimeMillis()
        val other = readEvents(app, if (source == "direct") "bridge" else "direct")
        val upcoming = (events + other).filter { it.atMs > now && it.atMs <= now + 45L * 24 * 60 * 60_000 }
        val groups = repository.getGroups()
        val groupId = groups.firstOrNull { it.name == GROUP }?.id ?: repository.addGroup(GROUP)
        val managed = prefs.getStringSet(MANAGED, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toMutableSet()
        val activeIds = mutableSetOf<Long>()
        val slots = upcoming.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.atMs - 180_000L }.let { time ->
                time.get(Calendar.HOUR_OF_DAY) to time.get(Calendar.MINUTE)
            }
        }
        for ((slot, trips) in slots) {
            val current = RomancecarAlarmSlots.select(repository.getAlarms(), groupId, slot.first, slot.second)
            // Keep an already scheduled alarm during its final minute and while it is ringing.
            if (trips.none { RomancecarAutomation.alarmTimestamp(it.atMs, now) != null }) {
                if (trips.any { it.atMs - 180_000L <= now + 60_000L }) current?.let { activeIds += it.id }
                continue
            }
            if (current?.enabled == true && current.vibrationEnabled) {
                activeIds += current.id
                continue
            }
            val alarm = current?.copy(enabled = true, vibrationEnabled = true, soundMode = SoundMode.VIBRATE_ONLY)
                ?: AlarmItem(
                    groupId = groupId, label = "", hour = slot.first, minute = slot.second,
                    weekdaysMask = 0, enabled = true, soundMode = SoundMode.VIBRATE_ONLY,
                    vibrationEnabled = true, vibrationPattern = VibrationPattern.LONG,
                    snoozeEnabled = false, maxSnoozeCount = 0, autoStopMinutes = 2,
                )
            val result = repository.saveAlarmChecked(alarm)
            val id = if (result.scheduled) result.alarmId else result.duplicateOf?.takeIf {
                repository.setAlarmEnabled(it, true)
            } ?: return false
            activeIds += id
            // An alarm that was already manually enabled is never claimed or disabled by this sync.
            if (current?.enabled != true) managed += id
            if (!prefs.edit().putStringSet(MANAGED, managed.mapTo(mutableSetOf()) { it.toString() }).commit()) {
                return false
            }
        }
        for (id in managed - activeIds) {
            val alarm = repository.getAlarm(id) ?: continue
            if (alarm.groupId == groupId && RomancecarAlarmSlots.isSilentOneTime(alarm) && alarm.enabled) {
                if (!repository.setAlarmEnabled(id, false)) return false
            }
        }
        val keptIds = repository.getAlarms().mapTo(mutableSetOf()) { it.id }
        val serialized = JSONArray()
        events.filter { it.atMs > now }.forEach {
            serialized.put(JSONArray().put(it.atMs).put(it.station).put(it.departure))
        }
        prefs.edit().putString(source, serialized.toString())
            .putStringSet(MANAGED, managed.intersect(keptIds).mapTo(mutableSetOf()) { it.toString() }).commit()
        return true
    }
}
