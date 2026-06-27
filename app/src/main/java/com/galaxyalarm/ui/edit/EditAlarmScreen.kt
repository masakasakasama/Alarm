package com.galaxyalarm.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import com.galaxyalarm.data.model.Weekdays
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.WheelTimePicker

private val AmColor = Color(0xFF80DEEA)
private val PmColor = Color(0xFFCE93D8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Long, groupId: Long = 0L, onDone: () -> Unit, vm: EditAlarmViewModel = viewModel()) {
    LaunchedEffect(alarmId, groupId) { vm.load(alarmId, groupId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val alarm = draft ?: return
    val isAm = alarm.hour < 12

    var optionsExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (alarmId > 0) "アラームを編集" else "アラームを追加",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        // ── 時間 ──
        SectionCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeFormat.hourMinuteOnly(alarm.hour, alarm.minute),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isAm) " AM" else " PM",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAm) AmColor else PmColor,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                WheelTimePicker(hour24 = alarm.hour, minute = alarm.minute) { hour, minute ->
                    vm.update { it.copy(hour = hour, minute = minute) }
                }
            }
        }

        // ── 音モード ──
        SectionCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundChip("音あり", alarm.soundMode == SoundMode.SOUND, AmColor, Modifier.weight(1f)) {
                    vm.update { it.copy(soundMode = SoundMode.SOUND, vibrationEnabled = true) }
                }
                SoundChip("音なし", alarm.soundMode != SoundMode.SOUND, PmColor, Modifier.weight(1f)) {
                    vm.update { it.copy(soundMode = SoundMode.VIBRATE_ONLY, vibrationEnabled = true) }
                }
            }
        }

        // ── オプション (折りたたみ) ──
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { optionsExpanded = !optionsExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "オプション",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (optionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = optionsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp)) {

                        OutlinedTextField(
                            value = alarm.label,
                            onValueChange = { value -> vm.update { it.copy(label = value) } },
                            label = { Text("ラベル") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider()

                        Column {
                            Text("曜日 (${Weekdays.label(alarm.weekdaysMask)})", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Weekdays.LABELS.forEachIndexed { index, label ->
                                    FilterChip(
                                        selected = Weekdays.has(alarm.weekdaysMask, index),
                                        onClick = { vm.update { it.copy(weekdaysMask = Weekdays.toggle(it.weekdaysMask, index)) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Text("選択なしは一度きり", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }

                        HorizontalDivider()

                        var groupExpanded by remember { mutableStateOf(false) }
                        val currentGroup = groups.firstOrNull { it.id == alarm.groupId }?.name ?: "選択"
                        Column {
                            Text("グループ", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                                TextField(
                                    value = currentGroup, onValueChange = {}, readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                                    groups.forEach { group ->
                                        DropdownMenuItem(text = { Text(group.name) }, onClick = {
                                            vm.update { it.copy(groupId = group.id) }; groupExpanded = false
                                        })
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        val vibrationRequired = alarm.soundMode != SoundMode.SOUND
                        Column {
                            ToggleRow("バイブ", alarm.vibrationEnabled || vibrationRequired, enabled = !vibrationRequired) { checked ->
                                vm.update { it.copy(vibrationEnabled = checked) }
                            }
                            if (alarm.vibrationEnabled || vibrationRequired) {
                                Spacer(Modifier.height(6.dp))
                                var vibExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = vibExpanded, onExpandedChange = { vibExpanded = it }) {
                                    TextField(
                                        value = patternLabel(alarm.vibrationPattern), onValueChange = {}, readOnly = true,
                                        label = { Text("バイブパターン") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vibExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = vibExpanded, onDismissRequest = { vibExpanded = false }) {
                                        VibrationPattern.entries.forEach { pattern ->
                                            DropdownMenuItem(text = { Text(patternLabel(pattern)) }, onClick = {
                                                vm.update { it.copy(vibrationPattern = pattern) }; vibExpanded = false
                                            })
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Column {
                            ToggleRow("スヌーズ", alarm.snoozeEnabled) { vm.update { it.copy(snoozeEnabled = it.snoozeEnabled.not()) } }
                            if (alarm.snoozeEnabled) {
                                Spacer(Modifier.height(6.dp))
                                Stepper("間隔 (分)", alarm.snoozeMinutes, 1, 60) { v -> vm.update { it.copy(snoozeMinutes = v) } }
                                Stepper("最大回数", alarm.maxSnoozeCount, 0, 20) { v -> vm.update { it.copy(maxSnoozeCount = v) } }
                            }
                        }

                        HorizontalDivider()

                        Stepper("自動停止 (分)", alarm.autoStopMinutes, 1, 60) { v -> vm.update { it.copy(autoStopMinutes = v) } }
                    }
                }
            }
        }

        Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        if (alarmId > 0) {
            OutlinedButton(
                onClick = { vm.delete(onDone) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("削除") }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("キャンセル") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SoundChip(label: String, selected: Boolean, accentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentColor.copy(alpha = 0.25f),
            selectedLabelColor = accentColor,
        )
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("-") }
        Text("$value", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}

private fun patternLabel(pattern: VibrationPattern) = when (pattern) {
    VibrationPattern.SHORT -> "短い"
    VibrationPattern.LONG -> "長い"
    VibrationPattern.DOUBLE -> "ダブル"
    VibrationPattern.HEARTBEAT -> "ハートビート"
}
