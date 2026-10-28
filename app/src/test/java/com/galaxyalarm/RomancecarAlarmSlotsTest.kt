package com.galaxyalarm

import androidx.room.Room
import com.galaxyalarm.automation.RomancecarAlarmImporter
import com.galaxyalarm.automation.RomancecarAlarmImporter.Event
import com.galaxyalarm.automation.RomancecarAlarmSlots
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
            Event(at, "新宿|E123", true), Event(at, "本厚木|E124"))))
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
        val changed = secondBooking.copy(atMs = at + 10 * 60_000L)
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
        val targetGroup = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.GROUP))
        val previousGroup = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.PREVIOUS_GROUP))
        val at = System.currentTimeMillis() + 30 * 60_000L
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
        val group = db.groupDao().insert(AlarmGroup(name = RomancecarAlarmImporter.GROUP))
        val at = System.currentTimeMillis() + 30 * 60_000L
        val time = java.util.Calendar.getInstance().apply { timeInMillis = at - 180_000L }
        val manual = repo.saveAlarmChecked(AlarmItem(groupId = group,
            hour = time.get(java.util.Calendar.HOUR_OF_DAY), minute = time.get(java.util.Calendar.MINUTE),
            label = "自分のメモ", soundMode = SoundMode.VIBRATE_ONLY))
        assertTrue(manual.scheduled)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, listOf(Event(at, "新宿|E123", true))))
        assertEquals(1, repo.getAlarms().size)
        assertTrue(RomancecarAlarmImporter.sync(context, repo, emptyList()))
        assertTrue(repo.getAlarm(manual.alarmId)!!.enabled)
        assertEquals("自分のメモ", repo.getAlarm(manual.alarmId)!!.label)
    }

}
