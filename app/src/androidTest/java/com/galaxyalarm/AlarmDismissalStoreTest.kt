package com.galaxyalarm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyalarm.ring.AlarmDismissalStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDismissalStoreTest {
    @Test fun dismissedOccurrenceRemainsBlockedAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val occurrenceId = System.nanoTime()

        AlarmDismissalStore(context).markDismissed(listOf(occurrenceId))

        assertTrue(AlarmDismissalStore(context).isDismissed(occurrenceId))
        assertFalse(AlarmDismissalStore(context).isDismissed(occurrenceId + 1))
    }
}
