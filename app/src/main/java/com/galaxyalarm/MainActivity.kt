package com.galaxyalarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.galaxyalarm.ui.AppNavigation
import com.galaxyalarm.ui.theme.GalaxyAlarmTheme
import com.galaxyalarm.update.AutoUpdateInstaller
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
                StartupAutoUpdate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
