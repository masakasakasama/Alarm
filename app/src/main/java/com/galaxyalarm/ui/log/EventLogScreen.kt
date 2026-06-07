package com.galaxyalarm.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.data.entity.AlarmEventLog
import com.galaxyalarm.data.model.EventResult
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

@Composable
fun EventLogScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("イベントログ", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("戻る") }
            }
        }
        if (logs.isEmpty()) item { Text("ログはまだありません。",
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(logs, key = { it.id }) { log -> LogRow(log) }
    }
}

@Composable
private fun LogRow(log: AlarmEventLog) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(TimeFormat.dateTime(log.createdAt), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(resultLabel(log.result), levelOf(log.result))
            }
            if (log.message.isNotBlank()) Text(log.message, style = MaterialTheme.typography.bodyMedium)
            log.delayMs?.let { d ->
                if (d in 1..3_600_000) Text("遅延 ${d / 1000}秒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun resultLabel(r: EventResult) = when (r) {
    EventResult.FIRED -> "発火"
    EventResult.DISMISSED -> "停止"
    EventResult.SNOOZED -> "スヌーズ"
    EventResult.MISSED -> "自動停止/未対応"
    EventResult.FAILED_TO_SCHEDULE -> "予約失敗"
}

private fun levelOf(r: EventResult) = when (r) {
    EventResult.FIRED, EventResult.DISMISSED, EventResult.SNOOZED -> PillLevel.OK
    EventResult.MISSED -> PillLevel.WARN
    EventResult.FAILED_TO_SCHEDULE -> PillLevel.DANGER
}
