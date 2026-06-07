package com.galaxyalarm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.entity.ScheduledOccurrence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDbTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun create150Alarms() = runBlocking {
        val gid = db.groupDao().insert(AlarmGroup(name = "G"))
        repeat(150) { i -> db.alarmDao().insert(AlarmItem(groupId = gid, hour = i % 24, minute = i % 60)) }
        assertEquals(150, db.alarmDao().count())
    }

    @Test fun tenSameTimeAlarmsAllRetained() = runBlocking {
        val gid = db.groupDao().insert(AlarmGroup(name = "G"))
        repeat(10) { db.alarmDao().insert(AlarmItem(groupId = gid, hour = 7, minute = 0)) }
        val sameTime = db.alarmDao().getAll().filter { it.hour == 7 && it.minute == 0 }
        assertEquals(10, sameTime.size)
    }

    @Test fun requestCodesAreUnique() = runBlocking {
        val gid = db.groupDao().insert(AlarmGroup(name = "G"))
        val aid = db.alarmDao().insert(AlarmItem(groupId = gid, hour = 7, minute = 0))
        val rcs = mutableListOf<Int>()
        repeat(20) {
            val id = db.occurrenceDao().insert(
                ScheduledOccurrence(alarmId = aid, groupId = gid, triggerAtMillis = 1L, requestCode = 0)
            )
            db.occurrenceDao().update(db.occurrenceDao().getById(id)!!.copy(requestCode = id.toInt()))
            rcs += id.toInt()
        }
        assertEquals(rcs.size, rcs.toSet().size)
    }

    @Test fun deletingGroupCascadesAlarms() = runBlocking {
        val gid = db.groupDao().insert(AlarmGroup(name = "G"))
        db.alarmDao().insert(AlarmItem(groupId = gid, hour = 7, minute = 0))
        val group = db.groupDao().getById(gid)!!
        db.groupDao().delete(group)
        assertTrue(db.alarmDao().getByGroup(gid).isEmpty())
    }
}
