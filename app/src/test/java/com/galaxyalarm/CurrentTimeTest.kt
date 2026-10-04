package com.galaxyalarm

import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.currentTimeFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentTimeTest {
    private fun time(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()

    @Test
    fun remainingUpdatesWithoutAlarmDataChanging() = runTest {
        val alarm = time("2026-10-05T06:55:00+09:00")
        var now = time("2026-10-04T22:57:00+09:00")
        var label = ""
        val job = backgroundScope.launch {
            currentTimeFlow { now }.collect { label = TimeFormat.remaining(alarm, it) }
        }
        runCurrent()
        assertEquals("あと 7時間58分", label)

        // Reproduce the screenshot: the same alarm after 20 minutes 52 seconds.
        now = time("2026-10-04T23:17:52+09:00")
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals("あと 7時間37分", label)

        // Wall-clock changes must be reflected, rather than subtracting one second.
        now = time("2026-10-05T00:00:00+09:00")
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals("あと 6時間55分", label)
        job.cancel()
    }

    @Test
    fun restartingCollectionImmediatelyReadsCurrentTime() = runTest {
        var now = time("2026-10-04T23:17:52+09:00")
        val ticks = currentTimeFlow { now }
        assertEquals(now, ticks.first())
        now = time("2026-10-05T06:54:30+09:00")
        assertEquals(now, ticks.first())
        assertEquals("あと 0分", TimeFormat.remaining(time("2026-10-05T06:55:00+09:00"), now))
        assertEquals("まもなく", TimeFormat.remaining(now, now))
    }
}
