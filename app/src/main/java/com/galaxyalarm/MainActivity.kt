package com.galaxyalarm

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galaxyalarm.ring.ActiveAlarms
import com.galaxyalarm.ring.AlarmStopController
import com.galaxyalarm.ui.AppNavigation
import com.galaxyalarm.ui.SystemSettings
import com.galaxyalarm.ui.theme.Danger
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import com.galaxyalarm.update.AutoUpdateInstaller
import com.galaxyalarm.update.UpdateChecker
import com.galaxyalarm.update.UpdatePermissionRecovery
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val editAlarmRequest = mutableStateOf<Long?>(null)
    private var notificationPermissionResolved = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    private var fullScreenRecoveryInFlight = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationPermissionResolved = true
            maybeRecoverFullScreenPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editAlarmRequest.value = intent.openAlarmId()
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            GalaxyAlarmTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavigation(editAlarmRequest.value)
                        ActiveAlarmStopButton()
                        StartupAutoUpdate()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        editAlarmRequest.value = intent.openAlarmId()
    }

    override fun onResume() {
        super.onResume()
        maybeRecoverFullScreenPermission()
        val app = application as AlarmApplication
        val container = app.container
        if (container.permissions.canScheduleExactAlarms()) {
            app.appScope.launch {
                container.reliabilityChecker.runCheck()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (
            ActiveAlarms.stack.value.isNotEmpty() &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            AlarmStopController.stopAllNow(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionResolved = true
            } else {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun maybeRecoverFullScreenPermission() {
        if (!notificationPermissionResolved || fullScreenRecoveryInFlight) return
        if (!UpdatePermissionRecovery.shouldOpenFullScreenSettings(this)) {
            UpdatePermissionRecovery.markFullScreenRecoveryHandled(this)
            return
        }
        fullScreenRecoveryInFlight = true
        if (SystemSettings.openFullScreenIntentSettings(this)) {
            UpdatePermissionRecovery.markFullScreenRecoveryHandled(this)
        } else {
            fullScreenRecoveryInFlight = false
        }
    }

    private fun Intent.openAlarmId(): Long? =
        getLongExtra(EXTRA_OPEN_ALARM_ID, -1L).takeIf { it > 0L }

    companion object {
        const val EXTRA_OPEN_ALARM_ID = "com.galaxyalarm.extra.OPEN_ALARM_ID"
    }
}

@Composable
private fun BoxScope.ActiveAlarmStopButton() {
    val context = LocalContext.current
    val active by ActiveAlarms.stack.collectAsState()
    if (active.isEmpty()) return

    Button(
        onClick = { AlarmStopController.stopAllNow(context) },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Danger),
    ) {
        Text(if (active.size > 1) "鳴動中のアラームをすべて停止" else "鳴動中のアラームを停止")
    }
}

@Composable
private fun StartupAutoUpdate() {
    val context = LocalContext.current
    val error = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val info = UpdateChecker.check(BuildConfig.VERSION_NAME)?.takeIf { it.isNewer }
                ?: return@LaunchedEffect
            AutoUpdateInstaller.downloadAndOpen(context.applicationContext, info)
        }.onFailure {
            error.value = "自動更新に失敗しました: ${it.message}"
        }
    }

    error.value?.let { Text(it) }
}
