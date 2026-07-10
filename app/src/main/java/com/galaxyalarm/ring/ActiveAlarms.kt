package com.galaxyalarm.ring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 現在鳴動中の1件。 */
data class ActiveAlarm(
    val occurrenceId: Long,
    val alarmId: Long,
    val label: String,
    val timeText: String,
)

/**
 * 鳴動中アラームの共有状態。Service が更新、RingActivity が購読(スタック表示用)。
 * プロセス内共有のためのシングルトン。
 */
object ActiveAlarms {
    private val _stack = MutableStateFlow<List<ActiveAlarm>>(emptyList())
    val stack: StateFlow<List<ActiveAlarm>> = _stack

    fun push(a: ActiveAlarm) {
        if (_stack.value.any { it.occurrenceId == a.occurrenceId }) return
        _stack.value = _stack.value + a
    }

    fun remove(occurrenceId: Long) {
        _stack.value = _stack.value.filterNot { it.occurrenceId == occurrenceId }
    }

    fun clear() { _stack.value = emptyList() }

    fun top(): ActiveAlarm? = _stack.value.lastOrNull()
    fun contains(occurrenceId: Long): Boolean =
        _stack.value.any { it.occurrenceId == occurrenceId }
    fun isEmpty(): Boolean = _stack.value.isEmpty()
}
