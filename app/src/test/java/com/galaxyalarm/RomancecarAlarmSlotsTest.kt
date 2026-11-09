package com.galaxyalarm

import androidx.room.Room
import com.galaxyalarm.automation.RomancecarAlarmImporter
import com.galaxyalarm.automation.RomancecarAlarmImporter.Event
import com.galaxyalarm.automation.RomancecarAlarmSlots
import com.galaxyalarm.automation.romancecarEventsFromBooking
import com.galaxyalarm.automation.romancecarEventsFromFeed
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.reliability.PermissionChecker
import com.galaxyalarm.scheduler.AlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RomancecarAlarmSlotsTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AlarmRepository

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scheduler = AlarmScheduler(context, db.groupDao(), db.alarmDao(), db.occurrenceDao(),
            db.eventLogDao(), PermissionChecker(context))
        repo = AlarmRepository(db, db.groupDao(), db.alarmDao(), db.occurrenceDao(), db.eventLogDao(), scheduler)
    }
    @After fun tearDown() { db.close() }

    private fun legacy(group: Long, date: String, departure: Boolean = true) = AlarmItem(
        groupId = group, label = "ロマンスカー${if (departure) "乗車" else "到着"}3分前 $date 新宿 [E762]",
        hour = 13, minute = 17, enabled = false, soundMode = SoundMode.VIBRATE_ONLY,
    )

    private fun groupName(at: Long): String {
        val time = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply { timeInMillis = at }
        val hour = time.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = time.get(java.util.Calendar.MINUTE)
        return if (hour == 8 && (minute == 11 || minute == 31)) "ロマ 8:${minute}発" else RomancecarAlarmImporter.PREVIOUS_GROUP
    }

    @Test fun hotelDeadlineUsesOneSilentSlotAndFeedRemovalPreservesTrain() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val originalZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val now = System.currentTimeMillis()
            val deadline = (now / 60_000L) * 60_000L + 150 * 60_000L
            val departure = deadline + 60 * 60_000L
            val arrival = departure + 60 * 60_000L
            val hotel = org.json.JSONObject().put("departure_ms", deadline).put("arrival_ms", deadline)
                .put("departure_station", "HOTEL EXAMPLE").put("station", "無料キャンセル期限")
            val train = org.json.JSONObject().put("departure_ms", departure).put("arrival_ms", arrival)
                .put("departure_station", "出発駅|E123").put("station", "到着駅|E123")
            val hotelEvents = romancecarEventsFromBooking(hotel, now)
            val trainEvents = romancecarEventsFromBooking(train, now)
            assertEquals(1, hotelEvents.size)
            assertTrue(hotelEvents.single().hotelDeadline)
            val events = hotelEvents + hotelEvents + trainEvents
            assertTrue(RomancecarAlarmImporter.sync(context, repo, events, now))
            assertTrue(RomancecarAlarmImporter.sync(context, repo, events, now))
            val silentGroup = repo.getVisibleGroups().single { it.name == "音無し" }
            val expectedTime = java.util.Calendar.getInstance().apply { timeInMillis = deadline - 180_000L }
            val hotelAlarm = db.alarmDao().getByGroup(silentGroup.id).single {
                it.hour == expectedTime.get(java.util.Calendar.HOUR_OF_DAY) &&
                    it.minute == expectedTime.get(java.util.Calendar.MINUTE)
            }
            assertEquals(SoundMode.VIBRATE_ONLY, hotelAlarm.soundMode)
            assertTrue(hotelAlarm.vibrationEnabled)
            assertTrue(hotelAlarm.enabled)
            assertEquals(deadline - 180_000L, repo.getAllScheduled().single { it.alarmId == hotelAlarm.id }.triggerAtMillis)
            val trainIds = repo.getAlarms().filter { it.id != hotelAlarm.id }.map { it.id }
            assertEquals(2, trainIds.size)
            assertTrue(RomancecarAlarmImporter.sync(context, repo, hotelEvents + trainEvents, now))
            assertTrue(db.alarmDao().getById(hotelAlarm.id)!!.enabled)
            assertTrue(RomancecarAlarmImporter.sync(context, repo, trainEvents, now))
            assertFalse(db.alarmDao().getById(hotelAlarm.id)!!.enabled)
            assertTrue(trainIds.all { db.alarmDao().getById(it)!!.enabled })
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }

    @Test fun hotelAt2333ProducesOneEventForThe2330Reminder() {
        val deadline = java.time.Instant.parse("2026-11-09T14:33:00Z").toEpochMilli()
        val now = java.time.Instant.parse("2026-11-09T12:05:00Z").toEpochMilli()
        val booking = org.json.JSONObject().put("departure_ms", deadline).put("arrival_ms", deadline)
            .put("departure_station", "HOTEL EXAMPLE").put("station", "無料キャンセル期限")
        val event = romancecarEventsFromBooking(booking, now).single()
        assertTrue(event.hotelDeadline)
        assertEquals(java.time.Instant.parse("2026-11-09T14:30:00Z").toEpochMilli(), event.atMs - 180_000L)
    }

    @Test fun cancelledHotelCancelsItsOwnPendingSnooze() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val deadline = (now / 60_000L) * 60_000L + 120 * 60_000L
        val booking = org.json.JSONObject().put("departure_ms", deadline).put("arrival_ms", deadline)
            .put("departure_station", "HOTEL EXAMPLE").put("station", "無料キャンセル期限")
        val events = romancecarEventsFromBooking(booking, now)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, events, now))
        val alarm = repo.getAlarms().single()
        val snooze = db.occurrenceDao().insertScheduled(alarm.id, alarm.groupId, deadline + 60_000L, 1)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList(), now))
        assertFalse(repo.getAlarm(alarm.id)!!.enabled)
        assertEquals(com.galaxyalarm.data.model.OccurrenceStatus.CANCELED, db.occurrenceDao().getById(snooze.id)!!.status)
    }

    @Test fun cancellingHotelNeverCancelsSharedTrainSnooze() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val deadline = (now / 60_000L) * 60_000L + 120 * 60_000L
        var departure = deadline - 30 * 60_000L
        while (groupName(departure) != RomancecarAlarmImporter.PREVIOUS_GROUP) departure -= 60_000L
        val hotel = Event(deadline, "無料キャンセル期限", hotelDeadline = true)
        val train = Event(deadline, "到着駅|E123", trainDepartureMs = departure)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(hotel, train), now))
        val alarm = repo.getAlarms().single()
        val snooze = db.occurrenceDao().insertScheduled(alarm.id, alarm.groupId, deadline + 60_000L, 1)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(train), now))
        assertEquals(com.galaxyalarm.data.model.OccurrenceStatus.SCHEDULED, db.occurrenceDao().getById(snooze.id)!!.status)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList(), now))
        assertEquals(com.galaxyalarm.data.model.OccurrenceStatus.SCHEDULED, db.occurrenceDao().getById(snooze.id)!!.status)
    }

    @Test fun equalTrainTimesAreRejectedRatherThanMisclassifiedAsHotel() {
        val now = System.currentTimeMillis()
        val at = now + 60 * 60_000L
        val invalid = org.json.JSONObject().put("departure_ms", at).put("arrival_ms", at)
            .put("departure_station", "出発駅").put("station", "到着駅")
        try {
            romancecarEventsFromBooking(invalid, now)
            fail("equal train times must not be accepted")
        } catch (_: IllegalStateException) { }
    }

    @Test fun incompleteFeedIsRejectedBeforeAnyExistingAlarmIsChanged() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val at = (now / 60_000L) * 60_000L + 120 * 60_000L
        val valid = org.json.JSONObject().put("departure_ms", at).put("arrival_ms", at)
            .put("departure_station", "HOTEL EXAMPLE").put("station", "無料キャンセル期限")
        val initial = romancecarEventsFromFeed(org.json.JSONArray().put(valid), now).first
        assertTrue(RomancecarAlarmImporter.sync(context, repo, initial, now))
        val before = repo.getAlarms().single()
        val beforeReservations = repo.getAllScheduled()
        val malformed = org.json.JSONObject().put("departure_ms", at).put("arrival_ms", at)
            .put("departure_station", "出発駅").put("station", "到着駅")
        try {
            val (events, _) = romancecarEventsFromFeed(org.json.JSONArray().put(valid).put(malformed), now)
            RomancecarAlarmImporter.sync(context, repo, events, now)
            fail("an invalid booking must reject the whole feed")
        } catch (_: IllegalStateException) { }
        assertEquals(before, repo.getAlarm(before.id))
        assertEquals(beforeReservations, repo.getAllScheduled())
    }

    @Test fun combinedFeedAccepts120BookingsAndRejectsOversizedResponse() {
        val now = System.currentTimeMillis()
        val at = (now / 60_000L) * 60_000L + 120 * 60_000L
        val hotel = org.json.JSONObject().put("departure_ms", at).put("arrival_ms", at)
            .put("departure_station", "HOTEL EXAMPLE").put("station", "無料キャンセル期限")
        val bookings = org.json.JSONArray()
        repeat(120) { bookings.put(hotel) }
        val (events, count) = romancecarEventsFromFeed(bookings, now)
        assertEquals(120, count)
        assertEquals(120, events.size)
        bookings.put(hotel)
        try {
            romancecarEventsFromFeed(bookings, now)
            fail("oversized feeds must fail rather than be truncated")
        } catch (_: IllegalStateException) { }
    }

    @Test fun routesEachTrainAndMovesPreviouslyMisplacedManagedAlarms() = runBlocking {
        val originalZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val now = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, 2026); set(java.util.Calendar.MONTH, java.util.Calendar.NOVEMBER)
                set(java.util.Calendar.DAY_OF_MONTH, 5)
                set(java.util.Calendar.HOUR_OF_DAY, 7); set(java.util.Calendar.MINUTE, 37)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val departure811 = now + 34 * 60_000L
            val arrival905 = now + 88 * 60_000L
            val departure831 = now + 54 * 60_000L
            val arrival923 = now + 106 * 60_000L
            val group811 = db.groupDao().insert(AlarmGroup(name = "ロマ 8:11発", enabled = false))
            val group831 = db.groupDao().insert(AlarmGroup(name = "ロマ 8:31発", enabled = true))
            val originalManual = db.alarmDao().insert(AlarmItem(groupId = group831, hour = 7, minute = 25, enabled = false))
            val misplaced = listOf(8 to 8, 9 to 2).map { (hour, minute) ->
                repo.saveAlarmChecked(AlarmItem(groupId = group831, hour = hour, minute = minute,
                    soundMode = SoundMode.VIBRATE_ONLY, vibrationEnabled = true)).also { assertTrue(it.scheduled) }.alarmId
            }
            val originalReservations = repo.getAllScheduled().filter { it.alarmId in misplaced }.associateBy { it.id }
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("romancecar_alarm_slots", 0)
            prefs.edit().putStringSet("managed_ids", misplaced.map { it.toString() }.toSet()).commit()
            val events = listOf(
                Event(departure811, "出発駅|E811", true, departure811),
                Event(arrival905, "到着駅|E811", trainDepartureMs = departure811),
                Event(departure831, "出発駅|E831", true, departure831),
                Event(arrival923, "到着駅|E831", trainDepartureMs = departure831),
            )
            assertTrue(RomancecarAlarmImporter.sync(RuntimeEnvironment.getApplication(), repo, events, now))
            val alarms811 = db.alarmDao().getByGroup(group811)
            val alarms831 = db.alarmDao().getByGroup(group831)
            assertEquals(setOf(8 to 8, 9 to 2), alarms811.map { it.hour to it.minute }.toSet())
            assertEquals(misplaced.toSet(), alarms811.map { it.id }.toSet())
            assertTrue(alarms811.all { it.enabled })
            assertTrue(db.groupDao().getById(group811)!!.enabled)
            assertEquals(setOf(7 to 25, 8 to 28, 9 to 20), alarms831.map { it.hour to it.minute }.toSet())
            assertFalse(db.alarmDao().getById(originalManual)!!.enabled)
            for ((id, original) in originalReservations) {
                assertEquals(original.copy(groupId = group811), db.occurrenceDao().getById(id))
            }
            assertTrue(RomancecarAlarmImporter.sync(RuntimeEnvironment.getApplication(), repo, events, now))
            assertEquals(2, db.alarmDao().getByGroup(group811).size)
            assertEquals(3, db.alarmDao().getByGroup(group831).size)
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }

    @Test fun directBookingCarriesDepartureToBothAlerts() {
        val departure = java.time.Instant.parse("2026-11-04T23:11:00Z").toEpochMilli()
        val arrival = java.time.Instant.parse("2026-11-05T00:05:00Z").toEpochMilli()
        val booking = org.json.JSONObject()
            .put("departure_ms", departure).put("arrival_ms", arrival)
            .put("departure_station", "出発駅|E811").put("station", "到着駅|E811")
        val events = romancecarEventsFromBooking(booking, departure - 30 * 60_000L)
        assertEquals(2, events.size)
        assertEquals(departure, events.single { it.departure }.atMs)
        assertEquals(arrival, events.single { !it.departure }.atMs)
        assertTrue(events.all { it.trainDepartureMs == departure })
        val legacy = romancecarEventsFromBooking(org.json.JSONObject()
            .put("arrival_ms", arrival).put("station", "到着駅"), departure - 30 * 60_000L)
        assertNull(legacy.single().trainDepartureMs)
    }

    @Test fun arrivalWithoutDepartureIsNotMisfiledUnder831() = runBlocking {
        val group831 = db.groupDao().insert(AlarmGroup(name = "ロマ 8:31発"))
        val now = System.currentTimeMillis()
        assertTrue(RomancecarAlarmImporter.sync(RuntimeEnvironment.getApplication(), repo,
            listOf(Event(now + 30 * 60_000L, "本厚木")), now))
        assertTrue(db.alarmDao().getByGroup(group831).isEmpty())
        assertEquals(RomancecarAlarmImporter.LEGACY_GROUP,
            db.groupDao().getById(repo.getAlarms().single().groupId)!!.name)
    }

    @Test fun eveningTrainUsesSilentGroupAndMovesManagedReservations() = runBlocking {
        val originalZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
        try {
        val context = RuntimeEnvironment.getApplication()
        val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 19); set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val departure = now + 72 * 60_000L
        val arrival = now + 116 * 60_000L
        val wrong = db.groupDao().insert(AlarmGroup(name = "ロマ 8:31発"))
        val target = db.groupDao().insert(AlarmGroup(name = "音無し"))
        val old = listOf(departure, arrival).map { at ->
            val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
            repo.saveAlarmChecked(AlarmItem(groupId = wrong, hour = time.get(java.util.Calendar.HOUR_OF_DAY),
                minute = time.get(java.util.Calendar.MINUTE), soundMode = SoundMode.VIBRATE_ONLY))
                .also { assertTrue(it.scheduled) }.alarmId
        }
        val reservations = repo.getAllScheduled().filter { it.alarmId in old }.associateBy { it.id }
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", old.map { it.toString() }.toSet()).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(
            Event(departure, "出発駅|ETEST", true, departure),
            Event(arrival, "到着駅|ETEST", trainDepartureMs = departure)
        ), now))
        assertEquals(old.toSet(), db.alarmDao().getByGroup(target).map { it.id }.toSet())
        assertEquals(setOf(20 to 39, 21 to 23), db.alarmDao().getByGroup(target).map { it.hour to it.minute }.toSet())
        assertTrue(db.alarmDao().getByGroup(wrong).isEmpty())
        reservations.forEach { (id, value) ->
            assertEquals(value.copy(groupId = target), db.occurrenceDao().getById(id))
        }
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
    }

    @Test fun existingEveningRowsMoveOnceWithoutChangingTheirStateOrReservations() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val morning = db.groupDao().insert(AlarmGroup(name = "ロマ 8:31発"))
        val silent = db.groupDao().insert(AlarmGroup(name = "音無し"))
        val ids = listOf(19 to 40, 20 to 23, 20 to 39, 21 to 23).mapIndexed { index, (hour, minute) ->
            db.alarmDao().insert(AlarmItem(groupId = morning, hour = hour, minute = minute,
                enabled = index >= 2, soundMode = SoundMode.VIBRATE_ONLY))
        }
        val states = ids.associateWith { db.alarmDao().getById(it)!! }
        val reservation = db.occurrenceDao().insertScheduled(ids.last(), morning, 123L, 0)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList()))
        ids.forEach { assertEquals(states[it]!!.copy(groupId = silent), db.alarmDao().getById(it)) }
        assertEquals(reservation.copy(groupId = silent), db.occurrenceDao().getById(reservation.id))
        assertTrue(db.alarmDao().getByGroup(morning).isEmpty())
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList()))
        assertEquals(4, db.alarmDao().getByGroup(silent).size)
    }

    @Test fun expiredDepartureSlotStillMovesWhileSameTrainsArrivalIsUpcoming() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val departure = now - 2 * 60_000L
        val arrival = now + 40 * 60_000L
        val target = db.groupDao().insert(AlarmGroup(name = groupName(departure)))
        val wrong = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = departure - 180_000L }
        val old = db.alarmDao().insert(AlarmItem(groupId = wrong,
            hour = time.get(java.util.Calendar.HOUR_OF_DAY), minute = time.get(java.util.Calendar.MINUTE),
            enabled = false, soundMode = SoundMode.VIBRATE_ONLY))
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.toString())).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(
            Event(departure, "出発駅|E811", true, departure),
            Event(arrival, "到着駅|E811", trainDepartureMs = departure),
        ), now))
        assertEquals(target, db.alarmDao().getById(old)!!.groupId)
        assertFalse(db.alarmDao().getById(old)!!.enabled)
        assertTrue(db.alarmDao().getByGroup(wrong).isEmpty())
    }

    @Test fun sameArrivalTimeForDifferentTrainsKeepsBothGroups() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tokyo")).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 8); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val firstDeparture = now + 11 * 60_000L
        val secondDeparture = now + 31 * 60_000L
        val arrival = now + 65 * 60_000L
        val first = db.groupDao().insert(AlarmGroup(name = groupName(firstDeparture)))
        val second = db.groupDao().insert(AlarmGroup(name = groupName(secondDeparture)))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = arrival - 180_000L }
        val old = repo.saveAlarmChecked(AlarmItem(groupId = second,
            hour = time.get(java.util.Calendar.HOUR_OF_DAY), minute = time.get(java.util.Calendar.MINUTE),
            soundMode = SoundMode.VIBRATE_ONLY)).also { assertTrue(it.scheduled) }.alarmId
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.toString())).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(
            Event(arrival, "本厚木|E811", trainDepartureMs = firstDeparture),
            Event(arrival, "本厚木|E831", trainDepartureMs = secondDeparture),
        ), now))
        assertEquals(second, repo.getAlarm(old)!!.groupId)
        assertTrue(repo.getAlarm(old)!!.enabled)
        assertEquals(1, db.alarmDao().getByGroup(first).count { it.enabled })
        assertEquals(1, db.alarmDao().getByGroup(second).count { it.enabled })
    }

    @Test fun existingManualSlotIsReusedAndOldManagedSlotLeavesWrongGroup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val target = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        val wrong = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val hour = time.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = time.get(java.util.Calendar.MINUTE)
        val manual = db.alarmDao().insert(AlarmItem(groupId = target, hour = hour, minute = minute,
            enabled = false, label = "手動", soundMode = SoundMode.VIBRATE_ONLY))
        val old = repo.saveAlarmChecked(AlarmItem(groupId = wrong, hour = hour, minute = minute,
            soundMode = SoundMode.VIBRATE_ONLY)).also { assertTrue(it.scheduled) }.alarmId
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.toString())).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E811", true))))
        assertTrue(db.alarmDao().getById(manual)!!.enabled)
        assertEquals(target, db.alarmDao().getById(old)!!.groupId)
        assertFalse(db.alarmDao().getById(old)!!.enabled)
        assertTrue(db.alarmDao().getByGroup(wrong).isEmpty())
        assertEquals("手動", db.alarmDao().getById(manual)!!.label)
    }

    @Test fun movingOldManagedDuplicateKeepsPendingSnooze() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val target = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        val wrong = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val hour = time.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = time.get(java.util.Calendar.MINUTE)
        db.alarmDao().insert(AlarmItem(groupId = target, hour = hour, minute = minute,
            enabled = false, soundMode = SoundMode.VIBRATE_ONLY))
        val old = repo.saveAlarmChecked(AlarmItem(groupId = wrong, hour = hour, minute = minute,
            soundMode = SoundMode.VIBRATE_ONLY)).also { assertTrue(it.scheduled) }.alarmId
        val snooze = db.occurrenceDao().insertScheduled(old, wrong, at - 120_000L, 1)
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.toString())).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E811", true))))
        assertEquals(snooze.copy(groupId = target), db.occurrenceDao().getById(snooze.id))
        assertEquals(target, db.alarmDao().getById(old)!!.groupId)
    }

    @Test fun finalMinuteKeepsScheduledOldSlotWhenTargetManualSlotIsOff() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val at = now + 210_000L
        val target = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        val wrong = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val hour = time.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = time.get(java.util.Calendar.MINUTE)
        val manual = db.alarmDao().insert(AlarmItem(groupId = target, hour = hour, minute = minute,
            enabled = false, soundMode = SoundMode.VIBRATE_ONLY))
        val old = repo.saveAlarmChecked(AlarmItem(groupId = wrong, hour = hour, minute = minute,
            soundMode = SoundMode.VIBRATE_ONLY)).also { assertTrue(it.scheduled) }.alarmId
        val reservation = repo.getAllScheduled().single { it.alarmId == old }
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.toString())).commit()
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "出発駅|E811", true)), now))
        assertTrue(db.alarmDao().getById(old)!!.enabled)
        assertFalse(db.alarmDao().getById(manual)!!.enabled)
        assertEquals(reservation, db.occurrenceDao().getById(reservation.id))
    }

    @Test fun merge831GroupsKeepsAlarmsAndScheduledSnoozes() = runBlocking {
        val target = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.SHORT_GROUP, enabled = false))
        val source = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.GROUP, enabled = true))
        val unrelated = db.groupDao().insert(AlarmGroup(name = "ロマ 8:11発", enabled = true))
        val manual = db.alarmDao().insert(AlarmItem(groupId = target, hour = 7, minute = 15, enabled = false))
        val moved = db.alarmDao().insert(AlarmItem(groupId = source, hour = 20, minute = 39, enabled = true,
            soundMode = SoundMode.VIBRATE_ONLY))
        val other = db.alarmDao().insert(AlarmItem(groupId = unrelated, hour = 8, minute = 11))
        val original = db.alarmDao().getById(moved)!!
        val regular = db.occurrenceDao().insertScheduled(moved, source, 123L, 0)
        val snooze = db.occurrenceDao().insertScheduled(moved, source, 456L, 1)

        repo.mergeRomancecar831Groups()
        repo.mergeRomancecar831Groups()

        assertEquals(original.copy(groupId = target), db.alarmDao().getById(moved))
        assertEquals(target, db.alarmDao().getById(manual)!!.groupId)
        assertEquals(unrelated, db.alarmDao().getById(other)!!.groupId)
        assertTrue(db.groupDao().getById(target)!!.enabled)
        assertNull(db.groupDao().getById(source))
        assertNotNull(db.groupDao().getById(unrelated))
        assertEquals(regular.copy(groupId = target), db.occurrenceDao().getById(regular.id))
        assertEquals(snooze.copy(groupId = target), db.occurrenceDao().getById(snooze.id))
    }

    @Test fun syncReusesExistingShort831Group() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val target = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E831", true))))
        assertEquals(target, repo.getAlarms().single().groupId)
        assertEquals(listOf(target), repo.getVisibleGroups().map { it.id })
    }

    @Test fun restoreOldBackupDoesNotRecreateLong831Group() = runBlocking {
        val backup = """{
            "groups": [
                {"localId": 1, "name": "ロマンスカー 8:31発"},
                {"localId": 2, "name": "ロマ 8:31発"}
            ],
            "alarms": [
                {"groupLocalId": 1, "groupName": "ロマンスカー 8:31発", "hour": 20, "minute": 39, "enabled": false},
                {"groupLocalId": 2, "groupName": "ロマ 8:31発", "hour": 7, "minute": 15, "enabled": false}
            ]
        }"""
        repo.mergeBackupJson(backup)
        repo.mergeBackupJson(backup)
        val target = repo.getVisibleGroups().single()
        assertEquals(RomancecarAlarmImporter.SHORT_GROUP, target.name)
        assertEquals(2, repo.getAlarms().size)
        assertTrue(repo.getAlarms().all { it.groupId == target.id && !it.enabled })
    }

    @Test fun upgradeMergesDifferentDatesAndPhasesAtSameTimeAndClearsLabel() = runBlocking {
        val group = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.PREVIOUS_GROUP))
        val first = db.alarmDao().insert(legacy(group, "2026/10/23"))
        db.alarmDao().insert(legacy(group, "2026/10/24", departure = false))
        assertEquals(setOf(first), repo.consolidateRomancecarSlots())
        val single = repo.getAlarms().single()
        assertEquals(first, single.id)
        assertEquals("", single.label)
        assertFalse(single.enabled)
        assertTrue(repo.consolidateRomancecarSlots().isEmpty())
    }

    @Test fun upgradeReusesManualSilentSlotAndKeepsOtherManualAlarmsOff() = runBlocking {
        val group = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.GROUP))
        val old = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val manual = db.alarmDao().insert(legacy(group, "").copy(label = ""))
        val unrelated = db.alarmDao().insert(legacy(group, "").copy(label = "", minute = 20))
        db.alarmDao().insert(legacy(old, "2026/10/23").copy(enabled = true))
        assertEquals(setOf(manual), repo.consolidateRomancecarSlots())
        assertEquals(2, repo.getAlarms().size)
        assertTrue(repo.getAlarm(manual)!!.enabled)
        assertFalse(repo.getAlarm(unrelated)!!.enabled)
        assertEquals("", repo.getAlarm(manual)!!.label)
    }

    @Test fun selectionUsesTimeAndGroupWithoutOverwritingRepeatingOrAudibleAlarms() {
        val base = legacy(1, "").copy(id = 10, label = "")
        val repeat = base.copy(id = 1, weekdaysMask = 1)
        val audible = base.copy(id = 2, soundMode = SoundMode.SOUND)
        val otherGroup = base.copy(id = 3, groupId = 2)
        val otherTime = base.copy(id = 4, minute = 18)
        val alarms = listOf(repeat, audible, otherGroup, otherTime, base)
        assertEquals(10L, RomancecarAlarmSlots.select(alarms, 1, 13, 17)!!.id)
        assertEquals(10L, RomancecarAlarmSlots.select(alarms, 1, 13, 17)!!.id)
        assertNull(RomancecarAlarmSlots.select(alarms, 1, 14, 17))
    }
    @Test fun repeatedSyncAndDifferentBookingsUseOneUnlabelledAlarm() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E123", true))))
        val first = repo.getAlarms().single()
        assertEquals("", first.label)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(
            Event(at, "新宿|E123", true), Event(at, "本厚木|E124", trainDepartureMs = at))))
        assertEquals(first.id, repo.getAlarms().single().id)
        assertTrue(repo.getAlarms().single().enabled)
        assertEquals(1, repo.getAllScheduled().size)
    }

    @Test fun bookingChangeKeepsSharedSlotUntilNoBookingNeedsIt() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val firstBooking = Event(at, "新宿|E123", true)
        val secondBooking = Event(at, "新宿|E124", true)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(firstBooking, secondBooking)))
        val oldId = repo.getAlarms().single().id
        val changed = secondBooking.copy(atMs = at + 10 * 60_000L, trainDepartureMs = at + 10 * 60_000L)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(firstBooking, changed)))
        assertTrue(repo.getAlarm(oldId)!!.enabled)
        assertEquals(2, repo.getAlarms().count { it.enabled })
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(changed)))
        assertFalse(repo.getAlarm(oldId)!!.enabled)
        assertEquals(1, repo.getAlarms().count { it.enabled })
        assertEquals(2, repo.getAlarms().size)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(firstBooking, changed)))
        assertEquals(2, repo.getAlarms().size)
    }

    @Test fun managedSlotFromPreviousGroupMovesIntoExistingRomancecar831Group() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val targetGroup = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        val previousGroup = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.LEGACY_GROUP))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val old = repo.saveAlarmChecked(AlarmItem(
            groupId = previousGroup,
            hour = time.get(java.util.Calendar.HOUR_OF_DAY),
            minute = time.get(java.util.Calendar.MINUTE),
            label = "",
            soundMode = SoundMode.VIBRATE_ONLY,
            vibrationEnabled = true,
        ))
        assertTrue(old.scheduled)
        context.getSharedPreferences("romancecar_alarm_slots", 0).edit()
            .putStringSet("managed_ids", setOf(old.alarmId.toString())).commit()

        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E831", true))))

        val moved = repo.getAlarm(old.alarmId)!!
        assertEquals(targetGroup, moved.groupId)
        assertTrue(moved.enabled)
        assertEquals(1, repo.getAlarms().size)
    }

    @Test fun syncDoesNotClaimAnAlreadyEnabledManualSlot() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val at = System.currentTimeMillis() + 30 * 60_000L
        val group = db.groupDao().insert(AlarmGroup(name = groupName(at)))
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val manual = repo.saveAlarmChecked(AlarmItem(groupId = group,
            hour = time.get(java.util.Calendar.HOUR_OF_DAY), minute = time.get(java.util.Calendar.MINUTE),
            label = "自分のメモ", soundMode = SoundMode.VIBRATE_ONLY))
        assertTrue(manual.scheduled)
        db.groupDao().setEnabled(group, false, System.currentTimeMillis())
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E123", true))))
        assertEquals(1, repo.getAlarms().size)
        assertTrue(db.groupDao().getById(group)!!.enabled)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList()))
        assertTrue(repo.getAlarm(manual.alarmId)!!.enabled)
        assertEquals("自分のメモ", repo.getAlarm(manual.alarmId)!!.label)
    }

}
