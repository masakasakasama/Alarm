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
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

@Composable
fun AlarmsScreen(vm: MainViewModel, onAddAlarm: () -> Unit, onEditAlarm: (Long) -> Unit) {
    val rows by vm.alarmRows.collectAsStateWithLifecycle()
    val grouped = rows.groupBy { it.groupName }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("アラーム", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddAlarm) { Text("＋ 追加") }
            }
        }
        grouped.forEach { (groupName, list) ->
            item(key = "h_$groupName") {
                Text(groupName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.alarm.id }) { row ->
                AlarmCard(row, onToggle = { vm.toggleAlarm(row.alarm, it) },
                    onClick = { onEditAlarm(row.alarm.id) })
            }
        }
        if (rows.isEmpty()) item {
            Text("アラームがありません。＋追加から登録してください。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    (if (row.alarm.label.isBlank()) "(無題)" else row.alarm.label) +
                        " ・ " + Weekdays.label(row.alarm.weekdaysMask),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundModePill(row.alarm.soundMode)
                    when {
                        row.nextTriggerAt != null ->
                            StatusPill(TimeFormat.nextTrigger(row.nextTriggerAt), PillLevel.OK)
                        row.alarm.enabled && !row.groupEnabled ->
                            StatusPill("グループOFF", PillLevel.WARN)
                        !row.alarm.enabled ->
                            StatusPill("OFF", PillLevel.WARN)
                        else ->
                            StatusPill("予約なし", PillLevel.DANGER)
                    }
                }
            }
            Switch(checked = row.alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SoundModePill(mode: SoundMode) {
    when (mode) {
        SoundMode.SOUND -> StatusPill("🔊 音あり", PillLevel.OK)
        SoundMode.VIBRATE_ONLY -> StatusPill("📳 バイブのみ", PillLevel.WARN)
        SoundMode.SILENT -> StatusPill("🔕 完全無音", PillLevel.WARN)
    }
}
