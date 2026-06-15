package com.galaxyalarm.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ストップウォッチの状態をプロセス内シングルトンで保持し、タブ移動で消えないようにする。
 * (鳴動しないため永続化は不要。プロセス生存中は保持。)
 */
object StopwatchController {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** 一時停止までに積み上げた経過(ms)。 */
    @Volatile private var baseElapsed = 0L
    /** 計測再開した実時刻(ms)。running 中のみ有効。 */
    @Volatile private var startedAt = 0L

    val laps = MutableStateFlow<List<Long>>(emptyList())

    fun elapsed(): Long =
        if (_running.value) baseElapsed + (System.currentTimeMillis() - startedAt) else baseElapsed

    fun startStop() {
        if (_running.value) {
            baseElapsed = elapsed()
            _running.value = false
        } else {
            startedAt = System.currentTimeMillis()
            _running.value = true
        }
    }

    fun lapOrReset() {
        if (_running.value) {
            laps.value = listOf(elapsed()) + laps.value
        } else {
            baseElapsed = 0L
            laps.value = emptyList()
        }
    }
}
