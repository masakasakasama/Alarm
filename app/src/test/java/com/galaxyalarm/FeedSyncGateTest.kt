package com.galaxyalarm

import com.galaxyalarm.automation.FeedSyncGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedSyncGateTest {
    @Test fun overlappingFetchesCannotApplyResponsesOutOfOrder() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        launch {
            FeedSyncGate.serialize {
                order += "first fetch"
                releaseFirst.await()
                order += "first apply"
            }
        }
        runCurrent()
        launch {
            FeedSyncGate.serialize {
                order += "second fetch"
                order += "second apply"
            }
        }
        runCurrent()
        assertEquals(listOf("first fetch"), order)
        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("first fetch", "first apply", "second fetch", "second apply"), order)
    }
}
