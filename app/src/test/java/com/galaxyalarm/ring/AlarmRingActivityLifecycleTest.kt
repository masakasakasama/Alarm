package com.galaxyalarm.ring

import android.app.Application
import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmRingActivityLifecycleTest {
    private val occurrenceId = 8_172L
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        ActiveAlarms.clear()
        // Dismissal IDs are deliberately durable; each stop test uses a distinct ID.
        ActiveAlarms.push(ActiveAlarm(occurrenceId, 12L, "起床", "7:00 AM"))
    }

    @After fun tearDown() { ActiveAlarms.clear() }

    @Test fun backNavigationClosesScreenWithoutDismissingAlarm() {
        val controller = Robolectric.buildActivity(AlarmRingActivity::class.java).setup()
        val activity = controller.get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
        assertTrue(ActiveAlarms.contains(occurrenceId))
        assertFalse(AlarmDismissalStore(context).isDismissed(occurrenceId))
        controller.pause().stop().destroy()
        assertTrue(ActiveAlarms.contains(occurrenceId))
    }

    @Test fun lockAndUnlockLifecycleDoesNotDismissAlarm() {
        val controller = Robolectric.buildActivity(AlarmRingActivity::class.java).setup()
        controller.pause().stop()
        assertTrue(ActiveAlarms.contains(occurrenceId))
        controller.restart().start().resume()
        assertTrue(ActiveAlarms.contains(occurrenceId))
        assertFalse(AlarmDismissalStore(context).isDismissed(occurrenceId))
        controller.pause().stop().destroy()
    }

    @Test fun recreatingScreenDoesNotDismissAlarm() {
        val controller = Robolectric.buildActivity(AlarmRingActivity::class.java).setup()
        controller.recreate()
        assertTrue(ActiveAlarms.contains(occurrenceId))
        assertFalse(AlarmDismissalStore(context).isDismissed(occurrenceId))
        controller.pause().stop().destroy()
    }

    @Test fun powerKeyDoesNotDismissAlarm() {
        val controller = Robolectric.buildActivity(AlarmRingActivity::class.java).setup()
        controller.get().onKeyDown(
            KeyEvent.KEYCODE_POWER,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER),
        )
        assertTrue(ActiveAlarms.contains(occurrenceId))
        assertFalse(AlarmDismissalStore(context).isDismissed(occurrenceId))
        controller.pause().stop().destroy()
    }

    @Test fun volumeKeyStillExplicitlyStopsAlarm() {
        val id = 8_173L
        ActiveAlarms.clear()
        ActiveAlarms.push(ActiveAlarm(id, 12L, "起床", "7:00 AM"))
        val controller = Robolectric.buildActivity(AlarmRingActivity::class.java).setup()
        controller.get().onKeyDown(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
        )
        assertTrue(ActiveAlarms.isEmpty())
        assertTrue(AlarmDismissalStore(context).isDismissed(id))
        controller.pause().stop().destroy()
    }
}
