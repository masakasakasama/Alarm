package com.galaxyalarm.ui.alarms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.Weekdays
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.ui.AlarmRow
import com.galaxyalarm.ui.GroupRow
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.clock.NextAlarmCard
import com.galaxyalarm.ui.clock.NowCard
import com.galaxyalarm.ui.clock.RunningTimerCard
import com.galaxyalarm.ui.clock.WorldClockCard
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    vm: MainViewModel,
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    groupId: Long? = null,
) {
    val allRows by vm.alarmRows.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val groupRows by vm.groupRows.collectAsStateWithLifecycle()
    val selectedGroup = groups.firstOrNull { it.id == groupId }
    val showingGroup = selectedGroup != null && groupId != null && groupId > 0L
    val rows = if (showingGroup) {
        allRows.filter { it.alarm.groupId == groupId }
    } else {
        allRows.filter { AlarmRepository.isDefaultGroupName(it.groupName) }
    }
    val groupedRows = if (showingGroup) emptyList() else groupRows.filter { it.totalCount > 0 }
    var actionTarget by remember { mutableStateOf<AlarmRow?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(selectedGroup?.name ?: "アラーム", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (!showingGroup) {
                        Text("グループなしのアラーム", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onAddAlarm) { Text("+ 追加") }
            }
        }

        // トップレベル表示のときは現在時刻+次のアラームを上部に出す(旧・時計タブの内容)。
        if (!showingGroup) {
            item { NowCard() }
            item { NextAlarmCard(vm = vm, onAddAlarm = onAddAlarm) }
            item { RunningTimerCard() }
        }

        items(rows, key = { it.alarm.id }) { row ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        vm.deleteAlarm(row.alarm)
                        true
                    } else {
                        false
                    }
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "削除",
                            tint = Color.White,
                            modifier = Modifier.padding(end = 20.dp)
                        )
                    }
                }
            ) {
                AlarmCard(
                    row = row,
                    onToggle = { vm.toggleAlarm(row.alarm, it) },
                    onClick = { onEditAlarm(row.alarm.id) },
                    onLongClick = { actionTarget = row }
                )
            }
        }

        if (rows.isEmpty()) item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(
                    if (showingGroup) "このグループにアラームはありません" else "グループなしのアラームはありません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (groupedRows.isNotEmpty()) {
            item {
                Text(
                    "グループ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            items(groupedRows, key = { it.group.id }) { row ->
                GroupSummaryCard(
                    row = row,
                    onOpen = { onOpenGroup(row.group.id) },
                    onToggle = { vm.toggleGroup(row.group, it) }
                )
            }
        }

        // 世界時計は一覧の最後に置く(旧・時計タブの内容)。
        if (!showingGroup) {
            item { WorldClockCard() }
        }
    }

    // 長押しメニュー: 複製 / 削除
    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(TimeFormat.hourMinute12(target.alarm.hour, target.alarm.minute) + " のアラーム") },
            text = { Text("操作を選んでください。") },
            confirmButton = {
                TextButton(onClick = { vm.duplicateAlarm(target.alarm); actionTarget = null }) { Text("複製") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteAlarm(target.alarm); actionTarget = null }) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { actionTarget = null }) { Text("キャンセル") }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(row: AlarmRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    // タップで編集、長押しで複製。
    SectionCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    TimeFormat.hourMinute12(row.alarm.hour, row.alarm.minute),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    row.alarm.label.ifBlank { "ラベルなし" } + " ・ " + Weekdays.label(row.alarm.weekdaysMask),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundModePill(row.alarm.soundMode)
                    when {
                        row.nextTriggerAt != null -> StatusPill(TimeFormat.nextTrigger(row.nextTriggerAt), PillLevel.OK)
                        row.alarm.enabled && !row.groupEnabled -> StatusPill("グループOFF", PillLevel.WARN)
                        !row.alarm.enabled -> StatusPill("OFF", PillLevel.WARN)
                        else -> StatusPill("未予約", PillLevel.DANGER)
                    }
                }
            }
            Switch(checked = row.alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun GroupSummaryCard(row: GroupRow, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("有効 ${row.enabledCount} / 全 ${row.totalCount} 件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (row.isOn) "ON" else "OFF", if (row.isOn) PillLevel.OK else PillLevel.WARN)
                    Text("次回 " + TimeFormat.nextTrigger(row.nextTriggerAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = row.isOn, onCheckedChange = onToggle)
            Icon(Icons.Filled.ChevronRight, contentDescription = "開く")
        }
    }
}

@Composable
private fun SoundModePill(mode: SoundMode) {
    when (mode) {
        SoundMode.SOUND -> StatusPill("音あり", PillLevel.OK)
        SoundMode.VIBRATE_ONLY -> StatusPill("音なし", PillLevel.WARN)
        SoundMode.SILENT -> StatusPill("音なし", PillLevel.WARN)
    }
}
