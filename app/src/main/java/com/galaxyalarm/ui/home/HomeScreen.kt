package com.galaxyalarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.SystemSettings
import com.galaxyalarm.ui.TimeFormat
import com.galaxyalarm.ui.components.BigStat
import com.galaxyalarm.ui.components.PillLevel
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.ui.components.StatusPill
import com.galaxyalarm.ui.theme.Danger

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenReliability: () -> Unit,
    onOpenAlarms: () -> Unit,
    onAddAlarm: () -> Unit,
) {
    val context = LocalContext.current
    val canExact by vm.canScheduleExact.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val next by vm.nextAlarmRow.collectAsStateWithLifecycle()
    val groups by vm.groupRows.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("ホーム", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        // 権限警告(赤)
        if (!canExact) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth().background(Color.Transparent)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("要対応", PillLevel.DANGER)
                            Spacer(Modifier.height(0.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "⚠ 正確なアラーム権限がありません",
                            color = Danger, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "このままではアラームが鳴らない可能性があります。アラームは無効として扱われます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { SystemSettings.openExactAlarmSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("設定で「アラームとリマインダー」を許可") }
                    }
                }
            }
        }

        // 次に鳴るアラーム
        item {
            SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAlarms) {
                Column {
                    Text("次に鳴るアラーム", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val n = next
                    if (n?.nextTriggerAt != null) {
                        Text(
                            TimeFormat.clock(n.nextTriggerAt),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${if (n.alarm.label.isBlank()) "アラーム" else n.alarm.label} ・ ${TimeFormat.nextTrigger(n.nextTriggerAt)} ・ ${TimeFormat.remaining(n.nextTriggerAt)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("予約なし", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("アラームを追加してください",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 信頼性チェック状態
        item {
            SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenReliability) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("信頼性チェック", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        val r = report
                        when {
                            r == null -> StatusPill("確認中", PillLevel.WARN)
                            r.hasCritical -> StatusPill("要対応", PillLevel.DANGER)
                            !r.allOk -> StatusPill("注意", PillLevel.WARN)
                            else -> StatusPill("正常", PillLevel.OK)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val total = groups.sumOf { it.totalCount }
                        val enabled = groups.sumOf { it.enabledCount }
                        BigStat("$enabled", "有効アラーム")
                        BigStat("$total", "総アラーム")
                        BigStat("${groups.size}", "グループ")
                    }
                    Spacer(Modifier.height(12.dp))
                    val checkAt = report?.lastCheckAt ?: 0L
                    Text(
                        if (checkAt > 0) "前回チェック ${TimeFormat.clock(checkAt)}" else "前回チェック —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    report?.let {
                        Text(
                            if (it.allOk) "問題は見つかりませんでした" else "未解決: " +
                                it.items.filter { c -> !c.ok }.joinToString("、") { c -> c.title },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it.allOk) MaterialTheme.colorScheme.onSurfaceVariant else Danger
                        )
                    }
                }
            }
        }

        // スケジュール修復ボタン
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.repair() }, modifier = Modifier.weight(1f)) {
                    Text("スケジュール修復")
                }
                OutlinedButton(onClick = { vm.runCheck() }, modifier = Modifier.weight(1f)) {
                    Text("再チェック")
                }
            }
        }

        item {
            Button(onClick = onAddAlarm, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) { Text("＋ アラームを追加") }
        }
    }
}
