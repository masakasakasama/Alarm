package com.galaxyalarm.ui.reliability

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.reliability.CheckItem
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.SystemSettings
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill

@Composable
fun ReliabilityScreen(vm: MainViewModel, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val report by vm.report.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshPermission(); vm.runCheck() }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("信頼性チェック", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            val r = report
            Text(
                if (r == null) "確認中..." else if (r.allOk) "✅ すべて正常です"
                else "未解決の項目があります",
                style = MaterialTheme.typography.titleLarge,
                color = if (report?.allOk == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            val checkAt = report?.lastCheckAt ?: 0L
            Text(
                if (checkAt > 0) "前回チェック ${TimeFormat.clock(checkAt)}" else "前回チェック —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.repair() }, modifier = Modifier.weight(1f)) { Text("スケジュール修復") }
                OutlinedButton(onClick = { vm.runCheck() }, modifier = Modifier.weight(1f)) { Text("再チェック") }
            }
        }
        report?.let { r ->
            items(r.items) { item -> CheckRow(item, context) }
            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("最後のスケジュール修復", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (r.lastRepairAt > 0) "${TimeFormat.dateTime(r.lastRepairAt)} / ${r.lastRepairResult}"
                            else "未実施",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("最後のアラーム発火ログ", style = MaterialTheme.typography.titleLarge)
                        Text(r.lastEventLogSummary, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenLog) { Text("イベントログを見る") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, context: android.content.Context) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusPill(if (item.ok) "OK" else "要対応", if (item.ok) PillLevel.OK else PillLevel.DANGER)
            }
            Text(item.detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            if (!item.ok) {
                val action: (() -> Unit)? = when {
                    item.title.contains("exact") -> { { SystemSettings.openExactAlarmSettings(context) } }
                    item.title.contains("通知") -> { { SystemSettings.openNotificationSettings(context) } }
                    item.title.contains("バッテリー") -> { { SystemSettings.openBatteryOptimizationSettings(context) } }
                    else -> null
                }
                if (action != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = action) { Text("設定を開く") }
                }
            }
        }
    }
}
