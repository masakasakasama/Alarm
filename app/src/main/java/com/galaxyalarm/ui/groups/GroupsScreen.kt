package com.galaxyalarm.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.data.entity.AlarmGroup
import com.galaxyalarm.ui.GroupRow
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

@Composable
fun GroupsScreen(vm: MainViewModel, onOpenGroup: (Long) -> Unit) {
    val rows by vm.groupRows.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AlarmGroup?>(null) }
    var deleteTarget by remember { mutableStateOf<AlarmGroup?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("グループ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAdd = true }) { Text("+ 追加") }
            }
        }
        items(rows, key = { it.group.id }) { row ->
            GroupCard(
                row = row,
                onOpen = { onOpenGroup(row.group.id) },
                onToggle = { vm.toggleGroup(row.group, it) },
                onRename = { renameTarget = row.group },
                onDelete = { deleteTarget = row.group }
            )
        }
        if (rows.isEmpty()) item {
            Text("グループはありません。必要なときだけ追加してください。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showAdd) {
        NameDialog("グループを追加", "", onConfirm = { vm.addGroup(it); showAdd = false }, onDismiss = { showAdd = false })
    }
    renameTarget?.let { group ->
        NameDialog("グループ名を変更", group.name, onConfirm = { vm.renameGroup(group, it); renameTarget = null }, onDismiss = { renameTarget = null })
    }
    deleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("グループを削除") },
            text = { Text("「${group.name}」と中のアラームをすべて削除します。") },
            confirmButton = { TextButton(onClick = { vm.deleteGroup(group); deleteTarget = null }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } }
        )
    }
}

@Composable
private fun GroupCard(
    row: GroupRow,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "有効 ${row.enabledCount} / 全 ${row.totalCount} 件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = row.isOn, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.isOn) {
                    Text("次回 " + TimeFormat.nextTrigger(row.nextTriggerAt), style = MaterialTheme.typography.bodyMedium)
                } else {
                    StatusPill("OFF", PillLevel.WARN)
                }
                Row {
                    TextButton(onClick = onRename) { Text("名前") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "削除") }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("名前") }) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
