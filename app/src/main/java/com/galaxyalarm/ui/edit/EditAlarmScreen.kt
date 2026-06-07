package com.galaxyalarm.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.data.model.Weekdays
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.WheelTimePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Long, onDone: () -> Unit, vm: EditAlarmViewModel = viewModel()) {
    LaunchedEffect(alarmId) { vm.load(alarmId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val a = draft ?: return

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(if (alarmId > 0) "アラームを編集" else "アラームを追加",
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        SectionCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    TimeFormat.hourMinute12(a.hour, a.minute),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                WheelTimePicker(hour24 = a.hour, minute = a.minute) { h, m ->
                    vm.update { it.copy(hour = h, minute = m) }
                }
            }
        }

        // ラベル
        SectionCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = a.label, onValueChange = { v -> vm.update { it.copy(label = v) } },
                label = { Text("ラベル") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }

        // 曜日
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("曜日 (${Weekdays.label(a.weekdaysMask)})", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Weekdays.LABELS.forEachIndexed { i, lbl ->
                        FilterChip(
                            selected = Weekdays.has(a.weekdaysMask, i),
                            onClick = { vm.update { it.copy(weekdaysMask = Weekdays.toggle(it.weekdaysMask, i)) } },
                            label = { Text(lbl) }
                        )
                    }
                }
                Text("選択なし=一度きり", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // グループ
        SectionCard(Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            val current = groups.firstOrNull { it.id == a.groupId }?.name ?: "選択"
            Column {
                Text("グループ (必須)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    TextField(
                        value = current, onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        groups.forEach { g ->
                            DropdownMenuItem(text = { Text(g.name) }, onClick = {
                                vm.update { it.copy(groupId = g.id) }; expanded = false
                            })
                        }
                    }
                }
            }
        }

        // 音モード
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("音モード", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoundChip("🔊 音あり", a.soundMode == SoundMode.SOUND) { vm.update { it.copy(soundMode = SoundMode.SOUND) } }
                    SoundChip("📳 バイブのみ", a.soundMode == SoundMode.VIBRATE_ONLY) { vm.update { it.copy(soundMode = SoundMode.VIBRATE_ONLY) } }
                    SoundChip("🔕 完全無音", a.soundMode == SoundMode.SILENT) { vm.update { it.copy(soundMode = SoundMode.SILENT) } }
                }
                if (a.soundMode == SoundMode.SILENT) {
                    Text("完全無音でも通知と全画面表示は出ます。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // バイブ
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("バイブ", a.vibrationEnabled) { v -> vm.update { it.copy(vibrationEnabled = v) } }
                if (a.vibrationEnabled) {
                    Spacer(Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        TextField(
                            value = patternLabel(a.vibrationPattern), onValueChange = {}, readOnly = true,
                            label = { Text("バイブパターン") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            VibrationPattern.entries.forEach { p ->
                                DropdownMenuItem(text = { Text(patternLabel(p)) }, onClick = {
                                    vm.update { it.copy(vibrationPattern = p) }; expanded = false
                                })
                            }
                        }
                    }
                }
            }
        }

        // スヌーズ
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("スヌーズ", a.snoozeEnabled) { v -> vm.update { it.copy(snoozeEnabled = v) } }
                if (a.snoozeEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Stepper("スヌーズ間隔(分)", a.snoozeMinutes, 1, 60) { v -> vm.update { it.copy(snoozeMinutes = v) } }
                    Stepper("最大スヌーズ回数", a.maxSnoozeCount, 0, 20) { v -> vm.update { it.copy(maxSnoozeCount = v) } }
                }
            }
        }

        // 自動停止
        SectionCard(Modifier.fillMaxWidth()) {
            Stepper("自動停止(分)", a.autoStopMinutes, 1, 60) { v -> vm.update { it.copy(autoStopMinutes = v) } }
        }

        Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        if (alarmId > 0) {
            OutlinedButton(
                onClick = { vm.delete(onDone) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("削除") }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("キャンセル") }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−") }
        Text("$value", modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("＋") }
    }
}

private fun patternLabel(p: VibrationPattern) = when (p) {
    VibrationPattern.SHORT -> "短い"
    VibrationPattern.LONG -> "長い"
    VibrationPattern.DOUBLE -> "ダブル"
    VibrationPattern.HEARTBEAT -> "ハートビート"
}
