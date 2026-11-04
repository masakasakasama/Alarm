package com.galaxyalarm.automation

import android.content.Context
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** A booking can change without creating a second visible alarm at the same HH:mm. */
object RomancecarAlarmImporter {
    const val GROUP = "ロマンスカー 8:31発"
    const val SHORT_GROUP = "ロマ 8:31発"
    const val PREVIOUS_GROUP = "音無し"
    const val LEGACY_GROUP = "ロマンスカー（自動）"
    private const val PREFS = "romancecar_alarm_slots"
    private const val MANAGED = "managed_ids"
    private val importMutex = Mutex()

    data class Event(
        val atMs: Long,
        val station: String,
        val departure: Boolean = false,
        val trainDepartureMs: Long? = if (departure) atMs else null,
    )

    private val groupPattern = Regex("^(?:ロマ|ロマンスカー)\\s*(\\d{1,2})[:：](\\d{2})発$")

    private fun departureTime(timestamp: Long): Pair<Int, Int> =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply { timeInMillis = timestamp }
            .let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }

    private fun groupDeparture(name: String): Pair<Int, Int>? {
        val match = groupPattern.matchEntire(name) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return (hour to minute).takeIf { hour in 0..23 && minute in 0..59 }
    }

    private fun isMorningGroup(departure: Pair<Int, Int>?): Boolean =
        departure == (8 to 11) || departure == (8 to 31)

    suspend fun sync(app: AlarmApplication, events: List<Event>): Boolean {
        val ok = sync(app, app.container.repository, events)
        if (ok) NextAlarmWidgetProvider.refresh(app)
        return ok
    }

    internal suspend fun sync(context: Context, repository: AlarmRepository, events: List<Event>,
        now: Long = System.currentTimeMillis()): Boolean =
        importMutex.withLock { reconcile(context, repository, events, "direct", now) }

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
                val at = entry.getLong(0)
                val isDeparture = entry.getBoolean(2)
                Event(at, entry.getString(1), isDeparture,
                    entry.optLong(3, 0L).takeIf { it > 0L } ?: at.takeIf { isDeparture })
            }
        }.getOrDefault(emptyList())
    }

    suspend fun migrate(app: AlarmApplication) = importMutex.withLock { migrateLocked(app.container.repository) }

    private suspend fun migrateLocked(repository: AlarmRepository) {
        repository.mergeRomancecar831Groups()
    }

    private suspend fun reconcile(
        app: Context, repository: AlarmRepository, events: List<Event>, source: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (events.size > 120 || events.any { it.station.isBlank() || it.station.length > 100 }) return false
        migrateLocked(repository)
        val prefs = app.getSharedPreferences(PREFS, 0)
        val other = readEvents(app, if (source == "direct") "bridge" else "direct")
        val upcoming = (events + other).filter { it.atMs > now && it.atMs <= now + 45L * 24 * 60 * 60_000 }
        val groups = repository.getGroups().toMutableList()
        val automationGroupIds = groups.filter {
            groupDeparture(it.name) != null || it.name == PREVIOUS_GROUP || it.name == LEGACY_GROUP
        }.mapTo(mutableSetOf()) { it.id }
        val managed = prefs.getStringSet(MANAGED, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toMutableSet()
        val activeIds = mutableSetOf<Long>()
        val activeSlotGroups = mutableMapOf<Pair<Int, Int>, MutableSet<Long>>()
        // Older cached arrival-only events have no departure. Only pair them when
        // their confirmation code identifies exactly one departure; never guess.
        fun trainDeparture(event: Event): Long? {
            event.trainDepartureMs?.let { return it }
            val code = RomancecarAutomation.bookingCode(event.station) ?: return null
            return upcoming.filter { it.departure && RomancecarAutomation.bookingCode(it.station) == code }
                .map { it.atMs }.distinct().singleOrNull()
        }
        val slots = upcoming.groupBy { event ->
            val departure = trainDeparture(event)?.let(::departureTime)
            val alarmTime = Calendar.getInstance().apply { timeInMillis = event.atMs - 180_000L }
                .let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }
            departure to alarmTime
        }
        val routesPerSlot = slots.keys.groupingBy { it.second }.eachCount()
        for ((routeAndSlot, trips) in slots) {
            val (departure, slot) = routeAndSlot
            val existing = if (departure == null) groups.firstOrNull { it.name == LEGACY_GROUP }
                else if (!isMorningGroup(departure)) groups.firstOrNull { it.name == PREVIOUS_GROUP }
                else groups.filter { groupDeparture(it.name) == departure }
                    .sortedBy { if (it.name.startsWith("ロマ ")) 0 else 1 }.firstOrNull()
            val groupId = existing?.id ?: repository.addGroup(
                if (departure == null) LEGACY_GROUP else if (!isMorningGroup(departure)) PREVIOUS_GROUP
                else "ロマ ${departure.first}:${departure.second.toString().padStart(2, '0')}発"
            ).also { id -> repository.getGroup(id)?.let { groups.add(it) }; automationGroupIds.add(id) }
            val alarms = repository.getAlarms()
            val current = RomancecarAlarmSlots.select(alarms, groupId, slot.first, slot.second)
                ?: alarms.filter {
                    routesPerSlot[slot] == 1 && it.id !in activeIds &&
                        it.id in managed && it.groupId in automationGroupIds &&
                        it.hour == slot.first && it.minute == slot.second &&
                        RomancecarAlarmSlots.isSilentOneTime(it)
                }.sortedWith(compareByDescending<AlarmItem> { it.enabled }.thenBy { it.id }).firstOrNull()
            // Keep an already scheduled alarm during its final minute and while it is ringing.
            if (trips.none { RomancecarAutomation.alarmTimestamp(it.atMs, now) != null }) {
                if (trips.any { it.atMs - 180_000L <= now + 60_000L }) {
                    alarms.filter {
                        it.enabled && it.hour == slot.first && it.minute == slot.second &&
                            (it.groupId == groupId || it.id in managed && it.groupId in automationGroupIds) &&
                            RomancecarAlarmSlots.isSilentOneTime(it)
                    }.forEach { activeIds += it.id }
                }
                continue
            }
            activeSlotGroups.getOrPut(slot) { mutableSetOf() }.add(groupId)
            if (current != null && current.id in managed && current.groupId != groupId &&
                current.enabled && current.vibrationEnabled) {
                if (!repository.moveAlarmToGroup(current.id, groupId)) return false
                activeIds += current.id
                continue
            }
            if (current?.groupId == groupId && current.enabled && current.vibrationEnabled) {
                if (!repository.ensureGroupEnabledForActiveAlarm(current.id)) return false
                activeIds += current.id
                continue
            }
            val alarm = current?.copy(
                groupId = groupId,
                enabled = true,
                vibrationEnabled = true,
                soundMode = SoundMode.VIBRATE_ONLY,
            )
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
        // An arrival can still be upcoming after its departure alert has fired.
        // Keep the known departure route so its old slot can also leave the wrong group.
        for (departureMs in upcoming.mapNotNull(::trainDeparture).distinct().filter { it <= now }) {
            val trainTime = departureTime(departureMs)
            val group = if (isMorningGroup(trainTime)) groups.filter { groupDeparture(it.name) == trainTime }
                .sortedBy { if (it.name.startsWith("ロマ ")) 0 else 1 }.firstOrNull()
                else groups.firstOrNull { it.name == PREVIOUS_GROUP }
            if (group == null) continue
            val clock = Calendar.getInstance().apply { timeInMillis = departureMs - 180_000L }
                .let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }
            activeSlotGroups.getOrPut(clock) { mutableSetOf() }.add(group.id)
        }
        for (id in managed - activeIds) {
            val alarm = repository.getAlarm(id) ?: continue
            if (alarm.groupId !in automationGroupIds || !RomancecarAlarmSlots.isSilentOneTime(alarm)) continue
            val destination = activeSlotGroups[alarm.hour to alarm.minute]?.singleOrNull()
            val snoozing = repository.getAllScheduled().any { it.alarmId == id && it.snoozeCount > 0 }
            // Never cancel a pending snooze while reconciling a duplicate slot.
            if (snoozing) {
                if (destination != null && destination != alarm.groupId &&
                    !repository.moveAlarmToGroup(id, destination)) return false
                continue
            }
            if (alarm.enabled && !repository.setAlarmEnabled(id, false)) return false
            if (destination != null && destination != alarm.groupId &&
                !repository.moveAlarmToGroup(id, destination)) return false
        }
        val keptIds = repository.getAlarms().mapTo(mutableSetOf()) { it.id }
        val serialized = JSONArray()
        events.filter { it.atMs > now }.forEach {
            serialized.put(JSONArray().put(it.atMs).put(it.station).put(it.departure)
                .put(it.trainDepartureMs ?: JSONObject.NULL))
        }
        prefs.edit().putString(source, serialized.toString())
            .putStringSet(MANAGED, managed.intersect(keptIds).mapTo(mutableSetOf()) { it.toString() }).commit()
        return true
    }
}
