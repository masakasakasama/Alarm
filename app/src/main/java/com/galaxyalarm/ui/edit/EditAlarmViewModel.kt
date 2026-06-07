package com.galaxyalarm.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.backup.GitHubBackupClient
import com.galaxyalarm.backup.GitHubBackupStore
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 追加/編集画面の状態保持。alarmId<=0 は新規。 */
class EditAlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val repo = (app as AlarmApplication).container.repository

    val draft = MutableStateFlow<AlarmItem?>(null)
    val groups = MutableStateFlow<List<AlarmGroup>>(emptyList())

    fun load(alarmId: Long) = viewModelScope.launch {
        val gs = repo.getGroups()
        groups.value = gs
        draft.value = if (alarmId > 0) {
            repo.getAlarm(alarmId)
        } else {
            val gid = repo.ensureDefaultGroup()
            // 新規追加時は現在時刻を初期値にする。
            val now = java.util.Calendar.getInstance()
            AlarmItem(
                groupId = gs.firstOrNull()?.id ?: gid,
                hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                minute = now.get(java.util.Calendar.MINUTE)
            )
        }
    }

    fun update(transform: (AlarmItem) -> AlarmItem) {
        draft.value = draft.value?.let(transform)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        draft.value?.let { repo.saveAlarm(it.copy(enabled = true)) }
        backupIfConfigured()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        draft.value?.let { if (it.id > 0) repo.deleteAlarm(it) }
        backupIfConfigured()
        onDone()
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
}
