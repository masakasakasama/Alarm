package com.galaxyalarm.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.galaxyalarm.data.entity.AlarmItem
import com.galaxyalarm.ui.TimeFormat

@Composable
fun ConfirmAlarmDeleteDialog(
    alarm: AlarmItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val time = TimeFormat.hourMinute12(alarm.hour, alarm.minute)
    val label = alarm.label.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アラームを削除") },
        text = {
            Text(
                if (label.isBlank()) {
                    "$time のアラームを削除しますか？"
                } else {
                    "$time「$label」を削除しますか？"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
