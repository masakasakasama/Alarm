package com.galaxyalarm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxyalarm.BuildConfig
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.SystemSettings
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.update.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateText by remember { mutableStateOf("未確認") }
    var releaseUrl by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("権限とOS設定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { SystemSettings.openExactAlarmSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("アラームとリマインダー権限")
                }
                OutlinedButton(onClick = { SystemSettings.openNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("通知設定")
                }
                OutlinedButton(onClick = { SystemSettings.openBatteryOptimizationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("バッテリー最適化 除外")
                }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("スケジュール", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.rescheduleAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("全アラームを再スケジュール")
                }
                OutlinedButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) { Text("イベントログ") }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("アプリ更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("バージョン ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(updateText, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    updateText = "確認中..."
                    scope.launch {
                        val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
                        if (info == null) {
                            updateText = "確認できませんでした(ネットワーク/Release未公開)"
                        } else {
                            releaseUrl = info.apkUrl ?: info.releaseUrl
                            updateText = if (info.isNewer)
                                "新しいバージョン ${info.latestTag} があります"
                            else "最新です (${info.latestTag})"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("更新を確認") }
                releaseUrl?.let { url ->
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text("ダウンロードページを開く") }
                }
            }
        }

        Text(
            "個人利用向け。クラウド同期・ログイン・広告・課金なし。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}
