package com.galaxyalarm

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyalarm.ring.ActiveAlarm
import com.galaxyalarm.ring.ActiveAlarms
import com.galaxyalarm.ring.AlarmRingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmRingActivityLayoutTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun stopButtonIsVisibleBesideSnoozeNearAlarmTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActiveAlarms.push(
            ActiveAlarm(
                occurrenceId = -9_001L,
                alarmId = -1L,
                label = "アラーム",
                timeText = "8:20 AM",
            )
        )

        try {
            ActivityScenario.launch<AlarmRingActivity>(
                Intent(context, AlarmRingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ).use {
                val stop = composeRule.onNodeWithText("停止").assertIsDisplayed()
                val snooze = composeRule.onNodeWithText("スヌーズ").assertIsDisplayed()
                val stopBounds = stop.fetchSemanticsNode().boundsInRoot
                val snoozeBounds = snooze.fetchSemanticsNode().boundsInRoot
                val screenBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

                assertEquals(stopBounds.top, snoozeBounds.top, 1f)
                assertTrue(stopBounds.bottom < screenBounds.height * 0.6f)

                val previewDelay = InstrumentationRegistry.getArguments()
                    .getString("screenshotDelayMs")
                    ?.toLongOrNull()
                    ?: 0L
                if (previewDelay > 0L) Thread.sleep(previewDelay)

                stop.performClick()
                composeRule.waitUntil { ActiveAlarms.stack.value.isEmpty() }
            }
        } finally {
            ActiveAlarms.clear()
        }
    }
}
