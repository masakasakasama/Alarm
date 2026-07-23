package com.galaxyalarm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.galaxyalarm.prefs.GlobalAlarmPrefs
import com.galaxyalarm.BuildConfig
import com.galaxyalarm.backup.GitHubBackupClient
import com.galaxyalarm.backup.GitHubBackupSettings
import com.galaxyalarm.backup.GitHubBackupStore
import com.galaxyalarm.ui.MainViewModel
import com.galaxyalarm.ui.SystemSettings
import com.galaxyalarm.ui.components.SectionCard
import com.galaxyalarm.update.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onOpenLog: () -> Unit, onOpenReliability: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupStore = remember { GitHubBackupStore(context.applicationContext) }
    val savedBackupSettings = remember { backupStore.load() }
    val globalPrefs = remember { GlobalAlarmPrefs(context.applicationContext) }

    var updateText by remember { mutableStateOf("未確認") }
    var releaseUrl by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf(savedBackupSettings.token) }
    var gistId by remember { mutableStateOf(savedBackupSettings.gistId) }
    var backupText by remember { mutableStateOf("未実行") }
    var fadeInSeconds by remember { mutableIntStateOf(globalPrefs.fadeInSeconds) }
    var fadeInStartVolume by remember { mutableIntStateOf(globalPrefs.fadeInStartVolume) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        SectionCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("アラーム音", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("フェードイン (徐々に音量を上げる)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = fadeInSeconds > 0,
                        onCheckedChange = {
                            fadeInSeconds = if (it) 30 else 0
                            globalPrefs.fadeInSeconds = fadeInSeconds
                        }
                    )
                }
                if (fadeInSeconds > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("フェードイン時間", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            if (fadeInSeconds > 5) { fadeInSeconds -= 5; globalPrefs.fadeInSeconds = fadeInSeconds }
                        }) { Text("-") }
                        Text("${fadeInSeconds}秒", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            if (fadeInSeconds < 120) { fadeInSeconds += 5; globalPrefs.fadeInSeconds = fadeInSeconds }
                        }) { Text("+") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("開始音量", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            if (fadeInStartVolume > 5) { fadeInStartVolume -= 5; globalPrefs.fadeInStartVolume = fadeInStartVolume }
                        }) { Text("-") }
                        Text("${fadeInStartVolume}%", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            if (fadeInStartVolume < 50) { fadeInStartVolume += 5; globalPrefs.fadeInStartVolume = fadeInStartVolume }
                        }) { Text("+") }
                    }
                }
            }
        }

        // 信頼性チェックは下タブから外したため、設定の最上部から開けるようにする。
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("信頼性チェック", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "アラームが鳴らない原因(権限・予約欠落など)を点検します。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenReliability, modifier = Modifier.fillMaxWidth()) {
                    Text("信頼性チェックを開く")
                }
            }
        }

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
                    Text("バッテリー最適化の除外")
                }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("スケジュール", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.rescheduleAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("全アラームを再設定")
                }
                OutlinedButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
                    Text("イベントログ")
                }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHubバックアップ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "アラーム設定を private Gist に保存します。復元は削除せず、足りない設定だけ追加します。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gistId,
                    onValueChange = { gistId = it },
                    label = { Text("Gist ID（空なら新規作成）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(backupText, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        backupText = "バックアップ中..."
                        scope.launch {
                            runCatching {
                                backupStore.save(GitHubBackupSettings(token, gistId))
                                val json = vm.exportBackupJson()
                                val result = GitHubBackupClient.upload(token.trim(), gistId.trim(), json)
                                gistId = result.gistId
                                backupStore.saveGistId(result.gistId)
                                backupText = "バックアップ完了: ${result.gistId}"
                            }.onFailure {
                                backupText = "バックアップ失敗: ${it.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("GitHubにバックアップ") }
                OutlinedButton(
                    onClick = {
                        backupText = "復元中..."
                        scope.launch {
                            runCatching {
                                backupStore.save(GitHubBackupSettings(token, gistId))
                                val json = GitHubBackupClient.download(token.trim(), gistId.trim())
                                val (groups, alarms) = vm.mergeBackupJson(json)
                                backupText = "復元完了: グループ+$groups / アラーム+$alarms"
                            }.onFailure {
                                backupText = "復元失敗: ${it.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("GitHubから復元") }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                Text("アプリ更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "バージョン ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(updateText, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    updateText = "確認中..."
                    scope.launch {
                        val info = UpdateChecker.check(BuildConfig.VERSION_NAME)
                        if (info == null) {
                            updateText = "確認できませんでした"
                        } else {
                            releaseUrl = info.apkUrl ?: info.releaseUrl
                            updateText = if (info.isNewer) {
                                "新しいバージョン ${info.latestTag} があります"
                            } else {
                                "最新版です (${info.latestTag})"
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("更新を確認") }
                releaseUrl?.let { url ->
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }, modifier = Modifier.fillMaxWidth()) { Text("ダウンロードページを開く") }
                }
            }
        }

        Text(
            "GitHub token は端末内に保存されます。不要になったら GitHub 側で token を失効してください。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}
