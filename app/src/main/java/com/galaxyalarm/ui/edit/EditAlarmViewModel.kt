package com.galaxyalarm.ui.edit

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.backup.GitHubBackupClient
import com.galaxyalarm.backup.GitHubBackupStore
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.repo.AlarmSaveResult
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class EditAlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val container = (app as AlarmApplication).container
    private val repo = container.repository

    val draft = MutableStateFlow<AlarmItem?>(null)
    val groups = MutableStateFlow<List<AlarmGroup>>(emptyList())

    fun load(alarmId: Long, presetGroupId: Long = 0L, duplicate: Boolean = false) = viewModelScope.launch {
        val ungroupedId = repo.ensureDefaultGroup()
        val allGroups = repo.getGroups()
        groups.value = allGroups
        draft.value = if (alarmId > 0) {
            repo.getAlarm(alarmId)?.let { if (duplicate) it.copy(id = 0L) else it }?.withoutSilent()
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

    fun save(onResult: (AlarmSaveResult) -> Unit) = viewModelScope.launch {
        val item = draft.value ?: return@launch
        val requested = item.withoutSilent().copy(enabled = true)
        val saveResult = repo.saveAlarmChecked(requested)

        // 「追加」は常にONで保存する操作。
        // 同じ定義が既にある場合は重複を増やさず、その既存アラームをONにして完了する。
        // 編集中の重複だけは従来どおり確認ダイアログへ返す。
        val result = if (saveResult.duplicateOf != null && item.id == 0L) {
            val existingId = saveResult.duplicateOf
            val enabled = repo.setAlarmEnabled(existingId, true)
            if (enabled) {
                AlarmSaveResult(alarmId = existingId, scheduled = true)
            } else {
                AlarmSaveResult(
                    alarmId = existingId,
                    scheduled = false,
                    error = "同じ設定の既存アラームをONにできませんでした。権限と予約状態を確認してください"
                )
            }
        } else {
            saveResult
        }

        if (result.duplicateOf != null) {
            onResult(result)
            return@launch
        }
        if (!result.scheduled) {
            container.reliabilityChecker.runCheck()
            Toast.makeText(
                appContext,
                result.error ?: "アラームを予約できませんでした。現在の設定は変更していません。権限を確認して再度保存してください",
                Toast.LENGTH_LONG,
            ).show()
            onResult(result)
            return@launch
        }

        draft.value = draft.value?.copy(id = result.alarmId, enabled = true)
        refreshWidgets()
        backupIfConfigured()
        onResult(result)
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
