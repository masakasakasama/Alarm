package com.galaxyalarm

import android.app.Application
import androidx.room.Room
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.reliability.PermissionChecker
import com.galaxyalarm.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GroupOrderTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AlarmRepository

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scheduler = AlarmScheduler(
            context, db.groupDao(), db.alarmDao(), db.occurrenceDao(), db.eventLogDao(), PermissionChecker(context)
        )
        repo = AlarmRepository(db, db.groupDao(), db.alarmDao(), db.occurrenceDao(), db.eventLogDao(), scheduler)
    }

    @After fun tearDown() { db.close() }

    @Test fun movesPersistWithDuplicateOrdersAndPreserveAlarmState() = runBlocking {
        val hidden = db.groupDao().insert(AlarmGroup(name = AlarmRepository.DEFAULT_GROUP_NAME, sortOrder = Int.MIN_VALUE))
        val a = db.groupDao().insert(AlarmGroup(name = "A", sortOrder = 5, enabled = false))
        val b = db.groupDao().insert(AlarmGroup(name = "B", sortOrder = 5))
        val c = db.groupDao().insert(AlarmGroup(name = "C", sortOrder = 20))
        val alarmId = db.alarmDao().insert(AlarmItem(groupId = b, hour = 8, minute = 30))
        val alarm = db.alarmDao().getById(alarmId)
        val reservation = db.occurrenceDao().insertScheduled(alarmId, b, 123L, 0)

        repo.moveGroup(b, moveUp = true)
        assertEquals(listOf(b, a, c), repo.getVisibleGroups().map { it.id })
        assertEquals(listOf(0, 1, 2), repo.getVisibleGroups().map { it.sortOrder })
        assertEquals(listOf(hidden, b, a, c), db.groupDao().observeAll().first().map { it.id })
        repo.moveGroup(b, moveUp = false)
        assertEquals(listOf(a, b, c), repo.getVisibleGroups().map { it.id })
        assertEquals(false, db.groupDao().getById(a)!!.enabled)
        assertEquals(alarm, db.alarmDao().getById(alarmId))
        assertEquals(listOf(reservation), db.occurrenceDao().getAllScheduled())
        assertEquals(Int.MIN_VALUE, db.groupDao().getById(hidden)!!.sortOrder)
    }

    @Test fun boundariesAndDeletedOrHiddenGroupsDoNotChangeOrder() = runBlocking {
        val hidden = repo.ensureDefaultGroup()
        val a = repo.addGroup("A")
        val b = repo.addGroup("B")
        val before = db.groupDao().getAll()
        repo.moveGroup(a, moveUp = true)
        repo.moveGroup(b, moveUp = false)
        repo.moveGroup(hidden, moveUp = false)
        repo.moveGroup(-1L, moveUp = true)
        assertEquals(before, db.groupDao().getAll())
    }

    @Test fun newGroupsAppendAfterDeletionAndRenameKeepsNewOrder() = runBlocking {
        val a = repo.addGroup("A")
        val b = repo.addGroup("B")
        val c = repo.addGroup("C")
        val staleC = db.groupDao().getById(c)!!
        repo.moveGroup(c, moveUp = true)
        repo.renameGroup(staleC, "renamed")
        assertEquals(listOf(a, c, b), repo.getVisibleGroups().map { it.id })
        db.groupDao().delete(db.groupDao().getById(a)!!)
        val d = repo.addGroup("D")
        assertEquals(listOf(c, b, d), repo.getVisibleGroups().map { it.id })
        assertEquals("renamed", db.groupDao().getById(c)!!.name)
    }

    @Test fun rapidMovesUseTheLatestStoredOrder() = runBlocking {
        val a = repo.addGroup("A")
        val b = repo.addGroup("B")
        val c = repo.addGroup("C")
        val d = repo.addGroup("D")
        (1..3).map { async(Dispatchers.Default) { repo.moveGroup(d, moveUp = true) } }.awaitAll()
        assertEquals(listOf(d, a, b, c), repo.getVisibleGroups().map { it.id })
    }
}
