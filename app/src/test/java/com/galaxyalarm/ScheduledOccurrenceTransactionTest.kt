package com.galaxyalarm

import androidx.room.Room
import com.galaxyalarm.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ScheduledOccurrenceTransactionTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun concurrentAllocationsCommitUniqueNonzeroRequestCodes() = runBlocking {
        val dao = db.occurrenceDao()
        val rows = (1..20).map {
            async(Dispatchers.Default) { dao.insertScheduled(1L, 1L, 123L, 0) }
        }.awaitAll()
        assertEquals(20, rows.map { it.requestCode }.toSet().size)
        assertTrue(rows.all { it.id == it.requestCode.toLong() && it.requestCode > 0 })
        assertEquals(20, dao.getAllScheduled().size)
        assertNull(dao.getByRequestCode(0))
    }

    @Test fun failureAfterInsertRollsBackPlaceholderAndKeepsExistingReservations() = runBlocking {
        val dao = db.occurrenceDao()
        val existing = dao.insertScheduled(1L, 1L, 123L, 0)
        // Force the validation failure between INSERT and UPDATE in the production DAO.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sqlite_sequence SET seq = ${Int.MAX_VALUE} WHERE name = 'scheduled_occurrences'"
        )
        var rejected = false
        try {
            dao.insertScheduled(1L, 1L, 456L, 0)
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
        assertNull(dao.getByRequestCode(0))
        assertEquals(listOf(existing), dao.getAllScheduled())
    }
}
