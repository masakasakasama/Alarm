package com.galaxyalarm

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyalarm.ring.ActiveAlarms
import com.galaxyalarm.ring.AlarmStopController
import com.galaxyalarm.scheduler.AlarmIntents
import com.galaxyalarm.service.AlarmService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmStopControllerTest {
    @Test fun immediateStopClearsAServiceStartedAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        try {
            startTestAlarm(context)
            assertTrue(waitUntil { ActiveAlarms.stack.value.isNotEmpty() })

            AlarmStopController.stopAllNow(context)

            assertTrue(waitUntil { ActiveAlarms.stack.value.isEmpty() })
            Thread.sleep(200L)

            startTestAlarm(context)
            assertTrue(waitUntil { ActiveAlarms.stack.value.isNotEmpty() })
        } finally {
            AlarmStopController.stopAllNow(context)
        }
    }

    private fun startTestAlarm(context: Context) {
        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmIntents.ACTION_TEST_FIRE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return condition()
    }
}
