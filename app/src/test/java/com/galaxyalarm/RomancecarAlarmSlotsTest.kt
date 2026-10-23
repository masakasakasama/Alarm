package com.galaxyalarm

import androidx.room.Room
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
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
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
        val group = db.groupDao().insert(AlarmGroup(name = "音無し"))
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
        val group = db.groupDao().insert(AlarmGroup(name = "音無し"))
        val old = db.groupDao().insert(AlarmGroup(name = "ロマンスカー（自動）"))
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
}
