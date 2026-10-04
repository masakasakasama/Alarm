package com.galaxyalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

// Read wall time on every tick and immediately when lifecycle collection restarts.
internal fun currentTimeFlow(clock: () -> Long = System::currentTimeMillis) = flow {
    while (true) {
        emit(clock())
        delay(1000L)
    }
}

@Composable
internal fun rememberCurrentTimeMillis(): Long {
    val ticks = remember { currentTimeFlow() }
    val now by ticks.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    return now
}
