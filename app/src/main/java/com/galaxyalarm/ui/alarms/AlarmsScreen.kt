package com.galaxyalarm.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.Weekdays
import com.galaxyalarm.ui.AlarmRow
import com.galaxyalarm.ui.GroupRow
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

private val defaultGroupNames = setOf("既定グループ", "デフォルト", "Default", "譌｢螳壹げ繝ｫ繝ｼ繝・")

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
        allRows.filter { it.groupName in defaultGroupNames }
    }
    val title = selectedGroup?.name ?: "アラーム"
    val groupedRows = if (showingGroup) {
        emptyList()
    } else {
        groupRows.filter { row ->
            row.totalCount > 0 && row.group.name !in defaultGroupNames
        }
    }

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
                    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    if (!showingGroup) {
                        Text("未グループのアラームだけを表示", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onAddAlarm) { Text("+ 追加") }
            }
        }

        items(rows, key = { it.alarm.id }) { row ->
            AlarmCard(
                row = row,
                onToggle = { vm.toggleAlarm(row.alarm, it) },
                onClick = { onEditAlarm(row.alarm.id) }
            )
        }

        if (rows.isEmpty()) item {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(
                    if (showingGroup) "このグループにアラームはありません" else "未グループのアラームはありません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (groupedRows.isNotEmpty()) {
            item {
                Text(
                    "グループ化済み",
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
    }
}

@Composable
private fun AlarmCard(row: AlarmRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
                Text(
                    "有効 ${row.enabledCount} / 全 ${row.totalCount} 件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (row.group.enabled) "ON" else "OFF", if (row.group.enabled) PillLevel.OK else PillLevel.WARN)
                    Text("次回 " + TimeFormat.nextTrigger(row.nextTriggerAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = row.group.enabled, onCheckedChange = onToggle)
            Icon(Icons.Filled.ChevronRight, contentDescription = "開く")
        }
    }
}

@Composable
private fun SoundModePill(mode: SoundMode) {
    when (mode) {
        SoundMode.SOUND -> StatusPill("音あり", PillLevel.OK)
        SoundMode.VIBRATE_ONLY -> StatusPill("バイブのみ", PillLevel.WARN)
        SoundMode.SILENT -> StatusPill("無音", PillLevel.WARN)
    }
}
