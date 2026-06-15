package com.galaxyalarm.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.backup.GitHubBackupClient
import com.galaxyalarm.backup.GitHubBackupStore
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class EditAlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val repo = (app as AlarmApplication).container.repository

    val draft = MutableStateFlow<AlarmItem?>(null)
    val groups = MutableStateFlow<List<AlarmGroup>>(emptyList())

    fun load(alarmId: Long, presetGroupId: Long = 0L) = viewModelScope.launch {
        val ungroupedId = repo.ensureDefaultGroup()
        val allGroups = repo.getGroups()
        groups.value = allGroups
        draft.value = if (alarmId > 0) {
            repo.getAlarm(alarmId)?.withoutSilent()
        } else {
            val now = Calendar.getInstance()
            // グループ詳細から追加した場合はそのグループに所属させる。無ければ未グループ。
            val targetGroup = if (presetGroupId > 0 && allGroups.any { it.id == presetGroupId }) {
                presetGroupId
            } else {
                allGroups.firstOrNull { it.id == ungroupedId }?.id ?: ungroupedId
            }
            AlarmItem(
                groupId = targetGroup,
                hour = now.get(Calendar.HOUR_OF_DAY),
                minute = now.get(Calendar.MINUTE)
            )
        }
    }

    fun update(transform: (AlarmItem) -> AlarmItem) {
        draft.value = draft.value?.let(transform)?.withoutSilent()
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        draft.value?.let { repo.saveAlarm(it.withoutSilent().copy(enabled = true)) }
        refreshWidgets()
        backupIfConfigured()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        draft.value?.let { if (it.id > 0) repo.deleteAlarm(it) }
        refreshWidgets()
        backupIfConfigured()
        onDone()
    }

    private fun refreshWidgets() {
        runCatching { NextAlarmWidgetProvider.refresh(appContext) }
    }

    private fun AlarmItem.withoutSilent(): AlarmItem =
        if (soundMode == SoundMode.SILENT) copy(soundMode = SoundMode.VIBRATE_ONLY, vibrationEnabled = true) else this

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
