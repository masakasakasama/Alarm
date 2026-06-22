package com.galaxyalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.backup.GitHubBackupClient
import com.galaxyalarm.backup.GitHubBackupStore
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.Weekdays
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.reliability.ReliabilityReport
import com.galaxyalarm.scheduler.NextTriggerCalculator
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupRow(
    val group: AlarmGroup,
    val enabledCount: Int,
    val totalCount: Int,
    val nextTriggerAt: Long?,
) {
    val isOn: Boolean
        get() = enabledCount > 0
}

data class AlarmRow(
    val alarm: AlarmItem,
    val groupName: String,
    val groupEnabled: Boolean,
    val nextTriggerAt: Long?,   // 有効かつグループ有効かつ権限ありのときのみ非null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val container = (app as AlarmApplication).container
    private val repo = container.repository
    private val permissions = container.permissions

    val canScheduleExact = MutableStateFlow(permissions.canScheduleExactAlarms())
    val report = MutableStateFlow<ReliabilityReport?>(null)

    private val groupsFlow = repo.observeGroups()
    private val alarmsFlow = repo.observeAlarms()

    val groupRows: StateFlow<List<GroupRow>> =
        combine(groupsFlow, alarmsFlow) { groups, alarms ->
            groups.filterNot { AlarmRepository.isDefaultGroupName(it.name) }.map { g ->
                val inGroup = alarms.filter { it.groupId == g.id }
                val enabled = inGroup.filter { it.enabled }
                val next = if (g.enabled && enabled.isNotEmpty() && permissions.canScheduleExactAlarms()) {
                    enabled.mapNotNull { nextOf(it) }.minOrNull()
                } else null
                GroupRow(g, enabled.size, inGroup.size, next)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alarmRows: StateFlow<List<AlarmRow>> =
        combine(groupsFlow, alarmsFlow) { groups, alarms ->
            val gmap = groups.associateBy { it.id }
            alarms.map { a ->
                val g = gmap[a.groupId]
                val gEnabled = g?.enabled == true
                val next = if (a.enabled && gEnabled && permissions.canScheduleExactAlarms()) nextOf(a) else null
                AlarmRow(a, g?.name ?: "(不明)", gEnabled, next)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<AlarmGroup>> =
        groupsFlow
            .map { groups -> groups.filterNot { AlarmRepository.isDefaultGroupName(it.name) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs = repo.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun nextOf(a: AlarmItem): Long =
        NextTriggerCalculator.nextTrigger(a.hour, a.minute, a.weekdaysMask)

    init { refreshPermission(); runCheck(); viewModelScope.launch { startupSync() } }

    private suspend fun startupSync() = runCatching {
        val store = GitHubBackupStore(appContext)
        val settings = store.load()
        if (settings.token.isBlank() || settings.gistId.isBlank()) return@runCatching
        val json = GitHubBackupClient.download(settings.token, settings.gistId)
        repo.mergeBackupJson(json)
        refreshWidgets()
        runCheck()
    }

    fun refreshPermission() { canScheduleExact.value = permissions.canScheduleExactAlarms() }

    fun runCheck() = viewModelScope.launch { report.value = container.reliabilityChecker.runCheck() }
    fun repair() = viewModelScope.launch { report.value = container.reliabilityChecker.repair() }

    fun toggleGroup(group: AlarmGroup, enabled: Boolean) =
        viewModelScope.launch { repo.setGroupEnabled(group.id, enabled); refreshWidgets(); runCheck(); backupIfConfigured() }

    fun addGroup(name: String) = viewModelScope.launch { repo.addGroup(name); refreshWidgets(); backupIfConfigured() }
    fun renameGroup(group: AlarmGroup, name: String) = viewModelScope.launch { repo.renameGroup(group, name); refreshWidgets(); backupIfConfigured() }
    fun deleteGroup(group: AlarmGroup) = viewModelScope.launch { repo.deleteGroup(group); refreshWidgets(); runCheck(); backupIfConfigured() }

    fun toggleAlarm(alarm: AlarmItem, enabled: Boolean) =
        viewModelScope.launch { repo.setAlarmEnabled(alarm.id, enabled); refreshWidgets(); runCheck(); backupIfConfigured() }

    fun toggleAllAlarms(rows: List<AlarmRow>, enabled: Boolean) =
        viewModelScope.launch {
            rows.forEach { repo.setAlarmEnabled(it.alarm.id, enabled) }
            refreshWidgets(); runCheck(); backupIfConfigured()
        }

    fun deleteAlarm(alarm: AlarmItem) = viewModelScope.launch { repo.deleteAlarm(alarm); refreshWidgets(); runCheck(); backupIfConfigured() }

    /** アラームを複製(長押し用)。同じグループに同設定のコピーを作る。 */
    fun duplicateAlarm(alarm: AlarmItem) = viewModelScope.launch {
        repo.saveAlarm(alarm.copy(id = 0L))
        refreshWidgets(); runCheck(); backupIfConfigured()
    }

    /** 権限付与後など、全再スケジュール。 */
    fun rescheduleAll() = viewModelScope.launch { repo.rescheduleAll("ui-request"); refreshWidgets(); runCheck() }

    suspend fun exportBackupJson(): String = repo.exportBackupJson()

    suspend fun mergeBackupJson(json: String): Pair<Int, Int> {
        val result = repo.mergeBackupJson(json)
        refreshWidgets()
        runCheck()
        backupIfConfigured()
        return result
    }

    private fun refreshWidgets() {
        runCatching { NextAlarmWidgetProvider.refresh(appContext) }
    }

    private suspend fun backupIfConfigured() {
        runCatching {
            val store = GitHubBackupStore(appContext)
            val settings = store.load()
            if (settings.token.isBlank()) return
            val result = GitHubBackupClient.upload(settings.token, settings.gistId, repo.exportBackupJson())
            if (result.gistId != settings.gistId) store.saveGistId(result.gistId)
        }
    }

    val nextAlarmRow: StateFlow<AlarmRow?> = alarmRows
        .map { rows -> rows.filter { it.nextTriggerAt != null }.minByOrNull { it.nextTriggerAt!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        fun weekdayLabel(mask: Int) = Weekdays.label(mask)
    }
}
