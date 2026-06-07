package com.galaxyalarm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.galaxyalarm.ui.AppNavigation
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import com.galaxyalarm.update.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            GalaxyAlarmTheme {
                Surface(Modifier.fillMaxSize()) { AppNavigation() }
                StartupUpdatePrompt()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 権限が変わっている可能性があるため、戻ってきたら再チェック&再スケジュール。
        val app = application as AlarmApplication
        val container = app.container
        if (container.permissions.canScheduleExactAlarms()) {
            app.appScope.launch {
                container.repository.rescheduleAll("activity-resume")
                container.reliabilityChecker.runCheck()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun StartupUpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<com.galaxyalarm.update.UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = UpdateChecker.check(BuildConfig.VERSION_NAME)?.takeIf { it.isNewer }
    }

    val info = update
    if (info != null && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("新しいバージョンがあります") },
            text = {
                Text("現在: ${BuildConfig.VERSION_NAME}\n最新: ${info.latestTag}\nAPKをダウンロードして更新してください。")
            },
            confirmButton = {
                TextButton(onClick = {
                    dismissed = true
                    val url = info.apkUrl ?: info.releaseUrl
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("ダウンロード") }
            },
            dismissButton = {
                TextButton(onClick = { dismissed = true }) { Text("あとで") }
            }
        )
    }
}
