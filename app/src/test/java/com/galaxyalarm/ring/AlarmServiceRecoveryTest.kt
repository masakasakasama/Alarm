package com.galaxyalarm.ring

import android.app.Application
import android.app.Service
import android.content.Intent
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmServiceRecoveryTest {
    @After fun tearDown() { ActiveAlarms.clear() }

    @Test fun confirmedBackupPreservesRecoveryOfRingingAlarm() {
        val controller = Robolectric.buildService(AlarmService::class.java).create()
        val service = controller.get()
        val id = 8_174L
        ActiveAlarms.push(ActiveAlarm(id, 12L, "起床", "7:00 AM"))
        // Seed the completed-output state, without real audio hardware or an app DB.
        val confirmed = AlarmService::class.java.getDeclaredField("confirmedOutputOccurrences")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (confirmed.get(service) as MutableSet<Long>).add(id)
        val scope = AlarmService::class.java.getDeclaredField("scope").apply { isAccessible = true }
        // Backup cancellation is unrelated to the Android restart policy under test.
        (scope.get(service) as CoroutineScope).cancel()
        val intent = Intent(service, AlarmService::class.java).apply {
            action = AlarmIntents.ACTION_FIRE
            putExtra(AlarmIntents.EXTRA_OCCURRENCE_ID, id)
            putExtra(AlarmIntents.EXTRA_BACKUP_FIRE, true)
        }
        try {
            val policy = service.onStartCommand(intent, 0, 2)
            assertEquals(Service.START_REDELIVER_INTENT, policy)
            assertTrue(ActiveAlarms.contains(id))
        } finally {
            controller.destroy()
        }
    }
}
