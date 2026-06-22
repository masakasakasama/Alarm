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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val AmColor = Color(0xFF80DEEA)   // シアン (朝)
private val PmColor = Color(0xFFCE93D8)   // ラベンダー (午後)

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
    val allEnabled = rows.isNotEmpty() && rows.all { it.alarm.enabled }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rows.isNotEmpty()) {
                        Switch(
                            checked = allEnabled,
                            onCheckedChange = { vm.toggleAllAlarms(rows, it) },
                        )
                    }
                    TextButton(onClick = onAddAlarm) { Text("+ 追加") }
                }
            }
        }

        // トップレベル表示のときは現在時刻+次のアラームを上部に出す(旧・時計タブの内容)。
        if (!showingGroup) {
            item { NowCard() }
            item { NextAlarmCard(vm = vm, onAddAlarm = onAddAlarm, onEditAlarm = onEditAlarm) }
            if (groupedRows.isNotEmpty()) {
                item {
                    GroupStrip(
                        rows = groupedRows,
                        onOpenGroup = onOpenGroup,
                        onToggle = { group, enabled -> vm.toggleGroup(group, enabled) }
                    )
                }
            }
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
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAm = row.alarm.hour < 12
            val label = row.alarm.label.trim()
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeFormat.hourMinuteOnly(row.alarm.hour, row.alarm.minute),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isAm) " AM" else " PM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAm) AmColor else PmColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (label.isBlank()) Weekdays.label(row.alarm.weekdaysMask) else "$label ・ ${Weekdays.label(row.alarm.weekdaysMask)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SoundModePill(row.alarm.soundMode)
                when {
                    row.nextTriggerAt != null -> Text(TimeFormat.nextTriggerDay(row.nextTriggerAt), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    row.alarm.enabled && !row.groupEnabled -> StatusPill("G-OFF", PillLevel.WARN)
                    row.alarm.enabled -> StatusPill("未予約", PillLevel.DANGER)
                    else -> StatusPill("OFF", PillLevel.WARN)
                }
                Switch(checked = row.alarm.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun GroupStrip(
    rows: List<GroupRow>,
    onOpenGroup: (Long) -> Unit,
    onToggle: (com.galaxyalarm.data.entity.AlarmGroup, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("グループ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.group.id }) { row ->
                Card(
                    onClick = { onOpenGroup(row.group.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.width(130.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.group.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.requiredSize(36.dp).wrapContentSize(Alignment.Center)) {
                                Switch(
                                    checked = row.isOn,
                                    onCheckedChange = { onToggle(row.group, it) },
                                    modifier = Modifier.scale(0.6f)
                                )
                            }
                        }
                        Text(
                            "有効 ${row.enabledCount} / 全 ${row.totalCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            TimeFormat.nextTrigger(row.nextTriggerAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
